(ns borba.railway-test
  (:require
   [borba.railway :as railway]
   [clojure.test :refer [deftest is testing]]))

(deftest constructors-test
  (testing "right and left wrap a value"
    (is (= 1 (:value (railway/right 1))))
    (is (= {:error :x} (:value (railway/left {:error :x})))))

  (testing "the predicates tell them apart"
    (is (railway/right? (railway/right nil)))
    (is (not (railway/right? (railway/left nil))))
    (is (railway/left? (railway/left nil)))
    (is (not (railway/left? 1)))
    (is (railway/either? (railway/right 1)))
    (is (railway/either? (railway/left 1)))
    (is (not (railway/either? {:value 1}))))

  (testing "failure builds the conventional error map"
    (is (= (railway/left {:error :not-found})
           (railway/failure :not-found)))
    (is (= (railway/left {:error :not-found :id 7})
           (railway/failure :not-found {:id 7})))))

(deftest lift-test
  (testing "an Either is returned as it is"
    (let [success (railway/right 1)
          failure (railway/left {:error :x})]
      (is (identical? success (railway/lift success)))
      (is (identical? failure (railway/lift failure)))))

  (testing "any other value becomes a success"
    (is (= (railway/right 1) (railway/lift 1)))
    (is (= (railway/right nil) (railway/lift nil)))))

(deftest bind-test
  (testing "applies the step to the value of a success"
    (is (= (railway/right 2)
           (railway/bind (railway/right 1) #(railway/right (inc %))))))

  (testing "returns a failure without calling the step"
    (let [calls   (atom 0)
          failure (railway/failure :stop)
          step    (fn [_] (swap! calls inc) (railway/right 1))]
      (is (= failure (railway/bind failure step)))
      (is (zero? @calls))))

  (testing "a step may fail"
    (is (= (railway/failure :odd {:value 1})
           (railway/bind (railway/right 1)
                         #(railway/failure :odd {:value %})))))

  (testing "a step that returns neither is a programming error"
    (let [thrown (try (railway/bind (railway/right 1) inc)
                      (catch clojure.lang.ExceptionInfo e e))]
      (is (= :borba.railway/step-did-not-return-an-either
             (:error (ex-data thrown))))
      (is (= 2 (:returned (ex-data thrown)))))))

(deftest fmap-test
  (testing "maps the value of a success"
    (is (= (railway/right 2) (railway/fmap (railway/right 1) inc))))

  (testing "returns a failure unchanged"
    (is (= (railway/failure :x) (railway/fmap (railway/failure :x) inc)))))

(deftest fmap-left-test
  (testing "maps the error of a failure"
    (is (= (railway/left {:error :translated})
           (railway/fmap-left (railway/failure :original)
                              #(assoc % :error :translated)))))

  (testing "returns a success unchanged, without calling the function"
    (let [calls (atom 0)]
      (is (= (railway/right 1)
             (railway/fmap-left (railway/right 1)
                                (fn [error] (swap! calls inc) error))))
      (is (zero? @calls)))))

(deftest chaining-test
  (testing ">>= runs the steps in order"
    (is (= (railway/right 20)
           (railway/>>= (railway/right 1)
                        #(railway/right (inc %))
                        #(railway/right (* % 10))))))

  (testing ">>= with no steps returns its input"
    (is (= (railway/right 1) (railway/>>= (railway/right 1)))))

  (testing ">>= stops at the first failure and runs nothing after it"
    (let [after (atom 0)]
      (is (= (railway/failure :second)
             (railway/>>= (railway/right 1)
                          railway/right
                          (fn [_] (railway/failure :second))
                          (fn [x] (swap! after inc) (railway/right x)))))
      (is (zero? @after)))))

(deftest threading-macro-test
  (testing "lifts a plain value and threads it through the steps"
    (is (= (railway/right 4)
           (railway/|> 1
                       #(railway/right (+ % 1))
                       #(railway/right (* % 2))))))

  (testing "keeps an initial failure"
    (is (= (railway/failure :early)
           (railway/|> (railway/failure :early)
                       railway/right))))

  (testing "keeps an initial success"
    (is (= (railway/right 2)
           (railway/|> (railway/right 1)
                       #(railway/right (inc %))))))

  (testing "evaluates the initial expression exactly once"
    (let [evaluations (atom 0)]
      (railway/|> (do (swap! evaluations inc) 1) railway/right)
      (is (= 1 @evaluations)))))

(deftest consuming-test
  (testing "unwrap returns the value of either side"
    (is (= 1 (railway/unwrap (railway/right 1))))
    (is (= {:error :x} (railway/unwrap (railway/failure :x)))))

  (testing "unwrap-or returns a default only for a failure"
    (is (= 1 (railway/unwrap-or (railway/right 1) :default)))
    (is (= :default (railway/unwrap-or (railway/failure :x) :default))))

  (testing "either calls the function of the side the result is"
    (let [on-left  (fn [value] [:left value])
          on-right (fn [value] [:right value])]
      (is (= [:right 1] (railway/either (railway/right 1) on-left on-right)))
      (is (= [:left 2] (railway/either (railway/left 2) on-left on-right))))))

(deftest try-right-test
  (testing "returns a success holding the result"
    (is (= (railway/right 3) (railway/try-right + 1 2))))

  (testing "an ex-info that carries an :error is the failure its author meant"
    (is (= (railway/left {:error :boom :status 418})
           (railway/try-right
            (fn [] (throw (ex-info "teapot" {:error :boom :status 418})))))))

  (testing "an ex-info without an :error becomes a generic failure"
    (is (= (railway/failure :exception
                            {:exception-class "clojure.lang.ExceptionInfo"})
           (railway/try-right
            (fn [] (throw (ex-info "no code" {:detail 1})))))))

  (testing "the message of an exception is withheld"
    (let [result (railway/try-right
                  (fn [] (throw (IllegalStateException. "secret: pg://host"))))]
      (is (= :exception (:error (railway/unwrap result))))
      (is (= "java.lang.IllegalStateException"
             (:exception-class (railway/unwrap result))))
      (is (not (re-find #"secret" (pr-str result)))))))

(deftest validate-test
  (testing "a value that satisfies the predicate is a success"
    (is (= (railway/right 2) (railway/validate 2 even? {:error :odd}))))

  (testing "a value that does not is a failure holding the error"
    (is (= (railway/left {:error :odd})
           (railway/validate 1 even? {:error :odd})))))
