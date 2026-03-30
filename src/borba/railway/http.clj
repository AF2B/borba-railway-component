(ns borba.railway.http
  "HTTP response helpers for Railway-oriented handlers.

   Converts Railway Either values (Right/Left) into Pedestal-compatible
   HTTP response maps.

   ── Default error → HTTP status mapping ──────────────────────────────────────

     :not-found           → 404
     :validation-failed   → 422
     :conflict            → 409
     :unauthorized        → 401
     :forbidden           → 403
     :unsupported-media-type → 415
     (any other error)    → 400

   ── Usage ────────────────────────────────────────────────────────────────────

     (require '[borba.railway.http :as rh])

     ;; Simple — success defaults to 200
     (defn get-handler [{:keys [components path-params]}]
       (rh/railway->response (biz/get-by-id components (:id path-params))))

     ;; Custom success status
     (defn create-handler [{:keys [components body-params]}]
       (rh/railway->response (biz/create! components body-params) :status 201))

     ;; Custom error mapping (overrides default)
     (defn handler [{:keys [components body-params]}]
       (rh/railway->response
         (biz/do-something components body-params)
         :status 202
         :error->status (fn [{:keys [error]}]
                          (case error
                            :rate-limited 429
                            400))))"
  (:require [borba.railway :as rop]))

(defn- default-error->status [{:keys [error]}]
  (case error
    :not-found              404
    :validation-failed      422
    :conflict               409
    :unauthorized           401
    :forbidden              403
    :unsupported-media-type 415
    400))

(defn railway->response
  "Converts a Railway Either value into an HTTP response map.

   On Right (success): {:status <status> :body <value>}
   On Left  (error):   {:status <http-status> :body <error-map>}

   Options:
     :status        — HTTP status for success (default 200)
     :error->status — fn from error map to HTTP status (default: common mapping)"
  [result & {:keys [status error->status]
             :or   {status       200
                    error->status default-error->status}}]
  (rop/either result
              (fn [err] {:status (error->status err) :body err})
              (fn [val] {:status status :body val})))
