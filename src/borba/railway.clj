(ns borba.railway
  "Railway-oriented programming: failure as data.

   A step that can fail returns an Either, a `Right` holding its result or a
   `Left` holding what went wrong. `>>=` and `|>` chain steps so that the first
   `Left` ends the chain and no later step runs, which keeps the happy path
   free of nested conditionals and keeps exceptions for broken invariants.

   By convention the value of a `Left` is a map with an `:error` keyword and
   whatever context explains it, as `failure` builds:

     (|> {:name \"Ana\"}
         validate-user
         persist-user)
     ;; => #borba.railway.Right{:value {...}}
     ;; => #borba.railway.Left{:value {:error :validation-failed ...}}")

(set! *warn-on-reflection* true)

;; ── Types ────────────────────────────────────────────────────────────────

(defrecord Right [value])
(defrecord Left [value])

(defn right
  "Wraps a value as a success.
   - value: the result of a step"
  [value]
  (->Right value))

(defn left
  "Wraps a value as a failure.
   - value: what went wrong, by convention a map with an :error keyword"
  [value]
  (->Left value))

(defn failure
  "Returns a failure whose value is a map with the :error code and its context.
   - code: a keyword that names the failure, such as :not-found
   - context: a map of what explains it (default empty)"
  ([code]
   (failure code {}))
  ([code
    context]
   (left (assoc context :error code))))

(defn right?
  "Returns true when x is a success.
   - x: any value"
  [x]
  (instance? Right x))

(defn left?
  "Returns true when x is a failure.
   - x: any value"
  [x]
  (instance? Left x))

(defn either?
  "Returns true when x is a success or a failure.
   - x: any value"
  [x]
  (or (right? x) (left? x)))

(defn lift
  "Returns x unchanged when it is already a success or a failure, and a success
   holding x otherwise.
   - x: any value"
  [x]
  (if (either? x)
    x
    (right x)))

;; ── Composing ────────────────────────────────────────────────────────────

(defn bind
  "Applies a step to the value of a success, and returns a failure unchanged.
   The step must return a success or a failure; anything else is a programming
   error and throws.
   - result: a success or a failure
   - step: a function from the value of a success to a success or a failure"
  [result
   step]
  (if (right? result)
    (let [next-result (step (:value result))]
      (if (either? next-result)
        next-result
        (throw (ex-info "a railway step must return a Right or a Left"
                        {:error    ::step-did-not-return-an-either
                         :returned next-result}))))
    result))

(defn fmap
  "Applies a function to the value of a success and wraps the result as a
   success, and returns a failure unchanged.
   - result: a success or a failure
   - f: a function from a value to a value"
  [result
   f]
  (if (right? result)
    (right (f (:value result)))
    result))

(defn fmap-left
  "Applies a function to the value of a failure and wraps the result as a
   failure, and returns a success unchanged. It translates an error without
   running anything on the happy path.
   - result: a success or a failure
   - f: a function from the error to a new error"
  [result
   f]
  (if (left? result)
    (left (f (:value result)))
    result))

(defn >>=
  "Chains steps left to right, stopping at the first failure.
   - result: a success or a failure to start from
   - steps: functions from the value of a success to a success or a failure"
  [result & steps]
  (reduce bind result steps))

(defmacro |>
  "Threads a value through steps, as `>>=` does. The initial value is lifted to
   a success unless it already is a success or a failure, and is evaluated
   once.
   - initial: the starting value, or a success or a failure
   - steps: expressions that evaluate to functions of the value, such as a
     function name or a call that returns one: (check-unique components)"
  [initial & steps]
  `(>>= (lift ~initial) ~@steps))

;; ── Consuming ────────────────────────────────────────────────────────────

(defn unwrap
  "Returns the value of a success or of a failure.
   - result: a success or a failure"
  [result]
  (:value result))

(defn unwrap-or
  "Returns the value of a success, or a default for a failure.
   - result: a success or a failure
   - default: what to return for a failure"
  [result
   default]
  (if (right? result)
    (:value result)
    default))

(defn either
  "Calls one of two functions, according to what the result is.
   - result: a success or a failure
   - on-left: called with the value of a failure
   - on-right: called with the value of a success"
  [result
   on-left
   on-right]
  (if (right? result)
    (on-right (:value result))
    (on-left (:value result))))

;; ── Bridging ─────────────────────────────────────────────────────────────

(defn- exception-failure
  "Returns the failure an exception stands for. An ex-info that carries an
   :error is the failure its author meant, and is returned as it is; any other
   exception becomes :exception with its class, and its message is withheld
   because it can describe internals.
   - exception: the exception that was thrown"
  [exception]
  (let [data (ex-data exception)]
    (if (contains? data :error)
      (left data)
      (failure :exception {:exception-class (.getName (class exception))}))))

(defn try-right
  "Calls a function and returns a success holding its result, or a failure
   when it throws.
   - f: the function to call
   - args: the arguments to call it with"
  [f & args]
  (try
    (right (apply f args))
    (catch Exception exception
      (exception-failure exception))))

(defn validate
  "Returns a success holding the value when it satisfies the predicate, and a
   failure holding the error otherwise.
   - value: the value to check
   - pred: a predicate of the value
   - error: the value of the failure, by convention a map with an :error"
  [value
   pred
   error]
  (if (pred value)
    (right value)
    (left error)))
