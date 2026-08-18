(ns binance-clj.public-testnet-test
  (:require [binance-clj.client :as client]
            [binance-clj.spot :as spot]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- enabled?
  []
  (= "true" (some-> (System/getenv "BINANCE_RUN_PUBLIC_TESTNET")
                    str/lower-case)))

(deftest public-testnet-contract-test
  (if-not (enabled?)
    (is true "Skipped; set BINANCE_RUN_PUBLIC_TESTNET=true to opt in.")
    (testing "public Spot Testnet endpoint lifecycle"
      (let [connector (client/create-client {:environment :testnet})]
        (try
          (is (:ok? (spot/ping connector)))
          (is (integer? (get-in (spot/server-time connector) [:data :serverTime])))
          (is (= "BTCUSDT"
                 (get-in (spot/exchange-info connector {:symbol "BTCUSDT"})
                         [:data :symbols 0 :symbol])))
          (is (decimal? (get-in (spot/ticker-price connector "BTCUSDT")
                                [:data :price])))
          (is (decimal? (get-in (spot/ticker-24h connector "BTCUSDT")
                                [:data :lastPrice])))
          (is (decimal? (get-in (spot/book-ticker connector "BTCUSDT")
                                [:data :bidPrice])))
          (is (seq (get-in (spot/depth connector "BTCUSDT" {:limit 5})
                           [:data :bids])))
          (finally
            (client/close! connector)))))))
