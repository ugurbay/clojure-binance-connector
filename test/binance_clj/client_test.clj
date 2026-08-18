(ns binance-clj.client-test
  (:require [binance-clj.client :as sut]
            [binance-clj.config :as config]
            [binance-clj.errors :as errors]
            [clojure.test :refer [deftest is]]))

(def ping-spec
  "Minimal public read endpoint used by client lifecycle tests."
  {:id :spot/ping
   :method :get
   :path "/api/v3/ping"
   :security :none
   :execution :read})

(deftest client-lifecycle-test
  (let [close-count (atom 0)
        client (sut/create-client
                {:registry [ping-spec]
                 :transport {:send! (constantly {:pong true})
                             :close! #(swap! close-count inc)}})]
    (is (sut/client? client))
    (is (false? (sut/closed? client)))
    (is (= {:pong true} (:data (sut/execute! client :spot/ping))))
    (is (true? (sut/close! client)))
    (is (false? (sut/close! client)))
    (is (= 1 @close-count))
    (is (true? (sut/closed? client)))
    (is (= :client-closed
           (errors/error-category
            (try
              (sut/execute! client :spot/ping)
              nil
              (catch clojure.lang.ExceptionInfo throwable throwable)))))))

(deftest client-public-config-is-secret-safe-test
  (let [client (sut/create-client
                {:credentials {:api-key "visible-key" :api-secret "hidden-secret"}})
        public-config (sut/public-config client)]
    (is (= :testnet (:environment public-config)))
    (is (= :binance-clj/redacted (:credentials public-config)))
    (is (not (re-find #"visible-key|hidden-secret" (pr-str public-config))))
    (is (true? (sut/close! client)))))

(deftest default-http-transport-is-created-without-a-network-call-test
  (let [client (sut/create-client {:registry [ping-spec]})
        public-config (sut/public-config client)]
    (is (= :testnet (:environment public-config)))
    (is (true? (sut/close! client)))
    (is (true? (sut/closed? client)))))

(deftest injection-contracts-are-validated-eagerly-test
  (doseq [options [{:services {:sign :not-a-function}}
                   {:services {:unknown identity}}
                   {:stage-overrides {:unknown identity}}
                   {:registry {:spot/wrong (assoc ping-spec :id :spot/ping)}}]]
    (is (= :configuration
           (errors/error-category
            (try
              (sut/create-client options)
              nil
              (catch clojure.lang.ExceptionInfo throwable throwable)))))))

(deftest default-clock-can-be-synchronized-before-signed-requests-test
  (let [wall-clock (atom 1000)
        requests (atom [])]
    (with-redefs [config/system-clock #(deref wall-clock)]
      (let [client (sut/create-client
                    {:credentials {:api-key "test-only-api-key"
                                   :api-secret "test-only-api-secret"}
                     :transport
                     {:send! (fn [request]
                               (swap! requests conj request)
                               (case (:endpoint-id request)
                                 :spot/server-time
                                 (do
                                   (reset! wall-clock 1200)
                                   {:serverTime 1550})

                                 :spot/account
                                 {:balances []}))}})]
        (try
          (is (= {:offset-ms 450
                  :round-trip-ms 200
                  :server-time-ms 1550}
                 (sut/synchronize-time! client)))
          (sut/execute! client :spot/account)
          (is (= 1650 (:timestamp (last @requests))))
          (is (= 1650 (get-in (last @requests) [:params :timestamp])))
          (finally
            (sut/close! client)))))))

(deftest custom-clock-owns-its-synchronization-test
  (let [client (sut/create-client {:clock (constantly 1000)})]
    (try
      (is (= :configuration
             (errors/error-category
              (try
                (sut/synchronize-time! client)
                nil
                (catch clojure.lang.ExceptionInfo throwable throwable)))))
      (finally
        (sut/close! client)))))
