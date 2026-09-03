(ns binance-clj.spot.market
  "Public facade for Binance Spot market-data endpoints."
  (:require [binance-clj.client :as client]))

(defn- selection-params
  [selection]
  (cond
    (nil? selection) {}
    (map? selection) selection
    (string? selection) {:symbol selection}
    (sequential? selection) {:symbols selection}
    :else selection))

(defn ticker-price
  "Returns latest price for one, many, or all symbols."
  ([connector] (ticker-price connector nil))
  ([connector selection]
   (client/execute! connector :spot/ticker-price (selection-params selection))))

(defn ticker-24h
  "Returns FULL/MINI 24-hour ticker data for one, many, or all symbols."
  ([connector] (ticker-24h connector nil))
  ([connector selection]
   (client/execute! connector :spot/ticker-24h (selection-params selection))))

(defn book-ticker
  "Returns best bid/ask for one, many, or all symbols."
  ([connector] (book-ticker connector nil))
  ([connector selection]
   (client/execute! connector :spot/book-ticker (selection-params selection))))

(defn depth
  "Returns an order-book snapshot for symbol with optional limit/status params."
  ([connector symbol] (depth connector symbol {}))
  ([connector symbol options]
   (client/execute! connector
                    :spot/depth
                    (if (map? options)
                      (assoc options :symbol symbol)
                      options))))

(defn klines
  "Returns public Spot candlesticks for one symbol and case-sensitive interval."
  ([connector symbol interval] (klines connector symbol interval {}))
  ([connector symbol interval options]
   (client/execute! connector
                    :spot/klines
                    (if (map? options)
                      (assoc options :symbol symbol :interval interval)
                      options))))
