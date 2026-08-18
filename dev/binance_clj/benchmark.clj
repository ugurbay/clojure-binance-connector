(ns binance-clj.benchmark
  "Small dependency-free Phase 9 microbenchmark baseline."
  (:require [binance-clj.auth :as auth]
            [binance-clj.auth.hmac :as hmac]
            [binance-clj.json :as json]
            [binance-clj.websocket.buffer :as buffer]
            [binance-clj.websocket.normalize :as normalize]))

(def ^:private market-json
  (str "{\"stream\":\"btcusdt@bookTicker\",\"data\":{"
       "\"u\":400900217,\"s\":\"BTCUSDT\","
       "\"b\":\"62840.12000000\",\"B\":\"1.25000000\","
       "\"a\":\"62840.13000000\",\"A\":\"0.75000000\"}}"))

(def ^:private execution-message
  {:subscriptionId 1
   :event {:E 1499405658658
           :X "PARTIALLY_FILLED"
           :c "clj-benchmark"
           :e "executionReport"
           :i 4293153
           :n "0.00000100"
           :p "62840.12000000"
           :q "0.01000000"
           :s "BTCUSDT"
           :x "TRADE"
           :z "0.00500000"}})

(defn- percentile
  [sorted-samples ratio]
  (nth sorted-samples
       (min (dec (count sorted-samples))
            (dec (long (Math/ceil (* ratio (count sorted-samples))))))))

(defn- sample-scenario
  [operation]
  (let [warmup-iterations 5000
        batches 30
        operations-per-batch 1000
        sink (volatile! nil)]
    (dotimes [_ warmup-iterations]
      (vreset! sink (operation)))
    (let [samples
          (vec
           (for [_ (range batches)]
             (let [started (System/nanoTime)]
               (dotimes [_ operations-per-batch]
                 (vreset! sink (operation)))
               (/ (- (System/nanoTime) started) operations-per-batch))))
          sorted (vec (sort samples))
          p50 (percentile sorted 0.50)
          p95 (percentile sorted 0.95)]
      {:operations (* batches operations-per-batch)
       :p50-ns-per-operation (long p50)
       :p95-ns-per-operation (long p95)
       :throughput-per-second (long (/ 1000000000.0 p50))})))

(defn -main
  "Prints reproducible local baselines without network calls or credentials."
  [& _args]
  (let [signer (hmac/create-signer "phase9-benchmark-secret")
        event-buffer (buffer/create-buffer {:capacity 16})
        scenarios
        {:bounded-buffer-roundtrip
         #(do (buffer/offer! event-buffer execution-message)
              (buffer/poll! event-buffer 0))
         :hmac-websocket-sign
         #(auth/sign-websocket signer
                               {:apiKey "phase9-benchmark-key"
                                :recvWindow 5000M
                                :timestamp 1770123456789})
         :market-json-parse-normalize
         #(normalize/normalize-stream-message (json/read-json market-json))
         :user-event-normalize
         #(normalize/normalize-websocket-api-message execution-message)}
        results (into (sorted-map)
                      (map (fn [[name operation]]
                             [name (sample-scenario operation)]))
                      scenarios)]
    (println
     (pr-str
      {:clojure-version (clojure-version)
       :java-version (System/getProperty "java.version")
       :results results}))))
