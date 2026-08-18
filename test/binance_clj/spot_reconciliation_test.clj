(ns binance-clj.spot-reconciliation-test
  (:require [binance-clj.client :as client]
            [binance-clj.errors :as errors]
            [binance-clj.filters-test :as filter-fixture]
            [binance-clj.spot :as sut]
            [clojure.test :refer [deftest is]]))

(defn- order-response
  [client-order-id status]
  {:clientOrderId client-order-id
   :cummulativeQuoteQty "0.0"
   :executedQty "0.0"
   :orderId 42
   :origQty "0.001"
   :price "10000.0"
   :status status
   :symbol "BTCUSDT"})

(defn- test-client
  [send-fn]
  (client/create-client
   {:clock (constantly 1720000000123)
    :credentials {:api-key "test-only-api-key"
                  :api-secret "test-only-api-secret"}
    :transport {:send! send-fn}}))

(deftest facade-submits-once-and-queries-by-original-client-id-test
  (let [requests (atom [])
        query-count (atom 0)
        connector
        (test-client
         (fn [request]
           (swap! requests conj request)
           (case (:endpoint-id request)
             :spot/new-order
             (throw (errors/connector-error :unknown-execution
                                            "Test-only unknown command."
                                            {:attempts 1}))

             :spot/query-order
             (if (= 1 (swap! query-count inc))
               (throw (errors/connector-error
                       :api
                       "Test-only eventual consistency miss."
                       {:binance-code -2013
                        :binance-reason :order-not-found
                        :http-status 400}))
               (order-response "phase7-facade-1" "NEW")))))]
    (try
      (let [result (sut/submit-order!
                    connector
                    filter-fixture/symbol-info
                    (assoc filter-fixture/valid-limit-order
                           :new-client-order-id "phase7-facade-1")
                    {:reconciliation-policy
                     {:max-query-attempts 3
                      :max-query-delay-ms 0
                      :query-delay-ms 0}
                     :sleep-fn (constantly nil)})
            submissions (filter #(= :spot/new-order (:endpoint-id %)) @requests)
            queries (filter #(= :spot/query-order (:endpoint-id %)) @requests)]
        (is (= :reconciled (:state result)))
        (is (= "NEW" (get-in result [:order :status])))
        (is (= 1 (count submissions)))
        (is (= 2 (count queries)))
        (is (every? #(= "phase7-facade-1"
                        (get-in % [:params :origClientOrderId]))
                    queries))
        (is (= :never (:retry-policy (first submissions))))
        (is (= "another-id"
               (:client-order-id
                (ex-data
                 (try
                   (sut/new-order connector
                                  filter-fixture/symbol-info
                                  (assoc filter-fixture/valid-limit-order
                                         :new-client-order-id "another-id"))
                   nil
                   (catch clojure.lang.ExceptionInfo throwable throwable)))))))
      (finally
        (client/close! connector)))))

(deftest client-order-id-cannot-be-submitted-twice-in-one-client-test
  (let [requests (atom [])
        connector (test-client
                   (fn [request]
                     (swap! requests conj request)
                     (order-response (get-in request [:params :newClientOrderId])
                                     "NEW")))
        order (assoc filter-fixture/valid-limit-order
                     :new-client-order-id "phase7-no-duplicate")]
    (try
      (is (:ok? (sut/new-order connector filter-fixture/symbol-info order)))
      (let [error (try
                    (sut/new-order connector filter-fixture/symbol-info order)
                    nil
                    (catch clojure.lang.ExceptionInfo throwable throwable))]
        (is (= :validation (errors/error-category error)))
        (is (= 1 (count @requests))))
      (finally
        (client/close! connector)))))

(deftest unresolved-facade-result-never-resubmits-test
  (let [submits (atom 0)
        queries (atom 0)
        connector
        (test-client
         (fn [request]
           (case (:endpoint-id request)
             :spot/new-order
             (do
               (swap! submits inc)
               (throw (errors/connector-error :unknown-execution
                                              "Test-only unknown command."
                                              {})))

             :spot/query-order
             (do
               (swap! queries inc)
               (throw (errors/connector-error
                       :api
                       "Test-only order absence."
                       {:binance-code -2013
                        :binance-reason :order-not-found}))))))]
    (try
      (let [result (sut/submit-order!
                    connector
                    filter-fixture/symbol-info
                    (assoc filter-fixture/valid-limit-order
                           :new-client-order-id "phase7-unresolved")
                    {:reconciliation-policy
                     {:max-query-attempts 4
                      :max-query-delay-ms 0
                      :query-delay-ms 0}
                     :sleep-fn (constantly nil)})]
        (is (= :unresolved (:state result)))
        (is (= :order-not-observed (:reason result)))
        (is (= 1 @submits))
        (is (= 4 @queries)))
      (finally
        (client/close! connector)))))
