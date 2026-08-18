(ns binance-clj.websocket-test
  (:require [binance-clj.json :as json]
            [binance-clj.websocket :as sut]
            [binance-clj.websocket.normalize :as normalize]
            [clojure.test :refer [deftest is testing]]))

(defn- harness
  []
  (let [callbacks (atom [])
        closed-sockets (atom [])
        sent (atom [])
        tasks (atom [])
        socket-id (atom 0)]
    {:callbacks callbacks
     :closed-sockets closed-sockets
     :sent sent
     :tasks tasks
     :transport
     {:open! (fn [_url callback-map]
               (swap! callbacks conj callback-map)
               {:socket (swap! socket-id inc)})
      :send-text! (fn [socket payload]
                    (swap! sent conj {:socket socket
                                      :value (json/read-json payload)})
                    true)
      :close-socket! (fn [socket]
                       (swap! closed-sockets conj socket)
                       true)
      :close! (constantly true)}
     :scheduler
     {:schedule! (fn [delay-ms task]
                   (swap! tasks conj {:delay-ms delay-ms :task task})
                   task)
      :close! (constantly true)}}))

(defn- run-task!
  [harness predicate]
  (let [entry (first (filter predicate @(:tasks harness)))]
    ((:task entry))
    entry))

(deftest connect-subscribe-deduplicate-and-restore-test
  (let [h (harness)
        connection (sut/create-connection
                    {:jitter-fn (constantly 0)
                     :normalizer normalize/normalize-stream-message
                     :scheduler (:scheduler h)
                     :transport (:transport h)
                     :url "wss://example.test/stream"})]
    (is (true? (sut/subscribe! connection "btcusdt@ticker")))
    (is (false? (sut/subscribe! connection "btcusdt@ticker")))
    (is (true? (sut/connect! connection)))
    (is (= "SUBSCRIBE" (get-in @(:sent h) [0 :value :method])))
    (is (= ["btcusdt@ticker"] (get-in @(:sent h) [0 :value :params])))

    (testing "unexpected close uses exponential backoff and restores registry"
      ((:on-close (first @(:callbacks h))) 1006 "lost")
      (is (= 250 (:delay-ms (run-task! h #(= 250 (:delay-ms %))))))
      (is (= 2 (count @(:sent h))))
      (is (= ["btcusdt@ticker"] (get-in @(:sent h) [1 :value :params]))))

    (is (true? (sut/unsubscribe! connection "btcusdt@ticker")))
    (is (false? (sut/unsubscribe! connection "btcusdt@ticker")))
    (is (= "UNSUBSCRIBE" (get-in @(:sent h) [2 :value :method])))
    (is (true? (sut/close! connection)))
    (is (false? (sut/close! connection)))))

(deftest event-dispatch-malformed-tolerance-and-renewal-test
  (let [h (harness)
        connection (sut/create-connection
                    {:buffer-capacity 1
                     :clock (constantly 1234)
                     :jitter-fn (constantly 0)
                     :normalizer normalize/normalize-stream-message
                     :renewal-ms 5000
                     :scheduler (:scheduler h)
                     :transport (:transport h)
                     :url "wss://example.test/stream"})]
    (sut/connect! connection)
    (let [receive! (:on-text (first @(:callbacks h)))]
      (receive! "not-json")
      (is (= :malformed (:kind (sut/poll-event! connection 0))))
      (receive! "{\"e\":\"bookTicker\",\"b\":\"1.25\"}")
      (is (= 1.25M (:b (sut/poll-event! connection 0)))))
    (is (= 5000 (:delay-ms (run-task! h #(= 5000 (:delay-ms %))))))
    (run-task! h #(zero? (:delay-ms %)))
    (is (= 2 (:generation (sut/snapshot connection))))
    (sut/close! connection)))

(deftest server-shutdown-causes-immediate-reconnect-test
  (let [h (harness)
        connection (sut/create-connection
                    {:normalizer normalize/normalize-stream-message
                     :scheduler (:scheduler h)
                     :transport (:transport h)
                     :url "wss://example.test/stream"})]
    (sut/connect! connection)
    ((:on-text (first @(:callbacks h)))
     "{\"e\":\"serverShutdown\",\"E\":1770123456789}")
    (is (= 0 (:delay-ms (run-task! h #(zero? (:delay-ms %))))))
    (is (= 2 (:generation (sut/snapshot connection))))
    (sut/close! connection)))

(deftest explicit-renewal-uses-the-same-restore-path-test
  (let [h (harness)
        connection (sut/create-connection
                    {:normalizer normalize/normalize-stream-message
                     :scheduler (:scheduler h)
                     :transport (:transport h)
                     :url "wss://example.test/stream"})]
    (sut/subscribe! connection "btcusdt@bookTicker")
    (sut/connect! connection)
    (is (true? (sut/renew! connection)))
    (is (false? (sut/renew! connection)))
    (run-task! h #(zero? (:delay-ms %)))
    (is (= 2 (:generation (sut/snapshot connection))))
    (is (= 2 (count @(:sent h))))
    (is (= ["btcusdt@bookTicker"]
           (get-in @(:sent h) [1 :value :params])))
    (sut/close! connection)))

(deftest missing-server-heartbeat-causes-reconnect-test
  (let [h (harness)
        now (atom 0)
        connection (sut/create-connection
                    {:clock #(deref now)
                     :heartbeat-timeout-ms 1000
                     :normalizer normalize/normalize-stream-message
                     :scheduler (:scheduler h)
                     :transport (:transport h)
                     :url "wss://example.test/stream"})]
    (sut/connect! connection)
    (reset! now 1001)
    (run-task! h #(= 1000 (:delay-ms %)))
    (run-task! h #(zero? (:delay-ms %)))
    (is (= 2 (:generation (sut/snapshot connection))))
    (sut/close! connection)))
