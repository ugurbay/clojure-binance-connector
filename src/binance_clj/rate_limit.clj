(ns binance-clj.rate-limit
  "Binance REST rate-limit header parsing and local observation state."
  (:require [binance-clj.errors :as errors]
            [clojure.string :as str]))

(def ^:private interval-units
  {"s" :second
   "m" :minute
   "h" :hour
   "d" :day})

(def ^:private weight-pattern
  #"^x-mbx-used-weight-(\d+)([smhd])$")

(def ^:private orders-pattern
  #"^x-mbx-order-count-(\d+)([smhd])$")

(defn- fail!
  [message data]
  (throw (errors/connector-error :configuration message data)))

(defn- first-header-value
  [value]
  (cond
    (string? value) value
    (sequential? value) (first value)
    :else nil))

(defn normalize-headers
  "Returns a lowercase string-keyed header map with vector string values."
  [headers]
  (when-not (map? headers)
    (fail! "HTTP headers must be a map." {:field :headers}))
  (into {}
        (map (fn [[key value]]
               [(str/lower-case (if (keyword? key) (name key) (str key)))
                (cond
                  (nil? value) []
                  (string? value) [value]
                  (sequential? value) (mapv str value)
                  :else [(str value)])]))
        headers))

(defn- parse-count
  [value]
  (when-let [text (first-header-value value)]
    (when (re-matches #"\d+" text)
      (parse-long text))))

(defn- header-observations
  [headers pattern]
  (into {}
        (keep (fn [[header values]]
                (when-let [[_ amount unit] (re-matches pattern header)]
                  (when-let [count-value (parse-count values)]
                    [[(get interval-units unit) (parse-long amount)] count-value]))))
        headers))

(defn parse-headers
  "Extracts observed request weight, order count, and Retry-After seconds."
  [headers]
  (let [normalized (normalize-headers headers)]
    {:request-weight (header-observations normalized weight-pattern)
     :orders (header-observations normalized orders-pattern)
     :retry-after-seconds (parse-count (get normalized "retry-after"))}))

(defn create-tracker
  "Creates a local rate-limit tracker. The optional clock returns milliseconds."
  ([] (create-tracker #(System/currentTimeMillis)))
  ([clock]
   (when-not (fn? clock)
     (fail! "Rate-limit tracker clock must be a function." {:field :clock}))
   {:clock clock
    :state (atom {:estimated-request-weight 0
                  :raw-request-count 0
                  :request-weight {}
                  :orders {}
                  :retry-after-seconds nil
                  :updated-at-ms nil})}))

(defn tracker?
  "Returns true for trackers created by this namespace."
  [value]
  (and (map? value)
       (fn? (:clock value))
       (instance? clojure.lang.IAtom (:state value))))

(defn record-attempt!
  "Records one actual wire attempt and its endpoint weight estimate."
  [tracker request]
  (when-not (tracker? tracker)
    (fail! "Invalid rate-limit tracker." {:field :rate-limit-tracker}))
  (let [weight (or (:weight request) 0)]
    (when-not (and (integer? weight) (not (neg? weight)))
      (fail! "Request weight must be a non-negative integer." {:field :weight}))
    (swap! (:state tracker)
           (fn [state]
             (-> state
                 (update :raw-request-count inc)
                 (update :estimated-request-weight + weight)
                 (assoc :updated-at-ms ((:clock tracker))))))))

(defn record-response!
  "Merges authoritative Binance response-header observations into the tracker."
  [tracker headers]
  (when-not (tracker? tracker)
    (fail! "Invalid rate-limit tracker." {:field :rate-limit-tracker}))
  (let [{:keys [request-weight orders retry-after-seconds]} (parse-headers headers)]
    (swap! (:state tracker)
           (fn [state]
             (-> state
                 (update :request-weight merge request-weight)
                 (update :orders merge orders)
                 (assoc :retry-after-seconds retry-after-seconds
                        :updated-at-ms ((:clock tracker))))))))

(defn snapshot
  "Returns the tracker's immutable, non-sensitive current state."
  [tracker]
  (when-not (tracker? tracker)
    (fail! "Invalid rate-limit tracker." {:field :rate-limit-tracker}))
  @(:state tracker))
