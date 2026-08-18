(ns binance-clj.signed-testnet-test
  (:require [binance-clj.client :as client]
            [binance-clj.spot :as spot]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- enabled?
  []
  (= "true" (some-> (System/getenv "BINANCE_RUN_SIGNED_TESTNET")
                    str/lower-case)))

(defn- required-env
  [name]
  (let [value (System/getenv name)]
    (when (str/blank? value)
      (throw (ex-info (str name " is required for signed Testnet acceptance.")
                      {:environment-variable name})))
    value))

(defn- market-min-notional
  [symbol-info]
  (reduce (fn [minimum filter-map]
            (case (:filterType filter-map)
              "MIN_NOTIONAL" (if (true? (:applyToMarket filter-map))
                               (max minimum (:minNotional filter-map))
                               minimum)
              "NOTIONAL" (if (true? (:applyMinToMarket filter-map))
                           (max minimum (:minNotional filter-map))
                           minimum)
              minimum))
          0M
          (:filters symbol-info)))

(deftest signed-testnet-account-and-test-order-contract-test
  (if-not (enabled?)
    (is true "Skipped; set BINANCE_RUN_SIGNED_TESTNET=true to opt in.")
    (testing "signed account read and non-executing MARKET test order"
      (let [connector (client/create-client
                       {:environment :testnet
                        :credentials
                        {:api-key (required-env "BINANCE_API_KEY")
                         :api-secret (required-env "BINANCE_API_SECRET")}})]
        (try
          (let [time-sync (client/synchronize-time! connector)
                account-result (spot/account connector {:omit-zero-balances? true})
                symbol-info (get-in (spot/exchange-info
                                     connector
                                     {:symbol "BTCUSDT"})
                                    [:data :symbols 0])
                quote-quantity (+ 1M (market-min-notional symbol-info))
                test-result (spot/test-order
                             connector
                             symbol-info
                             {:symbol "BTCUSDT"
                              :side :buy
                              :type :market
                              :quote-order-qty quote-quantity})]
            (is (integer? (:offset-ms time-sync)))
            (is (:ok? account-result))
            (is (vector? (get-in account-result [:data :balances])))
            (is (:ok? test-result))
            (is (= {} (:data test-result))))
          (finally
            (client/close! connector)))))))
