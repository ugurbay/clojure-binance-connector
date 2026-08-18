(ns binance-clj.auth.signer
  "Small algorithm-independent signature boundary.")

(defprotocol Signer
  (algorithm [signer] "Returns the stable signer algorithm keyword.")
  (sign [signer payload] "Signs a UTF-8 string and returns its wire representation."))

(defn signer?
  "Returns true when value implements the connector signer contract."
  [value]
  (satisfies? Signer value))
