(ns binance-clj.spot.normalize
  "Lossless Public Spot REST response normalization."
  (:require [binance-clj.decimal :as decimal]
            [binance-clj.errors :as errors]))

(def ^:private ticker-decimal-fields
  #{:askPrice
    :askQty
    :bidPrice
    :bidQty
    :highPrice
    :lastPrice
    :lastQty
    :lowPrice
    :openPrice
    :prevClosePrice
    :price
    :priceChange
    :priceChangePercent
    :quoteVolume
    :volume
    :weightedAvgPrice})

(def ^:private filter-decimal-fields
  #{:askMultiplierDown
    :askMultiplierUp
    :bidMultiplierDown
    :bidMultiplierUp
    :maxNotional
    :maxPosition
    :maxPrice
    :maxQty
    :minNotional
    :minPrice
    :minQty
    :multiplierDown
    :multiplierUp
    :stepSize
    :tickSize})

(defn- fail!
  [message data]
  (throw (errors/connector-error :api message data)))

(defn- ensure-map
  [endpoint-id value]
  (when-not (map? value)
    (fail! "Binance response must be an object." {:endpoint-id endpoint-id}))
  value)

(defn- parse-decimal
  [endpoint-id value]
  (try
    (decimal/parse value)
    (catch clojure.lang.ExceptionInfo _
      (fail! "Binance financial field is not a plain decimal."
             {:endpoint-id endpoint-id}))))

(defn- normalize-decimal-fields
  [value endpoint-id fields]
  (reduce (fn [result field]
            (if (and (contains? result field) (some? (get result field)))
              (assoc result field (parse-decimal endpoint-id (get result field)))
              result))
          value
          fields))

(defn ping
  "Validates and preserves the empty ping response object."
  [value]
  (ensure-map :spot/ping value))

(defn server-time
  "Validates serverTime while preserving unknown response fields."
  [value]
  (let [result (ensure-map :spot/server-time value)
        server-time (:serverTime result)]
    (when-not (and (integer? server-time) (not (neg? server-time)))
      (fail! "serverTime must be a non-negative integer."
             {:endpoint-id :spot/server-time}))
    result))

(defn- normalize-filter
  [value]
  (-> (ensure-map :spot/exchange-info value)
      (normalize-decimal-fields :spot/exchange-info filter-decimal-fields)))

(defn exchange-info
  "Normalizes known filter decimals and preserves all unknown exchange fields."
  [value]
  (let [result (ensure-map :spot/exchange-info value)]
    (when-not (sequential? (:symbols result))
      (fail! "exchangeInfo symbols must be an array."
             {:endpoint-id :spot/exchange-info}))
    (cond-> (update result
                    :symbols
                    (fn [symbols]
                      (mapv (fn [symbol]
                              (let [symbol-map (ensure-map :spot/exchange-info symbol)]
                                (if (contains? symbol-map :filters)
                                  (do
                                    (when-not (sequential? (:filters symbol-map))
                                      (fail! "Symbol filters must be an array."
                                             {:endpoint-id :spot/exchange-info}))
                                    (update symbol-map :filters #(mapv normalize-filter %)))
                                  symbol-map)))
                            symbols)))
      (contains? result :exchangeFilters)
      (update :exchangeFilters
              (fn [filters]
                (when-not (sequential? filters)
                  (fail! "Exchange filters must be an array."
                         {:endpoint-id :spot/exchange-info}))
                (mapv normalize-filter filters))))))

(defn- normalize-object-or-array
  [endpoint-id value normalizer]
  (cond
    (map? value) (normalizer value)
    (sequential? value) (mapv (fn [item]
                                (normalizer (ensure-map endpoint-id item)))
                              value)
    :else (fail! "Binance response must be an object or array."
                 {:endpoint-id endpoint-id})))

(defn ticker-price
  "Normalizes ticker price objects or arrays."
  [value]
  (normalize-object-or-array
   :spot/ticker-price
   value
   #(normalize-decimal-fields % :spot/ticker-price #{:price})))

(defn ticker-24h
  "Normalizes FULL/MINI 24-hour ticker financial fields."
  [value]
  (normalize-object-or-array
   :spot/ticker-24h
   value
   #(normalize-decimal-fields % :spot/ticker-24h ticker-decimal-fields)))

(defn book-ticker
  "Normalizes best bid/ask price and quantity objects or arrays."
  [value]
  (normalize-object-or-array
   :spot/book-ticker
   value
   #(normalize-decimal-fields %
                              :spot/book-ticker
                              #{:askPrice :askQty :bidPrice :bidQty})))

(defn- depth-level
  [side value]
  (when-not (and (sequential? value) (<= 2 (count value)))
    (fail! "Depth level must contain price and quantity."
           {:endpoint-id :spot/depth :side side}))
  (-> (vec value)
      (assoc 0 (parse-decimal :spot/depth (first value)))
      (assoc 1 (parse-decimal :spot/depth (second value)))))

(defn depth
  "Normalizes order-book bid/ask price and quantity levels."
  [value]
  (let [result (ensure-map :spot/depth value)]
    (doseq [side [:bids :asks]]
      (when-not (sequential? (get result side))
        (fail! "Depth response sides must be arrays."
               {:endpoint-id :spot/depth :side side})))
    (-> result
        (update :bids #(mapv (partial depth-level :bids) %))
        (update :asks #(mapv (partial depth-level :asks) %)))))
