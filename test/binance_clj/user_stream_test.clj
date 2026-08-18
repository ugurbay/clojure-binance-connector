(ns binance-clj.user-stream-test
  (:require [binance-clj.client :as client]
            [binance-clj.json :as json]
            [binance-clj.spot.user-stream :as sut]
            [clojure.test :refer [deftest is]]))

(defn- harness
  []
  (let [callbacks (atom [])
        sent (atom [])
        tasks (atom [])]
    {:callbacks callbacks
     :sent sent
     :tasks tasks
     :transport
     {:open! (fn [_ callback-map]
               (swap! callbacks conj callback-map)
               {:socket (count @callbacks)})
      :send-text! (fn [_ payload]
                    (swap! sent conj (json/read-json payload))
                    true)
      :close-socket! (constantly true)}
     :scheduler {:schedule! (fn [delay task]
                              (swap! tasks conj {:delay delay :task task})
                              task)}}))

(deftest signature-subscription-is-refreshed-after-reconnect-test
  (let [h (harness)
        now (atom 1000)
        signed-parameters (atom [])
        connector-client
        (client/create-client
         {:clock #(deref now)
          :credentials {:api-key "test-api-key"}
          :services {:sign-websocket
                     (fn [parameters]
                       (swap! signed-parameters conj parameters)
                       {:signature (str "signature-" (:timestamp parameters))})}
          :transport {:send! (constantly {})}})
        stream (sut/create-stream connector-client
                                  {:jitter-fn (constantly 0)
                                   :scheduler (:scheduler h)
                                   :transport (:transport h)})]
    (sut/connect! stream)
    (is (= "userDataStream.subscribe.signature" (:method (first @(:sent h)))))
    (is (= 1000 (get-in (first @(:sent h)) [:params :timestamp])))
    (is (number? (get-in (first @(:sent h)) [:params :recvWindow])))
    (is (= 5000M (get-in (first @signed-parameters) [:recvWindow])))

    ((:on-text (first @(:callbacks h)))
     "{\"id\":\"uds-subscribe-1\",\"status\":200,\"result\":{\"subscriptionId\":8}}")
    (is (= {:status :active :subscription-id 8}
           (:user-stream (sut/snapshot stream))))

    (reset! now 2000)
    ((:on-close (first @(:callbacks h))) 1006 "lost")
    ((:task (first (filter #(= 250 (:delay %)) @(:tasks h)))))
    (is (= 2 (count @signed-parameters)))
    (is (= 2000 (get-in (second @(:sent h)) [:params :timestamp])))
    (is (not= (get-in (first @(:sent h)) [:params :signature])
              (get-in (second @(:sent h)) [:params :signature])))
    (sut/close! stream)
    (client/close! connector-client)))
