(ns borba.railway
  "Railway Oriented Programming (ROP) utilities.

   Provides an Either monad implementation for composable error handling.
   Functions return either a Right (success) or Left (failure) value.

   Use `>>=` (bind) or `|>` (thread) to chain operations:

     (|> input
         validate-user
         enrich-user
         persist-user)

   If any step returns a Left, the chain short-circuits and returns
   the Left value immediately. No exceptions needed for business errors.")

;; ── Types ────────────────────────────────────────────────────────────────────

(defrecord Right [value])
(defrecord Left  [value])

(defn right
  "Wraps a value in a Right (success)."
  [v]
  (->Right v))

(defn left
  "Wraps a value in a Left (failure)."
  [v]
  (->Left v))

(defn right?
  "Returns true if x is a Right."
  [x]
  (instance? Right x))

(defn left?
  "Returns true if x is a Left."
  [x]
  (instance? Left x))

;; ── Core operations ──────────────────────────────────────────────────────────

(defn bind
  "Applies f to the value inside a Right.
   If x is a Left, returns x unchanged (short-circuit).
   f must return either a Right or Left."
  [x f]
  (if (right? x)
    (f (:value x))
    x))

(defn fmap
  "Applies f to the value inside a Right, wrapping the result in Right.
   If x is a Left, returns x unchanged.
   f is a pure function (not expected to return Right/Left)."
  [x f]
  (if (right? x)
    (right (f (:value x)))
    x))

(defn >>=
  "Monadic bind operator. Chains multiple functions left-to-right.
   Each function receives the unwrapped Right value and must return Right or Left.

   (>>= (right {:name \"John\"})
        validate-name
        validate-email
        persist)"
  [m & fs]
  (reduce bind m fs))

(defmacro |>
  "Threading macro for Railway operations.
   Wraps initial value in Right (if not already) and threads through fns.

   (|> {:name \"John\"}
       validate-name
       validate-email
       persist)"
  [initial & fns]
  `(let [m# (if (or (right? ~initial) (left? ~initial))
              ~initial
              (right ~initial))]
     (>>= m# ~@fns)))

;; ── Utility ──────────────────────────────────────────────────────────────────

(defn unwrap
  "Extracts the inner value from Right or Left."
  [x]
  (:value x))

(defn either
  "Pattern-matches on Right/Left.
   Calls on-left with the Left value, or on-right with the Right value.

   (either result
     (fn [err] {:status 400 :body err})
     (fn [val] {:status 200 :body val}))"
  [x on-left on-right]
  (if (right? x)
    (on-right (:value x))
    (on-left (:value x))))

(defn try-right
  "Wraps a potentially throwing function in Right/Left.
   Returns Right on success, Left with ex-data or message on failure."
  [f & args]
  (try
    (right (apply f args))
    (catch clojure.lang.ExceptionInfo e
      (left (merge {:error (.getMessage e)} (ex-data e))))
    (catch Exception e
      (left {:error (.getMessage e)}))))

(defn validate
  "Validates a value against a predicate.
   Returns Right value if valid, Left with error-map if invalid.

   (validate {:name \"\"} #(seq (:name %)) {:error :name-required})"
  [value pred error-map]
  (if (pred value)
    (right value)
    (left error-map)))
