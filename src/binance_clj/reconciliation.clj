(ns binance-clj.reconciliation
  "Bounded, no-resubmit reconciliation for unknown Spot order execution."
  (:require [binance-clj.errors :as errors]))

(def lifecycle-states
  "Stable states emitted by the synchronous V1 order lifecycle."
  #{:confirmed :pending :reconciled :rejected :unknown :unresolved})

(def ^:private transitions
  {:pending #{:confirmed :rejected :unknown}
   :unknown #{:reconciled :unresolved}
   :unresolved #{:reconciled}})

(def default-policy
  "Conservative bounded query fallback used after an unknown submission."
  {:max-query-attempts 5
   :query-delay-ms 250
   :max-query-delay-ms 2000})

(def ^:private policy-keys
  #{:max-query-attempts :max-query-delay-ms :query-delay-ms})

(def ^:private retryable-query-errors
  #{:api :timeout :transport :unknown-execution})

(defn- fail!
  [message data]
  (throw (errors/connector-error :configuration message data)))

(defn- normalize-policy
  [policy]
  (when-not (map? policy)
    (fail! "Reconciliation policy must be a map."
           {:field :reconciliation-policy}))
  (when-let [unknown (seq (remove policy-keys (keys policy)))]
    (fail! "Reconciliation policy contains unknown keys."
           {:field :reconciliation-policy :unknown-keys unknown}))
  (let [{:keys [max-query-attempts max-query-delay-ms query-delay-ms]
         :as normalized} (merge default-policy policy)]
    (when-not (and (integer? max-query-attempts)
                   (<= 1 max-query-attempts 20))
      (fail! "max-query-attempts must be an integer from 1 to 20."
             {:field :max-query-attempts}))
    (when-not (and (integer? query-delay-ms)
                   (<= 0 query-delay-ms 60000))
      (fail! "query-delay-ms must be an integer from 0 to 60000."
             {:field :query-delay-ms}))
    (when-not (and (integer? max-query-delay-ms)
                   (<= query-delay-ms max-query-delay-ms 60000))
      (fail! "max-query-delay-ms must be between query-delay-ms and 60000."
             {:field :max-query-delay-ms}))
    normalized))

(defn- safe-error
  [throwable]
  (let [data (ex-data throwable)]
    (cond-> {:category (errors/error-category throwable)}
      (:binance-code data) (assoc :binance-code (:binance-code data))
      (:binance-reason data) (assoc :binance-reason (:binance-reason data))
      (:binance-filter data) (assoc :binance-filter (:binance-filter data))
      (:http-status data) (assoc :http-status (:http-status data))
      (:attempts data) (assoc :transport-attempts (:attempts data))
      (:field data) (assoc :field (:field data))
      (:filter-type data) (assoc :filter-type (:filter-type data))
      (:rule data) (assoc :rule (:rule data))
      (:retry-after-seconds data)
      (assoc :retry-after-seconds (:retry-after-seconds data)))))

(defn- transition
  [lifecycle next-state event]
  (when-not (contains? (get transitions (:state lifecycle) #{}) next-state)
    (fail! "Order lifecycle transition is invalid."
           {:from (:state lifecycle) :to next-state}))
  (-> lifecycle
      (assoc :state next-state)
      (update :events conj (assoc event :state next-state))))

(defn- delay-ms
  [{:keys [max-query-delay-ms query-delay-ms]} completed-attempt]
  (min max-query-delay-ms
       (*' query-delay-ms (bit-shift-left 1 (dec completed-attempt)))))

(defn- order-not-found?
  [throwable]
  (= :order-not-found (:binance-reason (ex-data throwable))))

(defn- retryable-query-error?
  [throwable]
  (let [data (ex-data throwable)
        category (errors/error-category throwable)]
    (or (contains? retryable-query-errors category)
        (and (= :rate-limit category)
             (= 429 (:http-status data))
             (integer? (:retry-after-seconds data))
             (not (neg? (:retry-after-seconds data)))))))

(defn- next-delay-ms
  [policy completed-attempt error-summary]
  (max (delay-ms policy completed-attempt)
       (* 1000 (get error-summary :retry-after-seconds 0))))

(defn- reconcile!
  [lifecycle query! sleep-fn policy]
  (loop [attempt 1
         current lifecycle
         only-not-found? true]
    (let [outcome (try
                    {:result (query!)}
                    (catch Throwable throwable
                      {:error (if (errors/normalized-error? throwable)
                                throwable
                                (errors/normalize throwable :transport))}))]
      (if-let [result (:result outcome)]
        (-> (transition current
                        :reconciled
                        {:event :order-observed :query-attempt attempt})
            (assoc :order (:data result)
                   :query-attempts attempt
                   :resolution :confirmed
                   :result result))
        (let [normalized (:error outcome)
              not-found? (order-not-found? normalized)
              error-summary (safe-error normalized)
              event {:error error-summary
                     :event (if not-found?
                              :order-not-observed
                              :order-query-failed)
                     :query-attempt attempt}
              next-current (update current :events conj (assoc event :state :unknown))
              retryable? (or not-found? (retryable-query-error? normalized))]
          (if (and retryable? (< attempt (:max-query-attempts policy)))
            (do
              (sleep-fn (next-delay-ms policy attempt error-summary))
              (recur (inc attempt)
                     next-current
                     (and only-not-found? not-found?)))
            (-> (transition next-current
                            :unresolved
                            {:error error-summary
                             :event :query-budget-exhausted
                             :query-attempt attempt})
                (assoc :query-attempts attempt
                       :resolution :unknown
                       :reason (if (and only-not-found? not-found?)
                                 :order-not-observed
                                 :query-budget-exhausted)))))))))

(defn submit-and-reconcile!
  "Calls `submit!` exactly once and queries only after unknown execution.

  Returns a secret-safe lifecycle map and never retries the command."
  [{:keys [client-order-id policy query! sleep-fn submit! symbol]
    :or {policy {}
         sleep-fn #(Thread/sleep %)}}]
  (when-not (and (string? symbol) (string? client-order-id)
                 (fn? submit!) (fn? query!) (fn? sleep-fn))
    (fail! "Reconciliation requires symbol, client order id, and function boundaries."
           {:field :reconciliation}))
  (let [normalized-policy (normalize-policy policy)
        pending {:client-order-id client-order-id
                 :events [{:event :submission-started :state :pending}]
                 :state :pending
                 :submit-attempts 1
                 :symbol symbol}]
    (try
      (let [result (submit!)]
        (-> (transition pending :confirmed {:event :submission-confirmed})
            (assoc :resolution :confirmed :result result)))
      (catch Throwable throwable
        (let [normalized (if (errors/normalized-error? throwable)
                           throwable
                           (errors/normalize throwable :unknown-execution))
              summary (safe-error normalized)]
          (if (= :unknown-execution (errors/error-category normalized))
            (reconcile! (transition pending
                                    :unknown
                                    {:error summary :event :submission-unknown})
                        query!
                        sleep-fn
                        normalized-policy)
            (-> (transition pending
                            :rejected
                            {:error summary :event :submission-rejected})
                (assoc :error summary :resolution :rejected))))))))

(defn observe-execution-report
  "Reconciles an unknown/unresolved lifecycle with a matching UDS order event.

  Non-matching, malformed, or already-final lifecycle values are unchanged."
  [lifecycle event]
  (let [state (:state lifecycle)
        client-order-id (:client-order-id event)
        symbol (:symbol event)]
    (if (and (contains? #{:unknown :unresolved} state)
             (= "executionReport" (:event-type event))
             (= (:client-order-id lifecycle) client-order-id)
             (= (:symbol lifecycle) symbol))
      (-> (transition lifecycle
                      :reconciled
                      {:event :order-observed-via-user-stream
                       :execution-type (:execution-type event)
                       :order-status (:order-status event)})
          (assoc :order-event event
                 :resolution :observed))
      lifecycle)))
