(ns binance-clj.filters
  "Pure local preflight checks for the V1 Spot MARKET and LIMIT order subset."
  (:require [binance-clj.decimal :as decimal]
            [binance-clj.errors :as errors]
            [clojure.string :as str])
  (:import [java.math BigDecimal]))

(def ^:private sides #{"BUY" "SELL"})
(def ^:private order-types #{"LIMIT" "MARKET"})
(def ^:private time-in-force-values #{"FOK" "GTC" "IOC"})

(def ^:private filter-decimal-fields
  {"PRICE_FILTER" [:minPrice :maxPrice :tickSize]
   "LOT_SIZE" [:minQty :maxQty :stepSize]
   "MARKET_LOT_SIZE" [:minQty :maxQty :stepSize]
   "MIN_NOTIONAL" [:minNotional]
   "NOTIONAL" [:minNotional :maxNotional]})

(def ^:private filter-boolean-fields
  {"MIN_NOTIONAL" [:applyToMarket]
   "NOTIONAL" [:applyMinToMarket :applyMaxToMarket]})

(defn- fail!
  [message data]
  (throw (errors/connector-error :validation message data)))

(defn- enum-value
  [field allowed value]
  (let [normalized (cond
                     (keyword? value) (str/upper-case (name value))
                     (string? value) (str/upper-case value)
                     :else nil)]
    (when-not (contains? allowed normalized)
      (fail! "Order enum value is unsupported." {:field field}))
    normalized))

(defn- exact-positive-decimal
  [field value]
  (let [parsed (try
                 (decimal/parse value)
                 (catch clojure.lang.ExceptionInfo _
                   (fail! "Order financial value must be an exact decimal."
                          {:field field})))]
    (when-not (pos? (.signum ^BigDecimal parsed))
      (fail! "Order financial value must be positive." {:field field}))
    parsed))

(defn- filter-type
  [filter-map]
  (let [value (:filterType filter-map)]
    (cond
      (and (string? value) (not (str/blank? value))) (str/upper-case value)
      (keyword? value) (str/upper-case (name value))
      :else nil)))

(defn- normalize-filter
  [filter-map]
  (when-not (map? filter-map)
    (fail! "Every symbol filter must be an object."
           {:field :symbol-info}))
  (let [type (filter-type filter-map)]
    (when-not type
      (fail! "Every symbol filter must declare filterType."
             {:field :symbol-info :rule :filterType}))
    (let [with-decimals
          (reduce
           (fn [result field]
             (when-not (contains? result field)
               (fail! "A known Binance filter is missing a required rule."
                      {:field :symbol-info :filter-type type :rule field}))
             (assoc result field
                    (try
                      (decimal/parse (get result field))
                      (catch clojure.lang.ExceptionInfo _
                        (fail! "A Binance filter rule must be an exact decimal."
                               {:field :symbol-info
                                :filter-type type
                                :rule field})))))
           (assoc filter-map :filterType type)
           (get filter-decimal-fields type []))]
      (doseq [field (get filter-boolean-fields type [])]
        (when-not (boolean? (get with-decimals field))
          (fail! "A known Binance filter flag must be boolean."
                 {:field :symbol-info :filter-type type :rule field})))
      with-decimals)))

(defn- filter-index
  [symbol-info]
  (when-not (map? symbol-info)
    (fail! "symbol-info must be an exchangeInfo symbol map."
           {:field :symbol-info}))
  (when-not (sequential? (:filters symbol-info))
    (fail! "symbol-info filters must be an array."
           {:field :symbol-info}))
  (reduce
   (fn [result filter-map]
     (let [normalized (normalize-filter filter-map)
           type (:filterType normalized)]
       (when (contains? result type)
         (fail! "symbol-info contains a duplicate Binance filter."
                {:field :symbol-info :filter-type type}))
       (assoc result type normalized)))
   {}
   (:filters symbol-info)))

(defn- positive-rule?
  [value]
  (and (instance? BigDecimal value) (pos? (.signum ^BigDecimal value))))

(defn- below?
  [^BigDecimal value ^BigDecimal minimum]
  (neg? (.compareTo value minimum)))

(defn- above?
  [^BigDecimal value ^BigDecimal maximum]
  (pos? (.compareTo value maximum)))

(defn- aligned?
  [^BigDecimal value ^BigDecimal increment]
  (zero? (.signum (.remainder value increment))))

(defn- validate-range!
  [field value filter-map filter-name minimum-key maximum-key increment-key]
  (let [minimum (get filter-map minimum-key)
        maximum (get filter-map maximum-key)
        increment (get filter-map increment-key)]
    (when (and (positive-rule? minimum) (below? value minimum))
      (fail! "Order value is below the Binance filter minimum."
             {:field field :filter-type filter-name :rule minimum-key}))
    (when (and (positive-rule? maximum) (above? value maximum))
      (fail! "Order value is above the Binance filter maximum."
             {:field field :filter-type filter-name :rule maximum-key}))
    (when (and (positive-rule? increment) (not (aligned? value increment)))
      (fail! "Order value is not aligned to the Binance filter increment."
             {:field field :filter-type filter-name :rule increment-key}))))

(defn- validate-symbol-contract!
  [symbol-info order]
  (when-not (= (:symbol order) (:symbol symbol-info))
    (fail! "Order symbol does not match symbol-info."
           {:field :symbol}))
  (when-not (= "TRADING" (:status symbol-info))
    (fail! "Symbol is not in TRADING status."
           {:field :symbol :rule :status}))
  (when (false? (:isSpotTradingAllowed symbol-info))
    (fail! "Spot trading is disabled for this symbol."
           {:field :symbol :rule :isSpotTradingAllowed}))
  (when-not (and (sequential? (:orderTypes symbol-info))
                 (some #{(:type order)} (:orderTypes symbol-info)))
    (fail! "Order type is not enabled for this symbol."
           {:field :type :rule :orderTypes}))
  (when (and (:quote-order-qty order)
             (not (true? (:quoteOrderQtyMarketAllowed symbol-info))))
    (fail! "quote-order-qty is not enabled for this symbol."
           {:field :quote-order-qty :rule :quoteOrderQtyMarketAllowed})))

(defn- market-notional
  [order reference-price]
  (if-let [quote-order-qty (:quote-order-qty order)]
    quote-order-qty
    (if reference-price
      (.multiply ^BigDecimal reference-price ^BigDecimal (:quantity order))
      (fail! "MARKET notional validation requires a reference price."
             {:field :reference-price
              :rule :market-notional-reference-price}))))

(defn- validate-min-notional!
  [order filter-map reference-price]
  (let [market? (= "MARKET" (:type order))
        applies? (or (not market?) (true? (:applyToMarket filter-map)))]
    (when applies?
      (let [notional (if market?
                       (market-notional order reference-price)
                       (.multiply ^BigDecimal (:price order)
                                  ^BigDecimal (:quantity order)))
            minimum (:minNotional filter-map)]
        (when (and (positive-rule? minimum) (below? notional minimum))
          (fail! "Order notional is below the Binance minimum."
                 {:field :notional
                  :filter-type "MIN_NOTIONAL"
                  :rule :minNotional}))))))

(defn- validate-notional!
  [order filter-map reference-price]
  (let [market? (= "MARKET" (:type order))
        check-min? (or (not market?) (true? (:applyMinToMarket filter-map)))
        check-max? (or (not market?) (true? (:applyMaxToMarket filter-map)))]
    (when (or check-min? check-max?)
      (let [notional (if market?
                       (market-notional order reference-price)
                       (.multiply ^BigDecimal (:price order)
                                  ^BigDecimal (:quantity order)))
            minimum (:minNotional filter-map)
            maximum (:maxNotional filter-map)]
        (when (and check-min? (positive-rule? minimum) (below? notional minimum))
          (fail! "Order notional is below the Binance minimum."
                 {:field :notional :filter-type "NOTIONAL" :rule :minNotional}))
        (when (and check-max? (positive-rule? maximum) (above? notional maximum))
          (fail! "Order notional is above the Binance maximum."
                 {:field :notional :filter-type "NOTIONAL" :rule :maxNotional}))))))

(defn- normalize-shape
  [order]
  (when-not (map? order)
    (fail! "order must be a map." {:field :order}))
  (let [symbol (:symbol order)]
    (when-not (and (string? symbol) (not (str/blank? symbol)))
      (fail! "Order symbol must be a non-blank string." {:field :symbol}))
    (let [side (enum-value :side sides (:side order))
          type (enum-value :type order-types (:type order))
          limit? (= "LIMIT" type)
          has-quantity? (contains? order :quantity)
          has-quote-quantity? (contains? order :quote-order-qty)]
      (when (and has-quantity? has-quote-quantity?)
        (fail! "quantity and quote-order-qty cannot be used together."
               {:field :quantity}))
      (when (and limit? (or (not has-quantity?) has-quote-quantity?
                            (not (contains? order :price))
                            (not (contains? order :time-in-force))))
        (fail! "LIMIT requires quantity, price, and time-in-force."
               {:field :type}))
      (when (and (not limit?) (not (or has-quantity? has-quote-quantity?)))
        (fail! "MARKET requires quantity or quote-order-qty."
               {:field :type}))
      (when (and (not limit?)
                 (or (contains? order :price) (contains? order :time-in-force)))
        (fail! "MARKET does not accept price or time-in-force in V1."
               {:field :type}))
      (cond-> (assoc order :side side :type type)
        has-quantity? (update :quantity #(exact-positive-decimal :quantity %))
        has-quote-quantity? (update :quote-order-qty
                                    #(exact-positive-decimal :quote-order-qty %))
        (contains? order :price) (update :price #(exact-positive-decimal :price %))
        (contains? order :time-in-force)
        (update :time-in-force
                #(enum-value :time-in-force time-in-force-values %))))))

(defn validate-order
  "Returns a normalized V1 MARKET/LIMIT order or throws a local validation error.

  `symbol-info` is one item from exchangeInfo `:symbols`; known filter numbers may
  be raw strings or normalized BigDecimals. MARKET orders using base `:quantity`
  require `:reference-price` when an applicable notional filter exists. No value
  is rounded or adjusted."
  ([symbol-info order] (validate-order symbol-info order {}))
  ([symbol-info order {:keys [reference-price]}]
   (let [normalized (normalize-shape order)
         filters (filter-index symbol-info)
         parsed-reference (when (some? reference-price)
                            (exact-positive-decimal :reference-price reference-price))
         price-filter (get filters "PRICE_FILTER")
         lot-filter (get filters "LOT_SIZE")
         market-lot-filter (get filters "MARKET_LOT_SIZE")]
     (validate-symbol-contract! symbol-info normalized)
     (when (and price-filter (:price normalized))
       (validate-range! :price (:price normalized) price-filter "PRICE_FILTER"
                        :minPrice :maxPrice :tickSize))
     (when (and lot-filter (:quantity normalized))
       (validate-range! :quantity (:quantity normalized) lot-filter "LOT_SIZE"
                        :minQty :maxQty :stepSize))
     (when (and market-lot-filter
                (= "MARKET" (:type normalized))
                (:quantity normalized))
       (validate-range! :quantity (:quantity normalized)
                        market-lot-filter "MARKET_LOT_SIZE"
                        :minQty :maxQty :stepSize))
     (when-let [minimum-filter (get filters "MIN_NOTIONAL")]
       (validate-min-notional! normalized minimum-filter parsed-reference))
     (when-let [notional-filter (get filters "NOTIONAL")]
       (validate-notional! normalized notional-filter parsed-reference))
     normalized)))
