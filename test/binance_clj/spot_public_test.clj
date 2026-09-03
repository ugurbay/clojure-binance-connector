(ns binance-clj.spot-public-test
  (:require [binance-clj.client :as client]
            [binance-clj.errors :as errors]
            [binance-clj.spot :as sut]
            [binance-clj.transport.http :as http]
            [clojure.test :refer [deftest is testing]])
  (:import [java.math BigDecimal]))

(defn- client-with-response
  [response captured]
  (client/create-client
   {:transport {:send! (fn [request]
                         (reset! captured request)
                         (if (fn? response) (response request) response))}}))

(defn- captured-error
  [operation]
  (try
    (operation)
    nil
    (catch clojure.lang.ExceptionInfo throwable throwable)))

(deftest general-endpoints-test
  (let [captured (atom nil)
        connector (client-with-response
                   (fn [request]
                     (case (:endpoint-id request)
                       :spot/ping {}
                       :spot/server-time {:serverTime 1499827319559
                                          :futureField "kept"}))
                   captured)]
    (try
      (is (= {} (:data (sut/ping connector))))
      (let [result (sut/server-time connector)]
        (is (= 1499827319559 (get-in result [:data :serverTime])))
        (is (= "kept" (get-in result [:data :futureField])))
        (is (= :spot/server-time (:endpoint-id @captured)))
        (is (= 1 (:weight @captured))))
      (finally (client/close! connector)))))

(deftest exchange-info-normalization-and-wire-params-test
  (let [captured (atom nil)
        response {:timezone "UTC"
                  :serverTime 1565246363776
                  :futureTopLevel {:kept true}
                  :exchangeFilters []
                  :symbols
                  [{:symbol "BTCUSDT"
                    :status "TRADING"
                    :futureSymbolField "kept"
                    :filters
                    [{:filterType "PRICE_FILTER"
                      :minPrice "0.01000000"
                      :maxPrice "1000000.00000000"
                      :tickSize "0.01000000"
                      :futureFilterField "kept"}
                     {:filterType "LOT_SIZE"
                      :minQty "0.00001000"
                      :maxQty "9000.00000000"
                      :stepSize "0.00001000"}]}]}
        connector (client-with-response response captured)
        params {:permissions [:SPOT :MARGIN]
                :show-permission-sets? false}
        result (sut/exchange-info connector params)
        price-filter (get-in result [:data :symbols 0 :filters 0])]
    (try
      (is (= params {:permissions [:SPOT :MARGIN]
                     :show-permission-sets? false}))
      (is (= "[\"SPOT\",\"MARGIN\"]" (get-in @captured [:params :permissions])))
      (is (false? (get-in @captured [:params :showPermissionSets])))
      (is (= 20 (:weight @captured)))
      (is (instance? BigDecimal (:minPrice price-filter)))
      (is (= 0.01000000M (:minPrice price-filter)))
      (is (= "kept" (:futureFilterField price-filter)))
      (is (= {:kept true} (get-in result [:data :futureTopLevel])))
      (finally (client/close! connector)))))

(deftest ticker-normalization-test
  (testing "ticker price supports a single object"
    (let [captured (atom nil)
          connector (client-with-response
                     {:symbol "BTCUSDT" :price "62123.45000000" :future "kept"}
                     captured)
          result (sut/ticker-price connector "BTCUSDT")]
      (try
        (is (= "BTCUSDT" (get-in @captured [:params :symbol])))
        (is (= 2 (:weight @captured)))
        (is (= 62123.45000000M (get-in result [:data :price])))
        (is (= "kept" (get-in result [:data :future])))
        (finally (client/close! connector)))))
  (testing "book ticker supports arrays and normalizes every financial field"
    (let [captured (atom nil)
          connector (client-with-response
                     [{:symbol "BTCUSDT"
                       :bidPrice "62120.10000000"
                       :bidQty "1.25000000"
                       :askPrice "62121.20000000"
                       :askQty "2.50000000"}]
                     captured)
          result (sut/book-ticker connector ["BTCUSDT" "ETHUSDT"])
          item (first (:data result))]
      (try
        (is (= "[\"BTCUSDT\",\"ETHUSDT\"]"
               (get-in @captured [:params :symbols])))
        (is (= 4 (:weight @captured)))
        (doseq [field [:bidPrice :bidQty :askPrice :askQty]]
          (is (instance? BigDecimal (get item field))))
        (finally (client/close! connector)))))
  (testing "24-hour FULL and MINI known decimals are lossless"
    (let [captured (atom nil)
          connector (client-with-response
                     {:symbol "BTCUSDT"
                      :priceChange "-100.01000000"
                      :priceChangePercent "-0.160"
                      :weightedAvgPrice "62500.12345678"
                      :lastPrice "62123.45000000"
                      :volume "1234.00000000"
                      :quoteVolume "77123456.12345678"
                      :openTime 1
                      :closeTime 2}
                     captured)
          result (sut/ticker-24h connector {:symbol "BTCUSDT" :type :mini})]
      (try
        (is (= "MINI" (get-in @captured [:params :type])))
        (is (= 2 (:weight @captured)))
        (doseq [field [:priceChange :priceChangePercent :weightedAvgPrice
                       :lastPrice :volume :quoteVolume]]
          (is (instance? BigDecimal (get-in result [:data field]))))
        (is (= 1 (get-in result [:data :openTime])))
        (finally (client/close! connector))))))

(deftest depth-normalization-and-weight-test
  (doseq [[limit expected-weight] [[1 5] [100 5] [101 25] [500 25]
                                   [501 50] [1000 50] [1001 250] [5000 250]]]
    (let [captured (atom nil)
          connector (client-with-response
                     {:lastUpdateId 1027024
                      :bids [["4.00000000" "431.00000000" "future"]]
                      :asks [["4.00000200" "12.00000000"]]
                      :future "kept"}
                     captured)
          result (sut/depth connector "BTCUSDT" {:limit limit})]
      (try
        (is (= expected-weight (:weight @captured)))
        (is (= limit (get-in @captured [:params :limit])))
        (is (instance? BigDecimal (get-in result [:data :bids 0 0])))
        (is (instance? BigDecimal (get-in result [:data :bids 0 1])))
        (is (= "future" (get-in result [:data :bids 0 2])))
        (is (= "kept" (get-in result [:data :future])))
        (finally (client/close! connector))))))

(deftest klines-normalization-and-wire-contract-test
  (let [captured (atom nil)
        response [[1502942428000
                   "4261.48000000" "4261.48000000" "4261.48000000"
                   "4261.48000000" "1.77518300" 1502942428999
                   "7564.90685100" 3 "0.00000000" "0.00000000"
                   "0" "future-field"]]
        connector (client-with-response response captured)
        result (sut/klines connector "BTCUSDT" "1d"
                           {:start-time 0 :limit 1})
        row (get-in result [:data 0])]
    (try
      (is (= :spot/klines (:endpoint-id @captured)))
      (is (= {:symbol "BTCUSDT" :interval "1d" :startTime 0 :limit 1}
             (:params @captured)))
      (is (= 2 (:weight @captured)))
      (doseq [index [1 2 3 4 5 7 9 10]]
        (is (instance? BigDecimal (nth row index))))
      (is (= 1502942428000 (nth row 0)))
      (is (= "future-field" (nth row 12)))
      (finally (client/close! connector)))))

(deftest dynamic-ticker-weight-test
  (doseq [[selection expected]
          [["BTCUSDT" 2]
           [(mapv #(str "S" %) (range 20)) 2]
           [(mapv #(str "S" %) (range 21)) 40]
           [(mapv #(str "S" %) (range 101)) 80]
           [nil 80]]]
    (let [captured (atom nil)
          connector (client-with-response [] captured)]
      (try
        (sut/ticker-24h connector selection)
        (is (= expected (:weight @captured)))
        (finally (client/close! connector))))))

(deftest json-array-query-is-percent-encoded-on-the-wire-test
  (let [captured (atom nil)
        connector (client-with-response [] captured)]
    (try
      (sut/ticker-price connector ["BTCUSDT" "ETHUSDT"])
      (let [request (http/build-http-request @captured)]
        (is (= (str "https://testnet.binance.vision/api/v3/ticker/price?"
                    "symbols=%5B%22BTCUSDT%22%2C%22ETHUSDT%22%5D")
               (str (.uri request)))))
      (finally (client/close! connector)))))

(deftest public-parameter-validation-test
  (let [captured (atom nil)
        connector (client-with-response {} captured)]
    (try
      (doseq [operation
              [#(client/execute! connector :spot/ping {:unexpected true})
               #(sut/exchange-info connector {:symbol "BTCUSDT"
                                              :permissions :SPOT})
               #(sut/exchange-info connector {:symbol-status :unknown})
               #(sut/exchange-info connector {:permissions []})
               #(sut/exchange-info connector {:show-permission-sets? "yes"})
               #(sut/ticker-price connector {:symbol "BTCUSDT"
                                             :symbols ["ETHUSDT"]})
               #(sut/ticker-price connector {:symbols []})
               #(sut/book-ticker connector {:symbol-status :unknown})
               #(sut/ticker-24h connector {:type :unknown})
               #(sut/depth connector "" {})
               #(sut/depth connector "BTCUSDT" "not-a-map")
               #(sut/depth connector "BTCUSDT" {:limit 0})
               #(sut/depth connector "BTCUSDT" {:limit 5001})
               #(sut/depth connector "BTCUSDT" {:unexpected true})
               #(sut/klines connector "BTCUSDT" "1D")
               #(sut/klines connector "BTCUSDT" "1d" {:limit 0})
               #(sut/klines connector "BTCUSDT" "1d" {:limit 1001})
               #(sut/klines connector "BTCUSDT" "1d" {:start-time -1})
               #(sut/klines connector "BTCUSDT" "1d"
                            {:start-time 20 :end-time 10})
               #(sut/klines connector "BTCUSDT" "1d" {:time-zone "15:00"})
               #(sut/klines connector "BTCUSDT" "1d" {:unexpected true})]]
        (is (= :validation (errors/error-category (captured-error operation)))))
      (is (nil? @captured))
      (finally (client/close! connector)))))

(deftest malformed-binance-public-response-test
  (doseq [[endpoint operation response]
          [[:spot/server-time sut/server-time {:serverTime "not-an-integer"}]
           [:spot/ticker-price #(sut/ticker-price % "BTCUSDT")
            {:symbol "BTCUSDT" :price "1e-8"}]
           [:spot/depth #(sut/depth % "BTCUSDT")
            {:lastUpdateId 1 :bids [["1.0"]] :asks []}]
           [:spot/klines #(sut/klines % "BTCUSDT" "1d")
            [[0 "1.0" "1.1" "0.9" "1.0" "2.0" 59999 "2.0" "three"
              "1.0" "1.0" "0"]]]
           [:spot/klines #(sut/klines % "BTCUSDT" "1d")
            [[0 "1.0"]]]
           [:spot/exchange-info sut/exchange-info {:symbols :not-an-array}]
           [:spot/exchange-info sut/exchange-info
            {:symbols [] :exchangeFilters :not-an-array}]]]
    (let [captured (atom nil)
          connector (client-with-response response captured)
          error (captured-error #(operation connector))]
      (try
        (is (= endpoint (:endpoint-id @captured)))
        (is (= :api (errors/error-category error)))
        (finally (client/close! connector))))))
