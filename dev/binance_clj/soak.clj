(ns binance-clj.soak
  "Short in-memory WebSocket event soak used by the Phase 9 release gate."
  (:require [binance-clj.websocket.buffer :as buffer]
            [binance-clj.websocket.normalize :as normalize]))

(def ^:private event
  {:E 1770123456789
   :X "NEW"
   :c "clj-soak"
   :e "executionReport"
   :i 1
   :p "62840.12000000"
   :q "0.01000000"
   :s "BTCUSDT"
   :x "NEW"
   :z "0.00000000"})

(defn- duration-seconds
  []
  (let [configured (some-> (System/getenv "BINANCE_SOAK_SECONDS") parse-long)]
    (if (and configured (<= 1 configured 300)) configured 5)))

(defn- used-heap-bytes
  []
  (let [runtime (Runtime/getRuntime)]
    (- (.totalMemory runtime) (.freeMemory runtime))))

(defn- settled-heap-bytes
  []
  (System/gc)
  (Thread/sleep 200)
  (used-heap-bytes))

(defn -main
  "Runs a bounded producer/consumer loop and rejects queue or thread growth."
  [& _args]
  (let [seconds (duration-seconds)
        deadline (+ (System/nanoTime) (* seconds 1000000000))
        event-buffer (buffer/create-buffer {:capacity 1024
                                            :overflow-policy :drop-oldest})
        heap-before (settled-heap-bytes)
        threads-before (Thread/activeCount)
        processed
        (loop [count 0]
          (if (>= (System/nanoTime) deadline)
            count
            (let [normalized (normalize/normalize-user-event event)]
              (buffer/offer! event-buffer normalized)
              (buffer/poll! event-buffer 0)
              (recur (inc count)))))
        heap-after (settled-heap-bytes)
        threads-after (Thread/activeCount)
        buffer-state (buffer/snapshot event-buffer)
        result {:buffer-depth (:depth buffer-state)
                :dropped (:dropped buffer-state)
                :duration-seconds seconds
                :events-processed processed
                :heap-delta-bytes (- heap-after heap-before)
                :messages-per-second (quot processed seconds)
                :thread-delta (- threads-after threads-before)}]
    (println (pr-str result))
    (when (or (pos? (:buffer-depth result))
              (pos? (:dropped result))
              (> (:thread-delta result) 1))
      (System/exit 1))))
