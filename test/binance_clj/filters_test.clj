(ns binance-clj.filters-test
  (:require [binance-clj.errors :as errors]
            [binance-clj.filters :as sut]
            [clojure.test :refer [deftest is testing]])
  (:import [java.math BigDecimal]))

(def symbol-info
  "Normalized exchangeInfo fixture covering every V1 order filter."
  {:symbol "BTCUSDT"
   :status "TRADING"
   :isSpotTradingAllowed true
   :quoteOrderQtyMarketAllowed true
   :orderTypes ["LIMIT" "MARKET"]
   :filters [{:filterType "PRICE_FILTER"
              :minPrice 10.0M
              :maxPrice 100000.0M
              :tickSize 0.1M}
             {:filterType "LOT_SIZE"
              :minQty 0.001M
              :maxQty 10.0M
              :stepSize 0.001M}
             {:filterType "MARKET_LOT_SIZE"
              :minQty 0.002M
              :maxQty 5.0M
              :stepSize 0.002M}
             {:filterType "MIN_NOTIONAL"
              :minNotional 10.0M
              :applyToMarket true
              :avgPriceMins 5}
             {:filterType "NOTIONAL"
              :minNotional 5.0M
              :maxNotional 100000.0M
              :applyMinToMarket true
              :applyMaxToMarket true
              :avgPriceMins 5}]})

(def valid-limit-order
  "Small valid LIMIT order aligned to the fixture filters."
  {:symbol "BTCUSDT"
   :side :buy
   :type :limit
   :time-in-force :gtc
   :quantity 0.001M
   :price 10000.0M})

(defn- captured-error
  [operation]
  (try
    (operation)
    nil
    (catch clojure.lang.ExceptionInfo throwable throwable)))

(deftest valid-limit-order-is-normalized-without-rounding-test
  (let [result (sut/validate-order symbol-info valid-limit-order)]
    (is (= "BUY" (:side result)))
    (is (= "LIMIT" (:type result)))
    (is (= "GTC" (:time-in-force result)))
    (is (= 0.001M (:quantity result)))
    (is (= 10000.0M (:price result)))
    (is (instance? BigDecimal (:quantity result)))
    (is (= valid-limit-order
           {:symbol (:symbol result)
            :side :buy
            :type :limit
            :time-in-force :gtc
            :quantity (:quantity result)
            :price (:price result)}))))

(deftest price-and-lot-filter-boundaries-test
  (doseq [[field value expected-filter expected-rule]
          [[:price 9.9M "PRICE_FILTER" :minPrice]
           [:price 100000.1M "PRICE_FILTER" :maxPrice]
           [:price 10000.05M "PRICE_FILTER" :tickSize]
           [:quantity 0.0001M "LOT_SIZE" :minQty]
           [:quantity 10.001M "LOT_SIZE" :maxQty]
           [:quantity 0.0015M "LOT_SIZE" :stepSize]]]
    (testing (str expected-filter " " expected-rule)
      (let [error (captured-error
                   #(sut/validate-order symbol-info
                                        (assoc valid-limit-order field value)))]
        (is (= :validation (errors/error-category error)))
        (is (= expected-filter (:filter-type (ex-data error))))
        (is (= expected-rule (:rule (ex-data error))))))))

(deftest enabled-filter-boundaries-are-inclusive-test
  (doseq [order [(assoc valid-limit-order :price 10M :quantity 1M)
                 (assoc valid-limit-order :price 100000M :quantity 0.001M)
                 (assoc valid-limit-order :price 10M :quantity 10M)
                 (assoc valid-limit-order :price 10000M :quantity 10M)]]
    (is (= "LIMIT" (:type (sut/validate-order symbol-info order))))))

(deftest market-lot-and-reference-notional-test
  (testing "MARKET quantity is checked against both lot filters and reference notional"
    (let [valid {:symbol "BTCUSDT" :side :sell :type :market :quantity 0.002M}
          result (sut/validate-order symbol-info valid {:reference-price 5000M})]
      (is (= "MARKET" (:type result)))
      (is (= 0.002M (:quantity result))))
    (doseq [[quantity rule]
            [[0.001M :minQty]
             [0.003M :stepSize]
             [5.002M :maxQty]]]
      (let [error (captured-error
                   #(sut/validate-order
                     symbol-info
                     {:symbol "BTCUSDT" :side :buy :type :market :quantity quantity}
                     {:reference-price 10000M}))]
        (is (= "MARKET_LOT_SIZE" (:filter-type (ex-data error))))
        (is (= rule (:rule (ex-data error))))))
    (let [error (captured-error
                 #(sut/validate-order
                   symbol-info
                   {:symbol "BTCUSDT" :side :buy :type :market :quantity 0.002M}))]
      (is (= :reference-price (:field (ex-data error))))
      (is (= :market-notional-reference-price (:rule (ex-data error)))))
    (testing "quoteOrderQty is the local MARKET notional estimate"
      (is (= 10M
             (:quote-order-qty
              (sut/validate-order symbol-info
                                  {:symbol "BTCUSDT"
                                   :side :buy
                                   :type :market
                                   :quote-order-qty 10M})))))
    (testing "market reference is not demanded when both notional filters opt out"
      (let [without-market-notional
            (update symbol-info
                    :filters
                    (fn [items]
                      (mapv #(cond-> %
                               (= "MIN_NOTIONAL" (:filterType %))
                               (assoc :applyToMarket false)
                               (= "NOTIONAL" (:filterType %))
                               (assoc :applyMinToMarket false :applyMaxToMarket false))
                            items)))]
        (is (= 0.002M
               (:quantity
                (sut/validate-order without-market-notional
                                    {:symbol "BTCUSDT"
                                     :side :buy
                                     :type :market
                                     :quantity 0.002M}))))))))

(deftest notional-boundaries-test
  (let [minimum-error (captured-error
                       #(sut/validate-order symbol-info
                                            (assoc valid-limit-order
                                                   :price 100M
                                                   :quantity 0.001M)))
        maximum-error (captured-error
                       #(sut/validate-order symbol-info
                                            (assoc valid-limit-order
                                                   :price 100000M
                                                   :quantity 10M)))]
    (is (= "MIN_NOTIONAL" (:filter-type (ex-data minimum-error))))
    (is (= :minNotional (:rule (ex-data minimum-error))))
    (is (= "NOTIONAL" (:filter-type (ex-data maximum-error))))
    (is (= :maxNotional (:rule (ex-data maximum-error))))))

(deftest order-shape-and-symbol-contract-test
  (doseq [order [(dissoc valid-limit-order :quantity)
                 (assoc valid-limit-order :type :market)
                 {:symbol "BTCUSDT" :side :buy :type :market}
                 {:symbol "BTCUSDT" :side :buy :type :market
                  :quantity 1M :quote-order-qty 10M}
                 (assoc valid-limit-order :symbol "ETHUSDT")
                 (assoc valid-limit-order :quote-order-qty 10M)
                 (assoc valid-limit-order :type :stop-loss)]]
    (is (= :validation
           (errors/error-category
            (captured-error #(sut/validate-order symbol-info order)))))))

(deftest quote-order-quantity-symbol-capability-test
  (let [error (captured-error
               #(sut/validate-order
                 (assoc symbol-info :quoteOrderQtyMarketAllowed false)
                 {:symbol "BTCUSDT" :side :buy :type :market
                  :quote-order-qty 10M}))]
    (is (= :quoteOrderQtyMarketAllowed (:rule (ex-data error))))))

(deftest raw-and-malformed-filter-contract-test
  (testing "raw exchangeInfo decimal strings are parsed exactly"
    (let [raw-info (update symbol-info :filters
                           (fn [filters]
                             (mapv (fn [filter-map]
                                     (reduce-kv
                                      (fn [result key value]
                                        (assoc result key
                                               (if (instance? BigDecimal value)
                                                 (.toPlainString ^BigDecimal value)
                                                 value)))
                                      {}
                                      filter-map))
                                   filters)))]
      (is (= "LIMIT" (:type (sut/validate-order raw-info valid-limit-order))))))
  (doseq [[filters expected-rule]
          [[(conj (:filters symbol-info) (first (:filters symbol-info))) nil]
           [(assoc-in (:filters symbol-info) [0 :tickSize] "1e-8") :tickSize]
           [(update-in (:filters symbol-info) [1] dissoc :stepSize) :stepSize]
           [(assoc-in (:filters symbol-info) [3 :applyToMarket] "true")
            :applyToMarket]
           [(conj (:filters symbol-info) {:minQty "1"}) :filterType]]]
    (let [error (captured-error
                 #(sut/validate-order (assoc symbol-info :filters filters)
                                      valid-limit-order))]
      (is (= :validation (errors/error-category error)))
      (when expected-rule
        (is (= expected-rule (:rule (ex-data error))))))))
