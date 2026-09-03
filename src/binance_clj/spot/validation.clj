(ns binance-clj.spot.validation
  "Public Spot REST parameter validation and wire-name normalization."
  (:require [binance-clj.errors :as errors]
            [binance-clj.json :as json]
            [clojure.string :as str]))

(def ^:private symbol-statuses
  #{"BREAK" "HALT" "TRADING"})

(def ^:private ticker-types
  #{"FULL" "MINI"})

(def ^:private kline-intervals
  #{"1s" "1m" "3m" "5m" "15m" "30m"
    "1h" "2h" "4h" "6h" "8h" "12h"
    "1d" "3d" "1w" "1M"})

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

(defn- symbols-value
  [endpoint-id value]
  (when-not (and (sequential? value) (seq value))
    (fail! endpoint-id "symbols must be a non-empty sequential collection."
           {:field :symbols}))
  (mapv #(non-blank-name endpoint-id :symbols %) value))

(defn- selection
  [endpoint-id params]
  (when (and (contains? params :symbol) (contains? params :symbols))
    (fail! endpoint-id "symbol and symbols cannot be used together." {}))
  (cond-> {}
    (contains? params :symbol)
    (assoc :symbol (non-blank-name endpoint-id :symbol (:symbol params)))

    (contains? params :symbols)
    (assoc :symbols (json/write-json (symbols-value endpoint-id (:symbols params))))

    (contains? params :symbol-status)
    (assoc :symbolStatus
           (enum-value endpoint-id
                       :symbol-status
                       symbol-statuses
                       (:symbol-status params)))))

(defn no-params
  "Rejects parameters for endpoints whose official contract has none."
  [{:keys [endpoint params] :as context}]
  (when (seq params)
    (fail! (:id endpoint) "Endpoint does not accept parameters." {}))
  context)

(defn exchange-info
  "Validates exchangeInfo selectors and returns wire-ready parameters."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)
        allowed #{:permissions :show-permission-sets? :symbol :symbol-status :symbols}]
    (reject-unknown! endpoint-id allowed params)
    (when (and (or (contains? params :symbol) (contains? params :symbols))
               (or (contains? params :permissions)
                   (contains? params :symbol-status)))
      (fail! endpoint-id
             "symbol/symbols cannot be combined with permissions or symbol-status."
             {}))
    (when (and (contains? params :show-permission-sets?)
               (not (boolean? (:show-permission-sets? params))))
      (fail! endpoint-id "show-permission-sets? must be boolean."
             {:field :show-permission-sets?}))
    (let [wire (cond-> (selection endpoint-id params)
                 (contains? params :permissions)
                 (assoc :permissions
                        (let [permissions (:permissions params)]
                          (if (sequential? permissions)
                            (do
                              (when-not (seq permissions)
                                (fail! endpoint-id
                                       "permissions collection must not be empty."
                                       {:field :permissions}))
                              (json/write-json
                               (mapv #(non-blank-name endpoint-id :permissions %)
                                     permissions)))
                            (non-blank-name endpoint-id :permissions permissions))))

                 (contains? params :show-permission-sets?)
                 (assoc :showPermissionSets (:show-permission-sets? params)))]
      (assoc context :params wire :request-weight 20))))

(defn market-selector
  "Validates common symbol/symbols/symbol-status market parameters."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)]
    (reject-unknown! endpoint-id #{:symbol :symbol-status :symbols} params)
    (assoc context :params (selection endpoint-id params))))

(defn ticker-24h
  "Validates 24-hour ticker params and resolves its dynamic request weight."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)]
    (reject-unknown! endpoint-id #{:symbol :symbol-status :symbols :type} params)
    (let [symbols (when (contains? params :symbols)
                    (symbols-value endpoint-id (:symbols params)))
          symbol-count (count symbols)
          weight (cond
                   (contains? params :symbol) 2
                   (<= 1 symbol-count 20) 2
                   (<= 21 symbol-count 100) 40
                   :else 80)
          wire (cond-> (selection endpoint-id params)
                 (contains? params :type)
                 (assoc :type
                        (enum-value endpoint-id :type ticker-types (:type params))))]
      (assoc context :params wire :request-weight weight))))

(defn ticker-price-or-book
  "Validates ticker price/book params and resolves 2-or-4 request weight."
  [{:keys [params] :as context}]
  (let [validated (market-selector context)]
    (assoc validated :request-weight (if (contains? params :symbol) 2 4))))

(defn- non-negative-long!
  [endpoint-id field value]
  (when-not (and (integer? value) (<= 0 value Long/MAX_VALUE))
    (fail! endpoint-id "Timestamp must be a non-negative 64-bit integer."
           {:field field}))
  (long value))

(defn- time-zone-value!
  [endpoint-id value]
  (let [[_ sign hours minutes]
        (when (string? value)
          (re-matches #"([+-]?)([0-9]{1,2})(?::([0-9]{2}))?" value))
        hour-value (some-> hours Long/parseLong)
        minute-value (some-> (or minutes "0") Long/parseLong)
        total (when (and hour-value minute-value (< minute-value 60))
                (* (if (= sign "-") -1 1)
                   (+ (* hour-value 60) minute-value)))]
    (when-not (and total (<= -720 total 840))
      (fail! endpoint-id "Kline time-zone must be within -12:00 and +14:00."
             {:field :time-zone}))
    value))

(defn klines
  "Validates public Spot kline parameters. Intervals are case-sensitive."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)
        allowed #{:end-time :interval :limit :start-time :symbol :time-zone}]
    (reject-unknown! endpoint-id allowed params)
    (doseq [required [:symbol :interval]]
      (when-not (contains? params required)
        (fail! endpoint-id "Klines requires symbol and interval."
               {:field required})))
    (let [interval (non-blank-name endpoint-id :interval (:interval params))
          limit (get params :limit 500)]
      (when-not (contains? kline-intervals interval)
        (fail! endpoint-id "Kline interval is unsupported or has incorrect case."
               {:field :interval}))
      (when-not (and (integer? limit) (<= 1 limit 1000))
        (fail! endpoint-id "Kline limit must be an integer from 1 to 1000."
               {:field :limit}))
      (let [start-time (when (contains? params :start-time)
                         (non-negative-long! endpoint-id :start-time (:start-time params)))
            end-time (when (contains? params :end-time)
                       (non-negative-long! endpoint-id :end-time (:end-time params)))]
        (when (and start-time end-time (> start-time end-time))
          (fail! endpoint-id "Kline start-time must not exceed end-time." {}))
        (assoc context
               :params (cond-> {:symbol (non-blank-name endpoint-id :symbol (:symbol params))
                                :interval interval}
                         (contains? params :start-time) (assoc :startTime start-time)
                         (contains? params :end-time) (assoc :endTime end-time)
                         (contains? params :limit) (assoc :limit limit)
                         (contains? params :time-zone)
                         (assoc :timeZone
                                (time-zone-value! endpoint-id (:time-zone params))))
               :request-weight 2)))))

(defn depth
  "Validates order-book params and resolves its limit-dependent weight."
  [{:keys [endpoint params] :as context}]
  (let [endpoint-id (:id endpoint)]
    (reject-unknown! endpoint-id #{:limit :symbol :symbol-status} params)
    (when-not (contains? params :symbol)
      (fail! endpoint-id "depth requires symbol." {:field :symbol}))
    (let [limit (get params :limit 100)]
      (when-not (and (integer? limit) (<= 1 limit 5000))
        (fail! endpoint-id "depth limit must be an integer from 1 to 5000."
               {:field :limit}))
      (let [weight (cond
                     (<= limit 100) 5
                     (<= limit 500) 25
                     (<= limit 1000) 50
                     :else 250)
            wire (cond-> (selection endpoint-id params)
                   (contains? params :limit) (assoc :limit limit))]
        (assoc context :params wire :request-weight weight)))))
