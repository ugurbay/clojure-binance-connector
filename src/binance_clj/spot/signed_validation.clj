(ns binance-clj.spot.signed-validation
  "Validation and wire normalization for V1 signed Spot REST endpoints."
  (:require [binance-clj.config :as config]
            [binance-clj.errors :as errors]
            [binance-clj.filters :as filters]
            [clojure.string :as str]))

(def ^:private client-order-id-pattern
  #"^[A-Za-z0-9._:/-]{1,36}$")

(def ^:private order-response-types
  #{"ACK" "FULL" "RESULT"})

(def ^:private cancel-restrictions
  #{"ONLY_NEW" "ONLY_PARTIALLY_FILLED"})

(def ^:private order-keys
  #{:new-client-order-id :new-order-response-type :price :quantity
    :quote-order-qty :side :stop-price :symbol :time-in-force :type})

(defn- fail!
  [endpoint-id message data]
  (throw (errors/connector-error :validation
                                 message
                                 (assoc data :endpoint-id endpoint-id))))

(defn- reject-unknown!
  [endpoint-id allowed params]
  (when-let [unknown (seq (remove allowed (keys params)))]
    (fail! endpoint-id "Endpoint params contain unknown keys."
           {:unknown-keys unknown})))

(defn- non-blank-name
  [endpoint-id field value]
  (let [text (cond
               (string? value) value
               (keyword? value) (name value)
               :else nil)]
    (when (str/blank? text)
      (fail! endpoint-id "Parameter must be a non-blank string or keyword."
             {:field field}))
    text))

(defn- enum-value
  [endpoint-id field allowed value]
  (let [normalized (some-> (non-blank-name endpoint-id field value)
                           str/upper-case)]
    (when-not (contains? allowed normalized)
      (fail! endpoint-id "Parameter enum value is unsupported." {:field field}))
    normalized))

(defn- optional-non-negative-integer
  [endpoint-id field value]
  (when-not (and (integer? value) (not (neg? value)))
    (fail! endpoint-id "Parameter must be a non-negative integer." {:field field}))
  value)

(defn- client-order-id
  [endpoint-id field value]
  (let [text (non-blank-name endpoint-id field value)]
    (when-not (re-matches client-order-id-pattern text)
      (fail! endpoint-id
             "Client order id contains characters outside Binance's legal range or is too long."
             {:field field}))
    text))

(defn- production-command-guard!
  [endpoint-id normalized-config]
  (when (and (= :production (:environment normalized-config))
             (not (config/live-trading-enabled? normalized-config)))
    (fail! endpoint-id
           "Production trading command is disabled by the live-trading guard."
           {:field :enable-live-trading?})))

(defn- base-query-params
  [endpoint-id params]
  (cond-> {}
    (contains? params :symbol)
    (assoc :symbol (non-blank-name endpoint-id :symbol (:symbol params)))

    (contains? params :order-id)
    (assoc :orderId
           (optional-non-negative-integer endpoint-id :order-id (:order-id params)))

    (contains? params :original-client-order-id)
    (assoc :origClientOrderId
           (client-order-id endpoint-id
                            :original-client-order-id
                            (:original-client-order-id params)))))

(defn account
  "Validates account information options."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)]
    (reject-unknown! endpoint-id #{:omit-zero-balances?} params)
    (when (and (contains? params :omit-zero-balances?)
               (not (boolean? (:omit-zero-balances? params))))
      (fail! endpoint-id "omit-zero-balances? must be boolean."
             {:field :omit-zero-balances?}))
    (assoc context
           :params
           (cond-> {}
             (contains? params :omit-zero-balances?)
             (assoc :omitZeroBalances (:omit-zero-balances? params))))))

(defn my-trades
  "Validates account trade-list query combinations and dynamic weight."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)
        allowed #{:end-time :from-id :limit :order-id :start-time :symbol}]
    (reject-unknown! endpoint-id allowed params)
    (when-not (contains? params :symbol)
      (fail! endpoint-id "my-trades requires symbol." {:field :symbol}))
    (doseq [field [:order-id :from-id :start-time :end-time]]
      (when (contains? params field)
        (optional-non-negative-integer endpoint-id field (get params field))))
    (when (and (contains? params :start-time) (contains? params :end-time)
               (> (- (:end-time params) (:start-time params)) 86400000))
      (fail! endpoint-id "Trade time range cannot exceed 24 hours."
             {:field :end-time}))
    (when (and (contains? params :start-time) (contains? params :end-time)
               (> (:start-time params) (:end-time params)))
      (fail! endpoint-id "start-time cannot be after end-time."
             {:field :start-time}))
    (when (and (contains? params :order-id)
               (or (contains? params :start-time) (contains? params :end-time)))
      (fail! endpoint-id "order-id cannot be combined with start/end time."
             {:field :order-id}))
    (when (and (contains? params :from-id)
               (or (contains? params :start-time) (contains? params :end-time)))
      (fail! endpoint-id "from-id cannot be combined with start/end time."
             {:field :from-id}))
    (when (contains? params :limit)
      (when-not (and (integer? (:limit params)) (<= 1 (:limit params) 1000))
        (fail! endpoint-id "limit must be an integer from 1 to 1000."
               {:field :limit})))
    (assoc context
           :request-weight (if (contains? params :order-id) 5 20)
           :params (cond-> (base-query-params endpoint-id params)
                     (contains? params :start-time) (assoc :startTime (:start-time params))
                     (contains? params :end-time) (assoc :endTime (:end-time params))
                     (contains? params :from-id) (assoc :fromId (:from-id params))
                     (contains? params :limit) (assoc :limit (:limit params))))))

(defn query-order
  "Validates an order query by exchange id and/or original client id."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)]
    (reject-unknown! endpoint-id
                     #{:order-id :original-client-order-id :symbol}
                     params)
    (when-not (contains? params :symbol)
      (fail! endpoint-id "query-order requires symbol." {:field :symbol}))
    (when-not (or (contains? params :order-id)
                  (contains? params :original-client-order-id))
      (fail! endpoint-id "query-order requires an order identifier."
             {:field :order-id}))
    (assoc context :params (base-query-params endpoint-id params))))

(defn open-orders
  "Validates optional open-orders symbol and resolves its dynamic weight."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)]
    (reject-unknown! endpoint-id #{:symbol} params)
    (assoc context
           :request-weight (if (contains? params :symbol) 6 80)
           :params (base-query-params endpoint-id params))))

(defn cancel-order
  "Validates a cancel command and enforces the production command guard."
  [{:keys [config endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)]
    (production-command-guard! endpoint-id config)
    (reject-unknown! endpoint-id
                     #{:cancel-restrictions :new-client-order-id :order-id
                       :original-client-order-id :symbol}
                     params)
    (when-not (contains? params :symbol)
      (fail! endpoint-id "cancel-order requires symbol." {:field :symbol}))
    (when-not (or (contains? params :order-id)
                  (contains? params :original-client-order-id))
      (fail! endpoint-id "cancel-order requires an order identifier."
             {:field :order-id}))
    (assoc context
           :params
           (cond-> (base-query-params endpoint-id params)
             (contains? params :new-client-order-id)
             (assoc :newClientOrderId
                    (client-order-id endpoint-id
                                     :new-client-order-id
                                     (:new-client-order-id params)))
             (contains? params :cancel-restrictions)
             (assoc :cancelRestrictions
                    (enum-value endpoint-id
                                :cancel-restrictions
                                cancel-restrictions
                                (:cancel-restrictions params)))))))

(defn- order->wire
  [endpoint-id order]
  (cond-> {:symbol (:symbol order)
           :side (:side order)
           :type (:type order)
           :newClientOrderId (:new-client-order-id order)}
    (contains? order :time-in-force) (assoc :timeInForce (:time-in-force order))
    (contains? order :quantity) (assoc :quantity (:quantity order))
    (contains? order :quote-order-qty) (assoc :quoteOrderQty (:quote-order-qty order))
    (contains? order :price) (assoc :price (:price order))
    (contains? order :stop-price) (assoc :stopPrice (:stop-price order))
    (contains? order :new-order-response-type)
    (assoc :newOrderRespType
           (enum-value endpoint-id
                       :new-order-response-type
                       order-response-types
                       (:new-order-response-type order)))))

(defn- order-command
  [context production-guard? test-order?]
  (let [{:keys [config endpoint params services]} context
        endpoint-id (:id endpoint)
        wrapper-keys (cond-> #{:order :reference-price :symbol-info}
                       test-order? (conj :compute-commission-rates?))]
    (when production-guard?
      (production-command-guard! endpoint-id config))
    (reject-unknown! endpoint-id wrapper-keys params)
    (when-not (contains? params :order)
      (fail! endpoint-id "Order command requires an order map." {:field :order}))
    (when-not (contains? params :symbol-info)
      (fail! endpoint-id "Order command requires exchangeInfo symbol data."
             {:field :symbol-info}))
    (when (and (contains? params :compute-commission-rates?)
               (not (boolean? (:compute-commission-rates? params))))
      (fail! endpoint-id "compute-commission-rates? must be boolean."
             {:field :compute-commission-rates?}))
    (let [raw-order (:order params)]
      (when-not (map? raw-order)
        (fail! endpoint-id "order must be a map." {:field :order}))
      (reject-unknown! endpoint-id order-keys raw-order)
      (let [generator (:new-client-order-id services)
            generated-id (when-not (contains? raw-order :new-client-order-id)
                           (when-not (fn? generator)
                             (fail! endpoint-id "Client order id generator is unavailable."
                                    {:field :new-client-order-id}))
                           (generator))
            order-with-id (if generated-id
                            (assoc raw-order :new-client-order-id generated-id)
                            raw-order)
            checked-id (client-order-id endpoint-id
                                        :new-client-order-id
                                        (:new-client-order-id order-with-id))
            normalized-order (filters/validate-order
                              (:symbol-info params)
                              (assoc order-with-id :new-client-order-id checked-id)
                              {:reference-price (:reference-price params)})
            _ (when production-guard?
                (let [claim-id (:claim-client-order-id services)]
                  (when-not (fn? claim-id)
                    (fail! endpoint-id "Client order id claim service is unavailable."
                           {:field :new-client-order-id}))
                  (claim-id checked-id)))
            wire (cond-> (order->wire endpoint-id normalized-order)
                   (contains? params :compute-commission-rates?)
                   (assoc :computeCommissionRates
                          (:compute-commission-rates? params)))]
        (assoc context
               :request-weight (if (true? (:compute-commission-rates? params)) 20 1)
               :params wire)))))

(defn test-order
  "Validates a non-executing test order and its local symbol filters."
  [context]
  (order-command context false true))

(defn new-order
  "Validates an executable order, filters, and production safety guard."
  [context]
  (order-command context true false))
