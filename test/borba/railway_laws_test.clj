(ns borba.railway-laws-test
  "The laws that make `bind` and `fmap` safe to compose, checked on generated
   values and steps rather than on the handful an example would pick."
  (:require
   [borba.railway :as railway]
   [clojure.test.check.clojure-test :as tct]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]))

(def ^:private trials 200)

(def ^:private gen-value
  (gen/one-of [gen/small-integer gen/string-alphanumeric gen/keyword]))

(def ^:private gen-result
  (gen/one-of [(gen/fmap railway/right gen-value)
               (gen/fmap railway/left gen-value)]))

(def ^:private steps
  "Steps that succeed, fail, or depend on what they are given."
  [(fn [x] (railway/right (str x)))
   (fn [x] (if (number? x)
             (railway/right (inc x))
             (railway/failure :not-a-number {:value x})))
   (fn [_] (railway/failure :always))
   railway/right])

(def ^:private gen-step (gen/elements steps))

(def ^:private gen-function
  (gen/elements [identity pr-str str vector hash-set]))

(tct/defspec left-identity trials
  (prop/for-all [value gen-value
                 step  gen-step]
    (= (railway/bind (railway/right value) step)
       (step value))))

(tct/defspec right-identity trials
  (prop/for-all [result gen-result]
    (= (railway/bind result railway/right) result)))

(tct/defspec associativity trials
  (prop/for-all [result gen-result
                 f      gen-step
                 g      gen-step]
    (= (railway/bind (railway/bind result f) g)
       (railway/bind result (fn [x] (railway/bind (f x) g))))))

(tct/defspec fmap-identity trials
  (prop/for-all [result gen-result]
    (= (railway/fmap result identity) result)))

(tct/defspec fmap-composition trials
  (prop/for-all [result gen-result
                 f      gen-function
                 g      gen-function]
    (= (railway/fmap result (comp f g))
       (railway/fmap (railway/fmap result g) f))))

(tct/defspec fmap-left-leaves-a-success-alone trials
  (prop/for-all [value gen-value
                 f     gen-function]
    (= (railway/fmap-left (railway/right value) f)
       (railway/right value))))

(tct/defspec a-failure-ends-the-chain trials
  (prop/for-all [value gen-value
                 later (gen/vector gen-step 0 5)]
    (let [failure (railway/left value)]
      (= failure (apply railway/>>= failure later)))))
