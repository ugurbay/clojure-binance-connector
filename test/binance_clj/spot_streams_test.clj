(ns binance-clj.spot-streams-test
  (:require [binance-clj.spot.streams :as sut]
            [clojure.test :refer [deftest is]]))

(deftest current-stream-name-contract-test
  (is (= "!miniTicker@arr" (sut/all-mini-tickers)))
  (is (= "btcusdt@ticker" (sut/ticker "BTCUSDT")))
  (is (= "btcusdt@bookTicker" (sut/book-ticker "BTCUSDT")))
  (is (= "btcusdt@depth5" (sut/partial-depth "BTCUSDT" 5)))
  (is (= "btcusdt@depth20@100ms" (sut/partial-depth "BTCUSDT" 20 100))))
