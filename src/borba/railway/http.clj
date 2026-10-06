(ns borba.railway.http
  "Turns an Either into an HTTP response map, so that a handler built from
   railway steps ends with one call:

     (defn create-user [{:keys [components body-params]}]
       (rh/railway->response (users/create! components body-params)
                             :status 201))

   A success becomes {:status <status> :body <value>}; a failure becomes
   {:status <status of its :error> :body <the error map>}."
  (:require
   [borba.railway :as railway]))

(def default-error-statuses
  "The HTTP status of the :error keywords the library names. A keyword that is
   not here maps to 400, and a caller adds or overrides entries through the
   :error->status option of `railway->response`."
  {:not-found              404
   :validation-failed      422
   :conflict               409
   :unauthorized           401
   :forbidden              403
   :unsupported-media-type 415
   :exception              500})

(def ^:private default-success-status 200)
(def ^:private fallback-error-status 400)

(defn- status-resolver
  "Returns the function that maps a failure to its HTTP status.
   - error->status: nil for the defaults, a map of :error keyword to status
     that is merged over the defaults, or a function from the error map to a
     status that replaces them"
  [error->status]
  (cond
    (fn? error->status)
    error->status

    :else
    (let [statuses (merge default-error-statuses error->status)]
      (fn [error]
        (get statuses (:error error) fallback-error-status)))))

(defn railway->response
  "Converts a success or a failure into an HTTP response map.
   - result: a success or a failure from borba.railway
   - status: the HTTP status of a success (default 200)
   - error->status: how a failure maps to a status; nil for the defaults, a
     map of :error keyword to status merged over them, or a function from the
     error map to a status"
  [result & {:keys [status error->status]
             :or   {status default-success-status}}]
  (let [resolve-status (status-resolver error->status)]
    (railway/either result
                    (fn [error] {:status (resolve-status error) :body error})
                    (fn [value] {:status status :body value}))))
