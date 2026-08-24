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
      (is (= 10.1M (get-in event [:events 0 :c])))))
  (testing "aggregate trade has exact finance fields and stable aliases"
    (let [event (sut/normalize-stream-message
                 {:e "aggTrade" :E 1672515782136 :s "BTCUSDT" :a 12345
                  :p "62123.45000000" :q "0.12500000" :f 100 :l 105
                  :T 1672515782135 :m false :M true :future "kept"})]
      (is (= :market-event (:kind event)))
      (is (= "aggTrade" (:event-type event)))
      (is (= 12345 (:aggregate-trade-id event)))
      (is (= 62123.45000000M (:price event)))
      (is (= 0.12500000M (:quantity event)))
      (is (= "BTCUSDT" (:symbol event)))
      (is (= 100 (:first-trade-id event)))
      (is (= 105 (:last-trade-id event)))
      (is (= 1672515782135 (:trade-time event)))
      (is (false? (:buyer-market-maker? event)))
      (is (= "kept" (:future event))))))

(deftest malformed-aggregate-trade-is-rejected-test
  (doseq [payload [{:e "aggTrade" :E 1 :s "BTCUSDT" :a 2
                    :p "not-a-price" :q "1" :f 3 :l 3 :T 1 :m false}
                   {:e "aggTrade" :E 1 :s "BTCUSDT" :a 2
                    :p "1" :q "0" :f 3 :l 3 :T 1 :m false}
                   {:e "aggTrade" :E 1 :s "BTCUSDT" :a nil
                    :p "1" :q "1" :f 3 :l 3 :T 1 :m false}
                   {:e "aggTrade" :E 1 :s "BTCUSDT" :a 2
                    :p "1" :q "1" :f 3 :l 3 :T 1 :m nil}
                   {:e "aggTrade" :E 1 :s "BTCUSDT" :a 2
                    :p "1" :q "1" :f 4 :l 3 :T 1 :m false}]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/normalize-stream-message payload)))))

(deftest user-events-preserve-original-fields-and-add-stable-aliases-test
  (let [event (sut/normalize-websocket-api-message
               {:subscriptionId 7
                :event {:e "executionReport" :s "BTCUSDT" :c "clj-1"
                        :x "TRADE" :X "PARTIALLY_FILLED" :i 42
                        :p "100.00" :q "0.010" :L "101.25" :l "0.005"
                        :z "0.005" :n "0.000005" :N "BTC" :t 77
                        :futureField "kept"}})]
    (is (= :user-event (:kind event)))
    (is (= 7 (:subscription-id event)))
    (is (= "clj-1" (:client-order-id event)))
    (is (= "PARTIALLY_FILLED" (:order-status event)))
    (is (= 100.00M (:p event)))
    (is (= 101.25M (:L event)))
    (is (= 0.005M (:l event)))
    (is (= 0.000005M (:n event)))
    (is (instance? BigDecimal (:l event)))
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
