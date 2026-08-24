(ns binance-clj.spot.trade
  "Public facade for locally validated V1 Spot trading commands."
  (:require [binance-clj.client :as client]
            [binance-clj.errors :as errors]
            [binance-clj.reconciliation :as reconciliation]))

(defn- order-with-client-id
  [connector order]
  (if (and (map? order) (not (contains? order :new-client-order-id)))
    (assoc order :new-client-order-id (client/next-client-order-id connector))
    order))

(defn test-order
  "Validates a MARKET/LIMIT/STOP_LOSS order locally and through Binance without execution."
  ([connector symbol-info order]
   (test-order connector symbol-info order {}))
  ([connector symbol-info order options]
   (client/execute! connector
                    :spot/test-order
                    (merge options {:order order :symbol-info symbol-info}))))

(defn new-order
  "Submits a locally validated MARKET/LIMIT/STOP_LOSS order.

  Production use also requires `:enable-live-trading? true` on the client."
  ([connector symbol-info order]
   (new-order connector symbol-info order {}))
  ([connector symbol-info order options]
   (let [prepared-order (order-with-client-id connector order)]
     (try
       (client/execute! connector
                        :spot/new-order
                        (merge options
                               {:order prepared-order :symbol-info symbol-info}))
       (catch clojure.lang.ExceptionInfo throwable
         (if (= :unknown-execution (errors/error-category throwable))
           (throw (errors/connector-error
                   :unknown-execution
                   "New order execution status is unknown."
                   (assoc (ex-data throwable)
                          :client-order-id (:new-client-order-id prepared-order)
                          :symbol (:symbol prepared-order))
                   throwable))
           (throw throwable)))))))

(defn submit-order!
  "Submits once and reconciles unknown execution by client order id.

  Options are `:reference-price`, `:reconciliation-policy`, and the testable
  `:sleep-fn` boundary. The final lifecycle is never reported as rejected only
  because bounded queries did not observe an order."
  ([connector symbol-info order]
   (submit-order! connector symbol-info order {}))
  ([connector symbol-info order options]
   (when-not (map? options)
     (throw (errors/connector-error :validation
                                    "submit-order! options must be a map."
                                    {:field :options})))
   (when-let [unknown (seq (remove #{:reconciliation-policy
                                     :reference-price
                                     :sleep-fn}
                                   (keys options)))]
     (throw (errors/connector-error :validation
                                    "submit-order! options contain unknown keys."
                                    {:unknown-keys unknown})))
   (let [prepared-order (order-with-client-id connector order)
         client-order-id (:new-client-order-id prepared-order)
         symbol (:symbol prepared-order)
         order-options (cond-> {}
                         (contains? options :reference-price)
                         (assoc :reference-price (:reference-price options)))]
     (reconciliation/submit-and-reconcile!
      {:client-order-id client-order-id
       :policy (get options :reconciliation-policy {})
       :query! #(client/execute! connector
                                 :spot/query-order
                                 {:symbol symbol
                                  :original-client-order-id client-order-id})
       :sleep-fn (get options :sleep-fn #(Thread/sleep %))
       :submit! #(new-order connector symbol-info prepared-order order-options)
       :symbol symbol}))))

(defn cancel-order
  "Cancels one order after validating its identifiers and production guard."
  [connector params]
  (client/execute! connector :spot/cancel-order params))
