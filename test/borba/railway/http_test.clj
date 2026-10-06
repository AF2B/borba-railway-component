(ns borba.railway.http-test
  (:require
   [borba.railway :as railway]
   [borba.railway.http :as http]
   [clojure.test :refer [deftest is testing]]))

(deftest success-test
  (testing "a success is a 200 with its value as the body"
    (is (= {:status 200 :body {:id 1}}
           (http/railway->response (railway/right {:id 1})))))

  (testing "the status of a success can be chosen"
    (is (= {:status 201 :body {:id 1}}
           (http/railway->response (railway/right {:id 1}) :status 201)))))

(deftest default-error-statuses-test
  (doseq [[code status] {:not-found              404
                         :validation-failed      422
                         :conflict               409
                         :unauthorized           401
                         :forbidden              403
                         :unsupported-media-type 415
                         :exception              500}]
    (testing (str code " maps to " status)
      (is (= status
             (:status (http/railway->response (railway/failure code)))))))

  (testing "the body of a failure is its error map"
    (is (= {:status 404 :body {:error :not-found :id 7}}
           (http/railway->response (railway/failure :not-found {:id 7})))))

  (testing "an error the library does not name is a 400"
    (is (= 400 (:status (http/railway->response (railway/failure :other)))))
    (is (= 400 (:status (http/railway->response (railway/left {})))))))

(deftest custom-error-statuses-test
  (testing "a map adds to the defaults and overrides them"
    (let [error->status {:rate-limited 429 :not-found 410}]
      (is (= 429 (:status (http/railway->response
                           (railway/failure :rate-limited)
                           :error->status error->status))))
      (is (= 410 (:status (http/railway->response
                           (railway/failure :not-found)
                           :error->status error->status))))
      (is (= 409 (:status (http/railway->response
                           (railway/failure :conflict)
                           :error->status error->status))))))

  (testing "a function replaces the defaults"
    (let [error->status (fn [{:keys [error]}] (if (= error :slow) 504 502))]
      (is (= 504 (:status (http/railway->response
                           (railway/failure :slow)
                           :error->status error->status))))
      (is (= 502 (:status (http/railway->response
                           (railway/failure :not-found)
                           :error->status error->status)))))))
