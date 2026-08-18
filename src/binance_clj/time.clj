(ns binance-clj.time
  "Timestamp units, receive-window rules, and server-offset-aware clocks."
  (:require [binance-clj.decimal :as decimal]
            [binance-clj.errors :as errors])
  (:import [java.math BigDecimal]))

(def default-recv-window
  "Binance's default receive window in milliseconds."
  5000M)

(def max-recv-window
  "Binance's maximum receive window in milliseconds."
  60000M)

(def timestamp-units
  "Timestamp units accepted by Binance Spot signed requests."
  #{:millisecond :microsecond})

(defn- fail!
  [message data]
  (throw (errors/connector-error :configuration message data)))

(defn normalize-recv-window
  "Validates Binance's millisecond recvWindow, including microsecond precision."
  [value]
  (let [window (try
                 (decimal/parse value)
                 (catch clojure.lang.ExceptionInfo _
                   (fail! "recv-window must be an exact decimal millisecond value."
                          {:field :recv-window})))
        scale (max 0 (.scale (.stripTrailingZeros ^BigDecimal window)))]
    (when (or (not (pos? window))
              (pos? (.compareTo ^BigDecimal window max-recv-window))
              (> scale 3))
      (fail! "recv-window must be positive, at most 60000 ms, with at most three decimals."
             {:field :recv-window}))
    window))

(defn current-timestamp
  "Reads a millisecond clock and returns a Binance timestamp in the chosen unit."
  [clock unit]
  (when-not (contains? timestamp-units unit)
    (fail! "time-unit must be :millisecond or :microsecond." {:field :time-unit}))
  (let [milliseconds (clock)]
    (when-not (and (integer? milliseconds) (not (neg? milliseconds)))
      (fail! "Clock must return a non-negative integer millisecond timestamp."
             {:field :clock}))
    (case unit
      :millisecond milliseconds
      :microsecond (*' milliseconds 1000))))

(defn calculate-offset-ms
  "Estimates server offset from the midpoint of one request/response interval."
  [server-time-ms request-start-ms response-end-ms]
  (when-not (every? #(and (integer? %) (not (neg? %)))
                    [server-time-ms request-start-ms response-end-ms])
    (fail! "Server-time synchronization values must be non-negative integers." {}))
  (when (< response-end-ms request-start-ms)
    (fail! "Synchronization response time cannot precede request time." {}))
  (- server-time-ms (quot (+ request-start-ms response-end-ms) 2)))

(defn synchronized-clock
  "Creates an offset-aware clock boundary.

  The returned map contains `:now`, `:offset-ms`, and `:synchronize!` functions.
  Callers inject `:now` into client config and update the offset after `/time`."
  [base-clock]
  (when-not (fn? base-clock)
    (fail! "base-clock must be a zero-argument function." {:field :clock}))
  (let [offset (atom 0)]
    {:now (fn []
            (let [base-value (base-clock)]
              (when-not (and (integer? base-value) (not (neg? base-value)))
                (fail! "Clock must return a non-negative integer millisecond timestamp."
                       {:field :clock}))
              (let [adjusted (+ base-value @offset)]
                (when (neg? adjusted)
                  (fail! "Adjusted clock must not return a negative timestamp."
                         {:field :clock}))
                adjusted)))
     :offset-ms (fn [] @offset)
     :synchronize! (fn [server-time-ms request-start-ms response-end-ms]
                     (reset! offset
                             (calculate-offset-ms server-time-ms
                                                  request-start-ms
                                                  response-end-ms)))}))
