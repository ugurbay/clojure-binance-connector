(ns binance-clj.spot-signed-test
  (:require [binance-clj.client :as client]
            [binance-clj.errors :as errors]
            [binance-clj.filters-test :as filter-fixture]
            [binance-clj.spot :as sut]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import [java.math BigDecimal]))

(def ^:private illustrative-api-secret
  "test-only-hmac-secret")

(defn- client-with-response
  ([response captured] (client-with-response response captured {}))
  ([response captured options]
   (client/create-client
    (merge {:clock (constantly 1720000000123)
            :credentials {:api-key "test-only-api-key"
                          :api-secret illustrative-api-secret}
            :transport {:send! (fn [request]
                                 (reset! captured request)
                                 (if (fn? response) (response request) response))}}
           options))))

(defn- captured-error
  [operation]
  (try
    (operation)
    nil
    (catch clojure.lang.ExceptionInfo throwable throwable)))

(deftest default-signed-pipeline-and-account-normalization-test
  (let [captured (atom nil)
        connector (client-with-response
                   {:balances [{:asset "BTC" :free "1.25000000" :locked "0.0"}]
                    :commissionRates {:maker "0.001" :taker "0.002"
                                      :buyer "0.0" :seller "0.0"}
                    :future "kept"}
                   captured)]
    (try
      (let [result (sut/account connector {:omit-zero-balances? true})]
        (is (= :signed (:security @captured)))
        (is (= :user-data (:permission @captured)))
        (is (= 20 (:weight @captured)))
        (is (= "test-only-api-key" (:api-key @captured)))
        (is (str/starts-with? (:signed-query @captured)
                              (str "omitZeroBalances=true&recvWindow=5000"
                                   "&timestamp=1720000000123&signature=")))
        (is (= 1.25000000M (get-in result [:data :balances 0 :free])))
        (is (instance? BigDecimal
                       (get-in result [:data :commissionRates :maker])))
        (is (= "kept" (get-in result [:data :future]))))
      (finally (client/close! connector)))))

(deftest authenticated-call-without-signer-fails-before-transport-test
  (let [called? (atom false)
        connector (client/create-client
                   {:credentials {:api-key "test-only-api-key"}
                    :transport {:send! (fn [_] (reset! called? true))}})]
    (try
      (is (= :auth (errors/error-category
                    (captured-error #(sut/account connector)))))
      (is (false? @called?))
      (finally (client/close! connector)))))

(deftest signed-query-endpoint-validation-and-normalization-test
  (testing "my trades uses current documented parameter combinations and weight"
    (let [captured (atom nil)
          connector (client-with-response
                     [{:symbol "BTCUSDT" :id 1 :price "10000.0"
                       :qty "0.001" :quoteQty "10.0" :commission "0.0001"}]
                     captured)]
      (try
        (let [result (sut/my-trades connector {:symbol "BTCUSDT"
                                               :order-id 42
                                               :from-id 0
                                               :limit 100})]
          (is (= 5 (:weight @captured)))
          (is (= 42 (get-in @captured [:params :orderId])))
          (is (= 0 (get-in @captured [:params :fromId])))
          (doseq [field [:price :qty :quoteQty :commission]]
            (is (instance? BigDecimal (get-in result [:data 0 field])))))
        (finally (client/close! connector)))))
  (testing "order query and open orders normalize financial fields"
    (let [captured (atom nil)
          response {:symbol "BTCUSDT" :orderId 7 :price "10000.0"
                    :origQty "0.001" :executedQty "0.0"
                    :cummulativeQuoteQty "0.0"}
          connector (client-with-response
                     (fn [request]
                       (if (= :spot/open-orders (:endpoint-id request))
                         [response]
                         response))
                     captured)]
      (try
        (is (= 10000.0M
               (get-in (sut/query-order connector
                                        {:symbol "BTCUSDT" :order-id 7})
                       [:data :price])))
        (is (= 6 (:weight (do (sut/open-orders connector "BTCUSDT") @captured))))
        (is (= 10000.0M
               (get-in (sut/open-orders connector "BTCUSDT") [:data 0 :price])))
        (finally (client/close! connector))))))

(deftest test-order-generates-id-runs-filters-and-never-retries-test
  (let [captured (atom nil)
        connector (client-with-response
                   {}
                   captured
                   {:services {:new-client-order-id (constantly "clj-test-order-1")}})]
    (try
      (let [result (sut/test-order connector
                                   filter-fixture/symbol-info
                                   filter-fixture/valid-limit-order)]
        (is (= {} (:data result)))
        (is (= :trade (:permission @captured)))
        (is (= :never (:retry-policy @captured)))
        (is (= "clj-test-order-1"
               (get-in @captured [:params :newClientOrderId])))
        (is (= 0.001M (get-in @captured [:params :quantity])))
        (is (str/includes? (:signed-query @captured)
                           "newClientOrderId=clj-test-order-1")))
      (finally (client/close! connector)))))

(deftest generated-client-order-ids-are-unique-and-wire-safe-test
  (let [requests (atom [])
        connector (client-with-response
                   (fn [request]
                     (swap! requests conj request)
                     {:symbol "BTCUSDT"
                      :orderId (count @requests)
                      :clientOrderId (get-in request [:params :newClientOrderId])})
                   (atom nil))]
    (try
      (sut/new-order connector
                     filter-fixture/symbol-info
                     filter-fixture/valid-limit-order)
      (sut/new-order connector
                     filter-fixture/symbol-info
                     filter-fixture/valid-limit-order)
      (let [ids (mapv #(get-in % [:params :newClientOrderId]) @requests)]
        (is (= 2 (count (distinct ids))))
        (is (every? #(re-matches #"clj-[a-f0-9]{32}" %) ids))
        (is (every? #(<= (count %) 36) ids)))
      (finally (client/close! connector)))))

(deftest commission-test-order-response-and-dynamic-weight-test
  (let [captured (atom nil)
        response {:standardCommissionForOrder {:maker "0.001" :taker "0.002"}
                  :discount {:enabledForAccount true :discount "0.25"}}
        connector (client-with-response response captured)]
    (try
      (let [result (sut/test-order connector
                                   filter-fixture/symbol-info
                                   (assoc filter-fixture/valid-limit-order
                                          :new-client-order-id "caller/id:1")
                                   {:compute-commission-rates? true})]
        (is (= 20 (:weight @captured)))
        (is (true? (get-in @captured [:params :computeCommissionRates])))
        (is (= 0.001M
               (get-in result [:data :standardCommissionForOrder :maker])))
        (is (= 0.25M (get-in result [:data :discount :discount]))))
      (finally (client/close! connector)))))

(deftest production-command-guard-test
  (let [called? (atom false)
        locked (client-with-response
                {}
                called?
                {:environment :production})]
    (try
      (is (= :validation
             (errors/error-category
              (captured-error
               #(sut/new-order locked
                               filter-fixture/symbol-info
                               filter-fixture/valid-limit-order)))))
      (is (false? @called?))
      (finally (client/close! locked))))
  (let [captured (atom nil)
        enabled (client-with-response
                 {:symbol "BTCUSDT" :orderId 10 :clientOrderId "clj-live-test"
                  :price "10000.0" :origQty "0.001" :executedQty "0.0"
                  :cummulativeQuoteQty "0.0"}
                 captured
                 {:environment :production
                  :enable-live-trading? true
                  :services {:new-client-order-id (constantly "clj-live-test")}})]
    (try
      (let [result (sut/new-order enabled
                                  filter-fixture/symbol-info
                                  filter-fixture/valid-limit-order)]
        (is (= :command (:execution @captured)))
        (is (= :never (:retry-policy @captured)))
        (is (= 10000.0M (get-in result [:data :price]))))
      (finally (client/close! enabled)))))

(deftest invalid-signed-params-never-reach-transport-test
  (let [called? (atom false)
        connector (client-with-response {} called?)]
    (try
      (doseq [operation
              [#(sut/account connector {:omit-zero-balances? "yes"})
               #(sut/my-trades connector {:symbol "BTCUSDT"
                                          :from-id 1 :start-time 1})
               #(sut/query-order connector {:symbol "BTCUSDT"})
               #(sut/open-orders connector "")
               #(sut/test-order connector filter-fixture/symbol-info
                                (assoc filter-fixture/valid-limit-order
                                       :price 10000.05M))
               #(sut/test-order connector filter-fixture/symbol-info
                                (assoc filter-fixture/valid-limit-order
                                       :new-client-order-id "contains space"))
               #(sut/cancel-order connector {:symbol "BTCUSDT"})]]
        (reset! called? false)
        (is (= :validation (errors/error-category (captured-error operation))))
        (is (false? @called?)))
      (finally (client/close! connector)))))
