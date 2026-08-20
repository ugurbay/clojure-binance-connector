(ns binance-clj.websocket-testnet-test
  (:require [binance-clj.client :as client]
            [binance-clj.spot :as spot]
            [binance-clj.spot.streams :as streams]
            [binance-clj.spot.user-stream :as user-stream]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- enabled?
  [name]
  (= "true" (some-> (System/getenv name) str/lower-case)))

(defn- required-env
  [name]
  (let [value (System/getenv name)]
    (when (str/blank? value)
      (throw (ex-info (str name " is required for signed WebSocket acceptance.")
                      {:environment-variable name})))
    value))

(defn- await-event
  [poll! predicate timeout-ms]
  (let [deadline (+ (System/nanoTime) (* timeout-ms 1000000))]
    (loop []
      (when (< (System/nanoTime) deadline)
        (let [event (poll! 1000)]
          (if (and event (predicate event))
            event
            (recur)))))))

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

(defn- free-balance
  [account asset]
  (or (some (fn [balance]
              (when (= asset (:asset balance)) (:free balance)))
            (:balances account))
      0M))

(defn- await-order-and-account-events
  [stream client-order-id timeout-ms]
  (let [deadline (+ (System/nanoTime) (* timeout-ms 1000000))]
    (loop [execution nil
           account nil]
      (if (or (and execution account) (>= (System/nanoTime) deadline))
        {:account account :execution execution}
        (let [event (user-stream/poll-event! stream 1000)]
          (recur (if (and (= "executionReport" (:event-type event))
                          (= client-order-id (:client-order-id event))
                          (= "FILLED" (:order-status event)))
                   event
                   execution)
                 (if (= "outboundAccountPosition" (:event-type event))
                   event
                   account)))))))

(defn- await-generation
  [stream generation timeout-ms]
  (let [deadline (+ (System/nanoTime) (* timeout-ms 1000000))]
    (loop []
      (let [current (:generation (streams/snapshot stream))]
        (cond
          (> current generation) current
          (>= (System/nanoTime) deadline) nil
          :else (do (Thread/sleep 50) (recur)))))))

(deftest public-market-websocket-testnet-contract-test
  (if-not (enabled? "BINANCE_RUN_PUBLIC_WEBSOCKET_TESTNET")
    (is true "Skipped; set BINANCE_RUN_PUBLIC_WEBSOCKET_TESTNET=true to opt in.")
    (testing "public Testnet market stream receives an exact-decimal event"
      (let [connector (client/create-client {:environment :testnet})
            stream (streams/create-stream connector)]
        (try
          (streams/subscribe! stream (streams/book-ticker "BTCUSDT"))
          (streams/connect! stream)
          (let [event (await-event #(streams/poll-event! stream %)
                                   #(and (= "BTCUSDT" (:s %))
                                         (= :market-event (:kind %)))
                                   15000)]
            (is (map? event))
            (is (decimal? (:b event)))
            (is (decimal? (:a event))))
          (finally
            (streams/close! stream)
            (client/close! connector)))))))

(deftest public-market-websocket-testnet-restore-test
  (if-not (enabled? "BINANCE_RUN_WEBSOCKET_RESTORE_TESTNET")
    (is true "Skipped; set BINANCE_RUN_WEBSOCKET_RESTORE_TESTNET=true to opt in.")
    (testing "planned renewal reconnects and restores a live market subscription"
      (let [connector (client/create-client {:environment :testnet})
            stream (streams/create-stream connector)]
        (try
          (streams/subscribe! stream (streams/book-ticker "BTCUSDT"))
          (streams/connect! stream)
          (is (map? (await-event #(streams/poll-event! stream %)
                                 #(= "BTCUSDT" (:s %))
                                 15000)))
          (let [generation (:generation (streams/snapshot stream))]
            (is (true? (streams/renew! stream)))
            (is (some? (await-generation stream generation 15000)))
            (is (map? (await-event #(streams/poll-event! stream %)
                                   #(and (= "BTCUSDT" (:s %))
                                         (= :market-event (:kind %)))
                                   15000))))
          (finally
            (streams/close! stream)
            (client/close! connector)))))))

(deftest public-aggregate-trade-production-contract-test
  (if-not (enabled? "BINANCE_RUN_PUBLIC_AGGTRADE_PRODUCTION")
    (is true "Skipped; set BINANCE_RUN_PUBLIC_AGGTRADE_PRODUCTION=true to opt in.")
    (testing "credential-free production aggTrade survives normalization and restore"
      (let [connector (client/create-client {:environment :production})
            stream (streams/create-stream connector)
            matches? #(and (= "aggTrade" (:event-type %))
                           (= "BTCUSDT" (:symbol %)))]
        (try
          (streams/subscribe! stream (streams/aggregate-trades "BTCUSDT"))
          (streams/connect! stream)
          (let [event (await-event #(streams/poll-event! stream %) matches? 15000)]
            (is (map? event))
            (is (decimal? (:price event)))
            (is (decimal? (:quantity event)))
            (is (integer? (:aggregate-trade-id event)))
            (is (<= (:first-trade-id event) (:last-trade-id event)))
            (is (boolean? (:buyer-market-maker? event))))
          (let [generation (:generation (streams/snapshot stream))]
            (is (true? (streams/renew! stream)))
            (is (some? (await-generation stream generation 15000)))
            (is (map? (await-event #(streams/poll-event! stream %) matches? 15000))))
          (finally
            (streams/close! stream)
            (client/close! connector)))))))

(deftest signed-user-stream-testnet-subscription-test
  (if-not (enabled? "BINANCE_RUN_SIGNED_WEBSOCKET_TESTNET")
    (is true "Skipped; set BINANCE_RUN_SIGNED_WEBSOCKET_TESTNET=true to opt in.")
    (testing "signed WebSocket API subscription is accepted without legacy listen key"
      (let [connector
            (client/create-client
             {:environment :testnet
              :credentials {:api-key (required-env "BINANCE_API_KEY")
                            :api-secret (required-env "BINANCE_API_SECRET")}})
            stream (user-stream/create-stream connector)]
        (try
          (client/synchronize-time! connector)
          (user-stream/connect! stream)
          (let [response (await-event #(user-stream/poll-event! stream %)
                                      #(and (= :response (:kind %))
                                            (= 200 (:status %)))
                                      15000)]
            (is (= 200 (:status response)))
            (is (integer? (get-in response [:result :subscriptionId]))))
          (finally
            (user-stream/close! stream)
            (client/close! connector)))))))

(deftest signed-user-stream-testnet-order-events-test
  (if-not (enabled? "BINANCE_RUN_USER_STREAM_EVENTS_TESTNET")
    (is true "Skipped; set BINANCE_RUN_USER_STREAM_EVENTS_TESTNET=true to opt in.")
    (testing "explicit opt-in executes one small Testnet MARKET order and observes UDS events"
      (let [connector
            (client/create-client
             {:environment :testnet
              :credentials {:api-key (required-env "BINANCE_API_KEY")
                            :api-secret (required-env "BINANCE_API_SECRET")}})
            stream (user-stream/create-stream connector)
            symbol "BTCUSDT"]
        (try
          (client/synchronize-time! connector)
          (let [symbol-info (get-in (spot/exchange-info
                                     connector
                                     {:symbol symbol})
                                    [:data :symbols 0])
                account (get-in (spot/account
                                 connector
                                 {:omit-zero-balances? true})
                                [:data])
                quote-asset (:quoteAsset symbol-info)
                quote-quantity (+ 1M (max 10M (market-min-notional symbol-info)))
                available (free-balance account quote-asset)
                client-order-id (client/next-client-order-id connector)]
            (when-not (true? (:quoteOrderQtyMarketAllowed symbol-info))
              (throw (ex-info "BTCUSDT does not allow quoteOrderQty on this Testnet snapshot."
                              {:symbol symbol})))
            (when (neg? (.compareTo available quote-quantity))
              (throw (ex-info "Testnet quote balance is insufficient for event acceptance."
                              {:asset quote-asset})))

            (user-stream/connect! stream)
            (is (= 200
                   (:status
                    (await-event #(user-stream/poll-event! stream %)
                                 #(and (= :response (:kind %))
                                       (= 200 (:status %)))
                                 15000))))

            (let [lifecycle
                  (spot/submit-order!
                   connector
                   symbol-info
                   {:new-client-order-id client-order-id
                    :quote-order-qty quote-quantity
                    :side :buy
                    :symbol symbol
                    :type :market})
                  events (await-order-and-account-events stream client-order-id 20000)
                  execution (:execution events)
                  account-event (:account events)
                  observed
                  (user-stream/reconcile-order
                   {:client-order-id client-order-id
                    :events []
                    :state :unresolved
                    :symbol symbol}
                   execution)]
              (is (contains? #{:confirmed :reconciled} (:state lifecycle)))
              (is (= "FILLED" (:order-status execution)))
              (is (decimal? (:q execution)))
              (is (decimal? (:z execution)))
              (is (= "outboundAccountPosition" (:event-type account-event)))
              (is (every? decimal?
                          (mapcat (juxt :f :l) (:B account-event))))
              (is (= :reconciled (:state observed)))
              (is (= :observed (:resolution observed)))))
          (finally
            (user-stream/close! stream)
            (client/close! connector)))))))
