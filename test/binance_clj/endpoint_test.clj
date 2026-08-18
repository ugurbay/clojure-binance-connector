(ns binance-clj.endpoint-test
  (:require [binance-clj.endpoint :as sut]
            [binance-clj.errors :as errors]
            [clojure.test :refer [deftest is testing]]))

(def read-spec
  "Read endpoint fixture whose default retry policy is safe."
  {:id :spot/ping
   :method :get
   :path "/api/v3/ping"
   :security :none
   :execution :read})

(def command-spec
  "State-changing DELETE fixture proving method alone cannot imply retry safety."
  {:id :spot/cancel-order
   :method :delete
   :path "/api/v3/order"
   :security :signed
   :execution :command})

(deftest endpoint-defaults-follow-execution-semantics-test
  (testing "read endpoints are safe and commands never retry regardless of HTTP method"
    (is (= :safe (:retry-policy (sut/validate-spec read-spec))))
    (is (= :never (:retry-policy (sut/validate-spec command-spec)))))
  (testing "a command cannot opt into automatic retry"
    (let [error (try
                  (sut/validate-spec (assoc command-spec :retry-policy :safe))
                  nil
                  (catch clojure.lang.ExceptionInfo throwable throwable))]
      (is (= :configuration (errors/error-category error))))))

(deftest immutable-registry-test
  (let [empty-registry (sut/create-registry)
        registry (sut/register empty-registry read-spec)]
    (is (empty? empty-registry))
    (is (= :spot/ping (:id (sut/lookup registry :spot/ping))))
    (is (= :validation
           (errors/error-category
            (try
              (sut/lookup registry :spot/missing)
              nil
              (catch clojure.lang.ExceptionInfo throwable throwable)))))))

(deftest duplicate-and-malformed-spec-test
  (doseq [operation [#(sut/create-registry [read-spec read-spec])
                     #(sut/validate-spec (dissoc read-spec :path))
                     #(sut/validate-spec (assoc read-spec :id :ping))
                     #(sut/validate-spec (assoc read-spec :weight -1))
                     #(sut/validate-spec (assoc read-spec :path "/sapi/v1/asset"))
                     #(sut/validate-spec (assoc read-spec :permission :user-data))
                     #(sut/validate-spec (assoc command-spec :permission :admin))]]
    (is (= :configuration
           (errors/error-category
            (try
              (operation)
              nil
              (catch clojure.lang.ExceptionInfo throwable throwable)))))))
