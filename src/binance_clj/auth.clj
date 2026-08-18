(ns binance-clj.auth
  "Binance REST and WebSocket API signing orchestration."
  (:require [binance-clj.auth.signer :as signer]
            [binance-clj.encoding :as encoding]
            [binance-clj.errors :as errors]))

(defn- ensure-signer!
  [value]
  (when-not (signer/signer? value)
    (throw (errors/connector-error :configuration
                                   "Signer must implement the Signer protocol."
                                   {}))))

(defn sign-rest
  "Returns the exact REST payload, signature, and signed query string."
  [selected-signer parameters]
  (ensure-signer! selected-signer)
  (let [payload (encoding/canonical-query parameters)
        signature (signer/sign selected-signer payload)]
    {:algorithm (signer/algorithm selected-signer)
     :payload payload
     :signature signature
     :signed-query (str payload (when-not (empty? payload) "&")
                        "signature=" (encoding/percent-encode signature))}))

(defn sign-websocket
  "Returns the canonical WebSocket API payload and its signature."
  [selected-signer parameters]
  (ensure-signer! selected-signer)
  (let [payload (encoding/canonical-ws-payload parameters)]
    {:algorithm (signer/algorithm selected-signer)
     :payload payload
     :signature (signer/sign selected-signer payload)}))
