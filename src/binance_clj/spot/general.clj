(ns binance-clj.spot.general
  "Public facade for Binance Spot general endpoints."
  (:require [binance-clj.client :as client]))

(defn ping
  "Tests REST connectivity and returns the standard result envelope."
  [connector]
  (client/execute! connector :spot/ping))

(defn server-time
  "Returns Binance server time in the standard result envelope."
  [connector]
  (client/execute! connector :spot/server-time))

(defn exchange-info
  "Returns exchange rules and symbol metadata without dropping unknown fields."
  ([connector] (exchange-info connector {}))
  ([connector params]
   (client/execute! connector :spot/exchange-info params)))
