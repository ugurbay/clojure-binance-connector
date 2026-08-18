(ns binance-clj.spot.account
  "Public facade for authenticated Spot account and order-query endpoints."
  (:require [binance-clj.client :as client]))

(defn account
  "Returns account information and BigDecimal-normalized balances."
  ([connector] (account connector {}))
  ([connector options]
   (client/execute! connector :spot/account options)))

(defn my-trades
  "Returns account trades for a symbol or an explicit query map."
  [connector symbol-or-params]
  (client/execute! connector
                   :spot/my-trades
                   (if (map? symbol-or-params)
                     symbol-or-params
                     {:symbol symbol-or-params})))

(defn query-order
  "Returns one order selected by order-id and/or original-client-order-id."
  [connector params]
  (client/execute! connector :spot/query-order params))

(defn open-orders
  "Returns open orders for one symbol or, when omitted, all symbols."
  ([connector] (open-orders connector nil))
  ([connector symbol]
   (client/execute! connector
                    :spot/open-orders
                    (if (nil? symbol) {} {:symbol symbol}))))
