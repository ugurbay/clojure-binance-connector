(ns binance-clj.release-testnet-test
  (:require [binance-clj.client :as client]
            [binance-clj.decimal :as decimal]
            [binance-clj.encoding :as encoding]
            [binance-clj.spot :as spot]
            [binance-clj.spot.user-stream :as user-stream]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import [java.math BigDecimal RoundingMode]))

(defn- enabled?
  []
  (= "true" (some-> (System/getenv "BINANCE_RUN_RELEASE_TESTNET")
                    str/lower-case)))

(defn- required-env
  [name]
  (let [value (System/getenv name)]
    (when (str/blank? value)
      (throw (ex-info (str name " is required for the release Testnet gate.")
                      {:environment-variable name})))
    value))

(defn- filter-by-type
  [symbol-info type]
  (some #(when (= type (:filterType %)) %) (:filters symbol-info)))

(defn- positive-decimal?
  [value]
  (and (instance? BigDecimal value) (pos? (.signum ^BigDecimal value))))

(defn- below?
  [^BigDecimal value ^BigDecimal limit]
  (neg? (.compareTo value limit)))

(defn- above?
  [^BigDecimal value ^BigDecimal limit]
  (pos? (.compareTo value limit)))

(defn- floor-to
  [^BigDecimal value ^BigDecimal increment]
  (decimal/normalize
   (.multiply (.divideToIntegralValue value increment) increment)))

(defn- ceil-to
  [^BigDecimal value ^BigDecimal increment]
  (let [floor (floor-to value increment)]
    (if (zero? (.compareTo floor value))
      floor
      (decimal/normalize (.add floor increment)))))

(defn- limit-min-notional
  [symbol-info]
  (reduce (fn [minimum filter-map]
            (case (:filterType filter-map)
              "MIN_NOTIONAL" (max minimum (:minNotional filter-map))
              "NOTIONAL" (max minimum (:minNotional filter-map))
              minimum))
          0M
          (:filters symbol-info)))

(defn- limit-max-notional
  [symbol-info]
  (let [maximum (some-> (filter-by-type symbol-info "NOTIONAL") :maxNotional)]
    (when (positive-decimal? maximum) maximum)))

(defn- aligned-price
  [side reference price-filter]
  (let [tick (:tickSize price-filter)
        raw (.multiply ^BigDecimal reference
                       (if (= :sell side) 1.02M 0.98M))
        aligned (if (= :sell side) (ceil-to raw tick) (floor-to raw tick))
        minimum (:minPrice price-filter)
        maximum (:maxPrice price-filter)
        above-min (if (and (positive-decimal? minimum) (below? aligned minimum))
                    (ceil-to minimum tick)
                    aligned)]
    (if (and (positive-decimal? maximum) (above? above-min maximum))
      (floor-to maximum tick)
      above-min)))

(defn- order-candidate
  [side symbol-info book]
  (let [price-filter (filter-by-type symbol-info "PRICE_FILTER")
        lot-filter (filter-by-type symbol-info "LOT_SIZE")
        reference (if (= :sell side) (:askPrice book) (:bidPrice book))
        price (aligned-price side reference price-filter)
        step (:stepSize lot-filter)
        target-notional (+ 1M (max 10M (limit-min-notional symbol-info)))
        raw-quantity (.divide ^BigDecimal target-notional
                              ^BigDecimal price
                              24
                              RoundingMode/UP)
        quantity (max (:minQty lot-filter) (ceil-to raw-quantity step))
        notional (.multiply ^BigDecimal price ^BigDecimal quantity)
        maximum-notional (limit-max-notional symbol-info)
        marketable? (if (= :sell side)
                      (not (pos? (.compareTo ^BigDecimal price
                                             ^BigDecimal (:askPrice book))))
                      (not (neg? (.compareTo ^BigDecimal price
                                             ^BigDecimal (:bidPrice book)))))]
    (when (or marketable?
              (and (positive-decimal? (:maxQty lot-filter))
                   (above? quantity (:maxQty lot-filter)))
              (and maximum-notional (above? notional maximum-notional)))
      (throw (ex-info "A safe non-marketable LIMIT candidate could not be built."
                      {:side side :symbol (:symbol symbol-info)})))
    {:notional notional
     :order {:price price
             :quantity quantity
             :side side
             :symbol (:symbol symbol-info)
             :time-in-force :gtc
             :type :limit}}))

(defn- free-balance
  [account asset]
  (or (some (fn [balance]
              (when (= asset (:asset balance)) (:free balance)))
            (:balances account))
      0M))

(defn- funded-order
  [symbol-info book account]
  (let [sell (order-candidate :sell symbol-info book)
        buy (order-candidate :buy symbol-info book)
        base-free (free-balance account (:baseAsset symbol-info))
        quote-free (free-balance account (:quoteAsset symbol-info))]
    (cond
      (not (below? base-free (get-in sell [:order :quantity]))) (:order sell)
      (not (below? quote-free (:notional buy))) (:order buy)
      :else (throw (ex-info "Testnet balances cannot fund the safe LIMIT acceptance order."
                            {:symbol (:symbol symbol-info)})))))

(defn- await-event
  [stream predicate timeout-ms]
  (let [deadline (+ (System/nanoTime) (* timeout-ms 1000000))]
    (loop []
      (when (< (System/nanoTime) deadline)
        (let [event (user-stream/poll-event! stream 1000)]
          (if (and event (predicate event)) event (recur)))))))

(defn- matching-execution?
  [client-order-id status event]
  (and (= "executionReport" (:event-type event))
       (= status (:order-status event))
       (or (= client-order-id (:client-order-id event))
           (= client-order-id (:original-client-order-id event)))))

(defn- order-data
  [lifecycle]
  (or (get-in lifecycle [:result :data]) (:order lifecycle)))

(defn- ensure-submission-observed!
  [lifecycle]
  (when-not (contains? #{:confirmed :reconciled} (:state lifecycle))
    (throw (ex-info "Testnet acceptance order was not accepted."
                    {:error (:error lifecycle)
                     :lifecycle-state (:state lifecycle)
                     :resolution (:resolution lifecycle)})))
  lifecycle)

(def ^:private observation-policy
  {:delay-ms 250
   :max-attempts 20})

(defn- order-not-found?
  [throwable]
  (= :order-not-found (:binance-reason (ex-data throwable))))

(defn- await-observation
  ([read! accepted?]
   (await-observation read! accepted? observation-policy))
  ([read! accepted? {:keys [delay-ms max-attempts sleep-fn]
                     :or {sleep-fn #(Thread/sleep (long %))}}]
   (loop [attempt 1]
     (let [outcome (try
                     {:value (read!)}
                     (catch Throwable throwable
                       {:error throwable}))
           error (:error outcome)]
       (cond
         (and (contains? outcome :value) (accepted? (:value outcome)))
         (:value outcome)

         (and error (not (order-not-found? error)))
         (throw error)

         (>= attempt max-attempts)
         (throw (ex-info "Timed out while awaiting a consistent Testnet read."
                         {:attempts attempt
                          :binance-reason (some-> error ex-data :binance-reason)}
                         error))

         :else
         (do
           (sleep-fn delay-ms)
           (recur (inc attempt))))))))

(defn- await-order-status
  [connector query status]
  (await-observation
   #(-> (spot/query-order connector query) :data)
   #(= status (:status %))))

(defn- await-open-order-presence
  [connector symbol order-id present?]
  (await-observation
   #(-> (spot/open-orders connector symbol) :data)
   (fn [orders]
     (= present? (boolean (some #(= order-id (:orderId %)) orders))))))

(deftest release-limit-candidate-contract-test
  (let [symbol-info {:baseAsset "BTC"
                     :filters [{:filterType "PRICE_FILTER"
                                :maxPrice 1000000M
                                :minPrice 0.01M
                                :tickSize 0.01M}
                               {:filterType "LOT_SIZE"
                                :maxQty 100M
                                :minQty 0.00001M
                                :stepSize 0.00001M}
                               {:applyToMarket true
                                :filterType "MIN_NOTIONAL"
                                :minNotional 10M}]
                     :quoteAsset "USDT"
                     :symbol "BTCUSDT"}
        book {:askPrice 101M :bidPrice 100M}
        sell-account {:balances [{:asset "BTC" :free 1M}
                                 {:asset "USDT" :free 1000M}]}
        buy-account {:balances [{:asset "BTC" :free 0M}
                                {:asset "USDT" :free 1000M}]}
        sell (funded-order symbol-info book sell-account)
        buy (funded-order symbol-info book buy-account)
        live-scale-candidate
        (:order (order-candidate :sell
                                 symbol-info
                                 {:askPrice 64734.35000000M
                                  :bidPrice 64734.34000000M}))]
    (is (= :sell (:side sell)))
    (is (pos? (.compareTo ^BigDecimal (:price sell) 101M)))
    (is (zero? (.signum (.remainder ^BigDecimal (:price sell) 0.01M))))
    (is (zero? (.signum (.remainder ^BigDecimal (:quantity sell) 0.00001M))))
    (is (= :buy (:side buy)))
    (is (neg? (.compareTo ^BigDecimal (:price buy) 100M)))
    (is (= "price=66029.04&quantity=0.00017"
           (encoding/canonical-query
            (select-keys live-scale-candidate [:price :quantity]))))))

(deftest bounded-testnet-observation-contract-test
  (testing "order-not-found and stale safe reads converge without repeating a command"
    (let [attempts (atom 0)
          sleeps (atom [])
          observed (await-observation
                    (fn []
                      (case (swap! attempts inc)
                        1 (throw (ex-info "not visible"
                                          {:binance-reason :order-not-found}))
                        2 {:status "NEW"}
                        {:status "CANCELED"}))
                    #(= "CANCELED" (:status %))
                    {:delay-ms 7
                     :max-attempts 3
                     :sleep-fn #(swap! sleeps conj %)})]
      (is (= {:status "CANCELED"} observed))
      (is (= 3 @attempts))
      (is (= [7 7] @sleeps))))

  (testing "unexpected API errors are never hidden or polled"
    (let [attempts (atom 0)
          failure (ex-info "rejected" {:binance-reason :invalid-api-key})]
      (is (identical?
           failure
           (try
             (await-observation
              (fn []
                (swap! attempts inc)
                (throw failure))
              some?
              {:delay-ms 0 :max-attempts 3 :sleep-fn (fn [_])})
             (catch Throwable throwable throwable))))
      (is (= 1 @attempts)))))

(deftest complete-testnet-limit-lifecycle-test
  (if-not (enabled?)
    (is true "Skipped; set BINANCE_RUN_RELEASE_TESTNET=true to opt in.")
    (testing "one non-marketable Testnet LIMIT order is created, observed, and canceled"
      (let [connector
            (client/create-client
             {:environment :testnet
              :credentials {:api-key (required-env "BINANCE_API_KEY")
                            :api-secret (required-env "BINANCE_API_SECRET")}})
            stream (user-stream/create-stream connector)
            symbol "BTCUSDT"
            client-order-id (client/next-client-order-id connector)
            command-attempted? (atom false)
            cancel-attempted? (atom false)
            submitted-order-id (atom nil)]
        (try
          (client/synchronize-time! connector)
          (let [symbol-info (get-in (spot/exchange-info connector {:symbol symbol})
                                    [:data :symbols 0])
                book (:data (spot/book-ticker connector symbol))
                account (:data (spot/account connector {:omit-zero-balances? true}))
                order (assoc (funded-order symbol-info book account)
                             :new-client-order-id client-order-id)
                preflight (spot/test-order connector
                                           symbol-info
                                           (dissoc order :new-client-order-id))]
            (is (:ok? preflight))
            (is (= {} (:data preflight)))
            (user-stream/connect! stream)
            (is (= 200
                   (:status
                    (await-event stream
                                 #(and (= :response (:kind %))
                                       (= 200 (:status %)))
                                 15000))))

            (reset! command-attempted? true)
            (let [lifecycle (ensure-submission-observed!
                             (spot/submit-order! connector symbol-info order))
                  placed (order-data lifecycle)
                  order-id (:orderId placed)
                  _ (when (integer? order-id)
                      (reset! submitted-order-id order-id))]
              (is (contains? #{:confirmed :reconciled} (:state lifecycle)))
              (is (integer? order-id))

              (let [new-event (await-event stream
                                           #(matching-execution?
                                             client-order-id "NEW" %)
                                           15000)
                    queried (await-order-status
                             connector
                             {:original-client-order-id client-order-id
                              :symbol symbol}
                             "NEW")
                    open-orders (await-open-order-presence
                                 connector symbol order-id true)]
                (is (= "NEW" (:order-status new-event)))
                (is (= "NEW" (:status queried)))
                (is (some #(= client-order-id (:clientOrderId %)) open-orders)))

              (reset! cancel-attempted? true)
              (let [cancel-result (:data (spot/cancel-order
                                          connector
                                          {:order-id order-id :symbol symbol}))
                    canceled-event (await-event stream
                                                #(matching-execution?
                                                  client-order-id "CANCELED" %)
                                                15000)
                    final-order (await-order-status
                                 connector
                                 {:order-id order-id :symbol symbol}
                                 "CANCELED")
                    final-open-orders (await-open-order-presence
                                       connector symbol order-id false)]
                (is (= "CANCELED" (:status cancel-result)))
                (is (= "CANCELED" (:order-status canceled-event)))
                (is (= "CANCELED" (:status final-order)))
                (is (not-any? #(= order-id (:orderId %)) final-open-orders)))))
          (finally
            (when (and @command-attempted? (not @cancel-attempted?))
              (try
                (let [known-order-id @submitted-order-id
                      order (when-not known-order-id
                              (await-observation
                               #(-> (spot/query-order
                                     connector
                                     {:original-client-order-id client-order-id
                                      :symbol symbol})
                                    :data)
                               some?))
                      cleanup-order-id (or known-order-id (:orderId order))]
                  (when (and cleanup-order-id
                             (or known-order-id
                                 (contains? #{"NEW" "PARTIALLY_FILLED"}
                                            (:status order))))
                    (reset! cancel-attempted? true)
                    (spot/cancel-order connector
                                       {:order-id cleanup-order-id :symbol symbol})))
                (catch Throwable _)))
            (user-stream/close! stream)
            (client/close! connector)))))))
