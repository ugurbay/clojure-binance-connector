(ns binance-clj.reconciliation-test
  (:require [binance-clj.errors :as errors]
            [binance-clj.reconciliation :as sut]
            [clojure.test :refer [deftest is testing]]))

(defn- api-error
  [reason]
  (errors/connector-error :api
                          "Test-only API error."
                          {:binance-code (if (= reason :order-not-found) -2013 -2010)
                           :binance-reason reason
                           :http-status 400}))

(defn- unknown-error
  []
  (errors/connector-error :unknown-execution
                          "Test-only unknown command."
                          {:attempts 1}))

(defn- execute
  [{:keys [policy query! sleep-fn submit!]}]
  (sut/submit-and-reconcile!
   {:client-order-id "phase7-order-1"
    :policy (or policy {})
    :query! query!
    :sleep-fn (or sleep-fn (constantly nil))
    :submit! submit!
    :symbol "BTCUSDT"}))

(deftest immediate-confirmation-and-rejection-test
  (testing "a confirmed command is submitted exactly once and is not queried"
    (let [submits (atom 0)
          queries (atom 0)
          result (execute {:submit! #(do (swap! submits inc) {:ok? true})
                           :query! #(swap! queries inc)})]
      (is (= :confirmed (:state result)))
      (is (= :confirmed (:resolution result)))
      (is (= 1 @submits))
      (is (zero? @queries))
      (is (= [:pending :confirmed] (mapv :state (:events result))))))
  (testing "a deterministic API rejection is not queried"
    (let [queries (atom 0)
          result (execute {:submit! #(throw (api-error :new-order-rejected))
                           :query! #(swap! queries inc)})]
      (is (= :rejected (:state result)))
      (is (= :rejected (:resolution result)))
      (is (= :new-order-rejected (get-in result [:error :binance-reason])))
      (is (zero? @queries)))))

(deftest rejection-retains-only-safe-diagnostic-fields-test
  (let [result (execute
                {:submit! #(throw
                            (errors/connector-error
                             :validation
                             "Test-only validation failure."
                             {:field :notional
                              :filter-type "NOTIONAL"
                              :rule :minNotional
                              :unsafe-detail "must not escape"}))
                 :query! identity})]
    (is (= {:category :validation
            :field :notional
            :filter-type "NOTIONAL"
            :rule :minNotional}
           (:error result)))
    (is (not (contains? (:error result) :unsafe-detail)))))

(deftest unknown-command-is-reconciled-without-a-second-submit-test
  (let [submits (atom 0)
        queries (atom 0)
        delays (atom [])
        result (execute
                {:policy {:max-query-attempts 5
                          :query-delay-ms 10
                          :max-query-delay-ms 20}
                 :sleep-fn #(swap! delays conj %)
                 :submit! #(do (swap! submits inc) (throw (unknown-error)))
                 :query! #(case (swap! queries inc)
                            1 (throw (api-error :order-not-found))
                            2 (throw (errors/connector-error
                                      :transport
                                      "Test-only query failure."
                                      {}))
                            {:ok? true :data {:status "NEW"}})})]
    (is (= :reconciled (:state result)))
    (is (= :confirmed (:resolution result)))
    (is (= "NEW" (get-in result [:order :status])))
    (is (= 1 @submits))
    (is (= 3 @queries))
    (is (= [10 20] @delays))
    (is (= [:pending :unknown :unknown :unknown :reconciled]
           (mapv :state (:events result))))))

(deftest order-absence-never-becomes-a-false-rejection-test
  (let [submits (atom 0)
        queries (atom 0)
        result (execute
                {:policy {:max-query-attempts 3
                          :query-delay-ms 0
                          :max-query-delay-ms 0}
                 :submit! #(do (swap! submits inc) (throw (unknown-error)))
                 :query! #(do (swap! queries inc)
                              (throw (api-error :order-not-found)))})]
    (is (= :unresolved (:state result)))
    (is (= :unknown (:resolution result)))
    (is (= :order-not-observed (:reason result)))
    (is (= 1 @submits))
    (is (= 3 @queries))))

(deftest non-retryable-query-error-stops-the-budget-test
  (let [queries (atom 0)
        result (execute
                {:submit! #(throw (unknown-error))
                 :query! #(do
                            (swap! queries inc)
                            (throw (errors/connector-error
                                    :auth
                                    "Test-only auth failure."
                                    {})))})]
    (is (= :unresolved (:state result)))
    (is (= :query-budget-exhausted (:reason result)))
    (is (= 1 @queries))))

(deftest query-rate-limit-obeys-retry-after-and-ip-ban-stops-test
  (testing "429 polling waits at least Retry-After before the next bounded query"
    (let [queries (atom 0)
          delays (atom [])
          result (execute
                  {:policy {:max-query-attempts 3
                            :query-delay-ms 10
                            :max-query-delay-ms 20}
                   :sleep-fn #(swap! delays conj %)
                   :submit! #(throw (unknown-error))
                   :query! #(if (= 1 (swap! queries inc))
                              (throw (errors/connector-error
                                      :rate-limit
                                      "Test-only rate limit."
                                      {:http-status 429
                                       :retry-after-seconds 2}))
                              {:ok? true :data {:status "NEW"}})})]
      (is (= :reconciled (:state result)))
      (is (= [2000] @delays))))
  (testing "418 is not polled again"
    (let [queries (atom 0)
          result (execute
                  {:submit! #(throw (unknown-error))
                   :query! #(do
                              (swap! queries inc)
                              (throw (errors/connector-error
                                      :rate-limit
                                      "Test-only IP ban."
                                      {:http-status 418
                                       :retry-after-seconds 60})))})]
      (is (= :unresolved (:state result)))
      (is (= 1 @queries)))))

(deftest unexpected-submit-boundary-failure-is-treated-as-unknown-test
  (let [queries (atom 0)
        result (execute {:submit! #(throw (IllegalStateException. "unsafe detail"))
                         :query! #(do
                                    (swap! queries inc)
                                    {:ok? true :data {:status "FILLED"}})})]
    (is (= :reconciled (:state result)))
    (is (= 1 @queries))
    (is (not (re-find #"unsafe detail" (pr-str result))))))

(deftest invalid-policy-fails-before-submission-test
  (let [submits (atom 0)
        error (try
                (execute {:policy {:max-query-attempts 0}
                          :submit! #(swap! submits inc)
                          :query! identity})
                nil
                (catch clojure.lang.ExceptionInfo throwable throwable))]
    (is (= :configuration (errors/error-category error)))
    (is (zero? @submits))))

(deftest matching-user-stream-execution-reconciles-an-unresolved-order-test
  (let [lifecycle {:client-order-id "clj-order-1"
                   :events []
                   :state :unresolved
                   :symbol "BTCUSDT"}
        event {:client-order-id "clj-order-1"
               :event-type "executionReport"
               :execution-type "NEW"
               :order-status "NEW"
               :symbol "BTCUSDT"}
        result (sut/observe-execution-report lifecycle event)]
    (is (= :reconciled (:state result)))
    (is (= :observed (:resolution result)))
    (is (= event (:order-event result)))
    (is (= :order-observed-via-user-stream
           (-> result :events last :event)))
    (is (= lifecycle
           (sut/observe-execution-report
            lifecycle
            (assoc event :client-order-id "different"))))))
