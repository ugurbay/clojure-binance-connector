(ns binance-clj.websocket-normalize-test
  (:require [binance-clj.websocket.normalize :as sut]
            [clojure.test :refer [deftest is testing]])
  (:import [java.math BigDecimal]))

(deftest supported-market-events-use-exact-decimals-test
  (testing "combined book ticker"
    (let [event (sut/normalize-stream-message
                 {:stream "btcusdt@bookTicker"
                  :data {:u 1 :s "BTCUSDT" :b "100.0100" :B "2.500"
                         :a "100.0200" :A "3.000" :future "kept"}})]
      (is (= :market-event (:kind event)))
      (is (= "btcusdt@bookTicker" (:stream event)))
      (is (instance? BigDecimal (:b event)))
      (is (= 100.0100M (:b event)))
      (is (= "kept" (:future event)))))
  (testing "partial depth levels"
    (let [event (sut/normalize-stream-message
                 {:lastUpdateId 9
                  :bids [["1.10" "2.20"]]
                  :asks [["1.30" "4.40"]]})]
      (is (= "depth" (:event-type event)))
      (is (= [[1.10M 2.20M]] (:bids event)))))
  (testing "all-market mini ticker array"
    (let [event (sut/normalize-stream-message
                 [{:e "24hrMiniTicker" :s "BTCUSDT" :c "10.1"}])]
      (is (= :market-event-batch (:kind event)))
      (is (= 10.1M (get-in event [:events 0 :c]))))))

(deftest user-events-preserve-original-fields-and-add-stable-aliases-test
  (let [event (sut/normalize-websocket-api-message
               {:subscriptionId 7
                :event {:e "executionReport" :s "BTCUSDT" :c "clj-1"
                        :x "TRADE" :X "PARTIALLY_FILLED" :i 42
                        :p "100.00" :q "0.010" :z "0.005"
                        :futureField "kept"}})]
    (is (= :user-event (:kind event)))
    (is (= 7 (:subscription-id event)))
    (is (= "clj-1" (:client-order-id event)))
    (is (= "PARTIALLY_FILLED" (:order-status event)))
    (is (= 100.00M (:p event)))
    (is (= "kept" (:futureField event)))))

(deftest account-and-control-responses-are-distinct-test
  (let [account (sut/normalize-websocket-api-message
                 {:subscriptionId 1
                  :event {:e "outboundAccountPosition"
                          :B [{:a "BTC" :f "1.25" :l "0.50"}]}})
        response (sut/normalize-websocket-api-message
                  {:id "one" :status 200
                   :result {:subscriptionId 3}
                   :rateLimits []})]
    (is (= 1.25M (get-in account [:B 0 :f])))
    (is (= :response (:kind response)))
    (is (= 3 (get-in response [:result :subscriptionId])))))
