(ns binance-clj.spot.streams
  "Public Spot market WebSocket stream names and shared connection facade."
  (:require [binance-clj.client :as client]
            [binance-clj.errors :as errors]
            [binance-clj.websocket :as websocket]
            [binance-clj.websocket.normalize :as normalize]
            [clojure.string :as str]))

(defn- fail!
  [message data]
  (throw (errors/connector-error :validation message data)))

(defn- stream-symbol
  [symbol]
  (when-not (and (string? symbol)
                 (not (str/blank? symbol))
                 (not (re-find #"\s" symbol)))
    (fail! "Stream symbol must be a non-blank symbol string."
           {:field :symbol}))
  (str/lower-case symbol))

(defn all-mini-tickers
  "Returns Binance's current all-market mini ticker stream name."
  []
  "!miniTicker@arr")

(defn ticker
  "Returns an individual 24-hour ticker stream name."
  [symbol]
  (str (stream-symbol symbol) "@ticker"))

(defn book-ticker
  "Returns an individual best bid/ask stream name."
  [symbol]
  (str (stream-symbol symbol) "@bookTicker"))

(defn partial-depth
  "Returns depth5/depth10/depth20, optionally at the 100 ms cadence."
  ([symbol levels] (partial-depth symbol levels 1000))
  ([symbol levels update-ms]
   (when-not (contains? #{5 10 20} levels)
     (fail! "Partial depth levels must be 5, 10, or 20." {:field :levels}))
   (when-not (contains? #{100 1000} update-ms)
     (fail! "Partial depth update interval must be 100 or 1000 ms."
            {:field :update-ms}))
   (str (stream-symbol symbol)
        "@depth"
        levels
        (when (= 100 update-ms) "@100ms"))))

(defn create-stream
  "Creates a disconnected market-stream manager owned by client."
  ([connector-client] (create-stream connector-client {}))
  ([connector-client options]
   (let [connection
         (websocket/create-connection
          (assoc options
                 :normalizer normalize/normalize-stream-message
                 :url (client/websocket-url connector-client :market-streams)))]
     (client/register-closeable! connector-client #(websocket/close! connection))
     connection)))

(def connect!
  "Opens a market-stream connection and restores desired subscriptions."
  websocket/connect!)
(def subscribe!
  "Adds one market stream to the desired subscription registry."
  websocket/subscribe!)
(def renew!
  "Rotates the socket immediately and restores desired subscriptions."
  websocket/renew!)
(def unsubscribe!
  "Removes one market stream from the desired subscription registry."
  websocket/unsubscribe!)
(def poll-event!
  "Returns the next normalized buffered market event."
  websocket/poll-event!)
(def snapshot
  "Returns secret-safe connection and backpressure state."
  websocket/snapshot)
(def close!
  "Closes the market-stream manager idempotently."
  websocket/close!)
