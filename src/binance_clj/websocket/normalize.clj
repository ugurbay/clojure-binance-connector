(ns binance-clj.websocket.normalize
  "Lossless Binance Spot market and User Data event normalization."
  (:require [binance-clj.decimal :as decimal]))

(def ^:private market-decimal-fields
  #{:a :A :b :B :c :C :h :l :o :p :P :q :Q :v :w :x})

(def ^:private execution-decimal-fields
  #{:A :B :F :L :n :p :P :q :Q :Y :Z :z})

(defn- decimal-value
  [value]
  (if (string? value) (decimal/parse value) value))

(defn- parse-fields
  [value fields]
  (reduce (fn [result field]
            (if (contains? result field)
              (update result field decimal-value)
              result))
          value
          fields))

(defn- parse-levels
  [levels]
  (mapv (fn [level]
          (if (and (vector? level) (= 2 (count level)))
            [(decimal-value (first level)) (decimal-value (second level))]
            level))
        levels))

(defn normalize-market-event
  "Normalizes supported market payloads and preserves unknown fields."
  [payload]
  (if (vector? payload)
    {:events (mapv normalize-market-event payload)
     :event-type "miniTickerArray"
     :kind :market-event-batch}
    (let [event (if (map? payload) payload {:raw payload})]
      (cond-> (parse-fields event market-decimal-fields)
        (vector? (:bids event)) (update :bids parse-levels)
        (vector? (:asks event)) (update :asks parse-levels)
        true (assoc :kind :market-event
                    :event-type (or (:e event)
                                    (when (:lastUpdateId event) "depth")))))))

(defn- normalize-balances
  [balances]
  (mapv #(parse-fields % #{:f :l}) balances))

(defn normalize-user-event
  "Normalizes supported UDS events while retaining Binance's original keys."
  [payload]
  (let [subscription-id (:subscriptionId payload)
        event (or (:event payload) payload)
        event-type (:e event)
        normalized
        (case event-type
          "executionReport"
          (-> (parse-fields event execution-decimal-fields)
              (assoc :client-order-id (:c event)
                     :event-type event-type
                     :execution-type (:x event)
                     :kind :user-event
                     :order-id (:i event)
                     :order-status (:X event)
                     :original-client-order-id (:C event)
                     :symbol (:s event)))

          "outboundAccountPosition"
          (cond-> (assoc event :event-type event-type :kind :user-event)
            (vector? (:B event)) (update :B normalize-balances))

          "balanceUpdate"
          (-> event
              (parse-fields #{:d})
              (assoc :event-type event-type :kind :user-event))

          (assoc event :event-type event-type :kind :user-event))]
    (cond-> normalized
      (some? subscription-id) (assoc :subscription-id subscription-id))))

(defn normalize-stream-message
  "Normalizes a raw or combined market-stream message."
  [message]
  (if (and (map? message) (contains? message :stream) (contains? message :data))
    (assoc (normalize-market-event (:data message)) :stream (:stream message))
    (normalize-market-event message)))

(defn normalize-websocket-api-message
  "Separates WebSocket API responses from User Data Stream events."
  [message]
  (cond
    (and (map? message) (contains? message :status))
    {:id (:id message)
     :kind :response
     :rate-limits (:rateLimits message)
     :result (:result message)
     :status (:status message)
     :error (:error message)}

    (map? message) (normalize-user-event message)

    :else {:kind :unknown :raw message}))
