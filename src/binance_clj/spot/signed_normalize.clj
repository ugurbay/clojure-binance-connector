(ns binance-clj.spot.signed-normalize
  "Lossless normalization for V1 authenticated Spot REST responses."
  (:require [binance-clj.decimal :as decimal]
            [binance-clj.errors :as errors]))

(def ^:private order-decimal-fields
  #{:cummulativeQuoteQty :executedQty :icebergQty :origQty :origQuoteOrderQty
    :peggedPrice :preventedExecutionPrice :preventedExecutionQty
    :preventedExecutionQuoteQty :preventedQuantity :price :stopPrice})

(def ^:private trade-decimal-fields
  #{:commission :price :qty :quoteQty})

(def ^:private commission-fields
  #{:buyer :discount :maker :seller :taker})

(defn- fail!
  [endpoint-id message]
  (throw (errors/connector-error :api message {:endpoint-id endpoint-id})))

(defn- ensure-map
  [endpoint-id value]
  (when-not (map? value)
    (fail! endpoint-id "Binance response must be an object."))
  value)

(defn- ensure-array
  [endpoint-id value]
  (when-not (sequential? value)
    (fail! endpoint-id "Binance response must be an array."))
  value)

(defn- parse-decimal
  [endpoint-id value]
  (try
    (decimal/parse value)
    (catch clojure.lang.ExceptionInfo _
      (fail! endpoint-id "Binance financial field is not a plain decimal."))))

(defn- normalize-decimal-fields
  [endpoint-id value fields]
  (reduce (fn [result field]
            (if (and (contains? result field) (some? (get result field)))
              (assoc result field (parse-decimal endpoint-id (get result field)))
              result))
          value
          fields))

(defn- normalize-commission-map
  [endpoint-id value]
  (normalize-decimal-fields endpoint-id
                            (ensure-map endpoint-id value)
                            commission-fields))

(defn account
  "Normalizes balances and commission rates while preserving unknown fields."
  [value]
  (let [endpoint-id :spot/account
        result (ensure-map endpoint-id value)]
    (when-not (sequential? (:balances result))
      (fail! endpoint-id "Account balances must be an array."))
    (cond-> (update result
                    :balances
                    (fn [balances]
                      (mapv #(normalize-decimal-fields
                              endpoint-id
                              (ensure-map endpoint-id %)
                              #{:free :locked})
                            balances)))
      (contains? result :commissionRates)
      (update :commissionRates #(normalize-commission-map endpoint-id %)))))

(defn my-trades
  "Normalizes account trade price, quantity, quote quantity, and commission."
  [value]
  (mapv #(normalize-decimal-fields
          :spot/my-trades
          (ensure-map :spot/my-trades %)
          trade-decimal-fields)
        (ensure-array :spot/my-trades value)))

(defn- normalize-fill
  [endpoint-id value]
  (normalize-decimal-fields endpoint-id
                            (ensure-map endpoint-id value)
                            #{:commission :price :qty}))

(defn- normalize-order-map
  [endpoint-id value]
  (let [result (normalize-decimal-fields endpoint-id
                                         (ensure-map endpoint-id value)
                                         order-decimal-fields)]
    (cond-> result
      (contains? result :fills)
      (update :fills
              (fn [fills]
                (when-not (sequential? fills)
                  (fail! endpoint-id "Order fills must be an array."))
                (mapv #(normalize-fill endpoint-id %) fills))))))

(defn new-order
  "Normalizes a new-order response."
  [value]
  (normalize-order-map :spot/new-order value))

(defn query-order
  "Normalizes a query-order response."
  [value]
  (normalize-order-map :spot/query-order value))

(defn cancel-order
  "Normalizes a cancel-order response."
  [value]
  (normalize-order-map :spot/cancel-order value))

(defn open-orders
  "Normalizes every item in an open-orders response array."
  [value]
  (mapv #(normalize-order-map :spot/open-orders %)
        (ensure-array :spot/open-orders value)))

(defn test-order
  "Normalizes optional test-order commission calculation fields."
  [value]
  (let [endpoint-id :spot/test-order
        result (ensure-map endpoint-id value)]
    (reduce (fn [normalized field]
              (if (contains? normalized field)
                (update normalized field #(normalize-commission-map endpoint-id %))
                normalized))
            result
            [:standardCommissionForOrder
             :specialCommissionForOrder
             :taxCommissionForOrder
             :discount])))
