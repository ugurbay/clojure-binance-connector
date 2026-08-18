(ns binance-clj.websocket.buffer
  "Bounded non-blocking event buffer for WebSocket callback isolation."
  (:require [binance-clj.errors :as errors])
  (:import [java.util.concurrent ArrayBlockingQueue TimeUnit]))

(def overflow-policies
  "Supported behavior when the bounded queue is full."
  #{:drop-newest :drop-oldest})

(defn- fail!
  [message data]
  (throw (errors/connector-error :configuration message data)))

(defn create-buffer
  "Creates a bounded queue. WebSocket producer threads are never blocked."
  ([] (create-buffer {}))
  ([{:keys [capacity overflow-policy]
     :or {capacity 1024 overflow-policy :drop-oldest}}]
   (when-not (and (integer? capacity) (pos? capacity))
     (fail! "WebSocket buffer capacity must be a positive integer."
            {:field :capacity}))
   (when-not (contains? overflow-policies overflow-policy)
     (fail! "WebSocket overflow policy is unsupported."
            {:field :overflow-policy}))
   {:queue (ArrayBlockingQueue. capacity)
    :capacity capacity
    :overflow-policy overflow-policy
    :statistics (atom {:accepted 0 :dropped 0})}))

(defn offer!
  "Offers an event without blocking and returns true when it was retained."
  [{:keys [^ArrayBlockingQueue queue overflow-policy statistics]} event]
  (if (.offer queue event)
    (do (swap! statistics update :accepted inc) true)
    (case overflow-policy
      :drop-newest
      (do (swap! statistics update :dropped inc) false)

      :drop-oldest
      (do
        (.poll queue)
        (if (.offer queue event)
          (do
            (swap! statistics #(-> %
                                   (update :accepted inc)
                                   (update :dropped inc)))
            true)
          (do
            (swap! statistics update :dropped inc)
            false))))))

(defn poll!
  "Returns the next event, waiting up to timeout-ms; nil means no event."
  [{:keys [^ArrayBlockingQueue queue]} timeout-ms]
  (when-not (and (integer? timeout-ms) (not (neg? timeout-ms)))
    (fail! "WebSocket poll timeout must be a non-negative integer."
           {:field :timeout-ms}))
  (.poll queue timeout-ms TimeUnit/MILLISECONDS))

(defn snapshot
  "Returns non-sensitive queue capacity, depth, policy, and counters."
  [{:keys [^ArrayBlockingQueue queue capacity overflow-policy statistics]}]
  (assoc @statistics
         :capacity capacity
         :depth (.size queue)
         :overflow-policy overflow-policy))
