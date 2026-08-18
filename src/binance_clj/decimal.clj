(ns binance-clj.decimal
  "Lossless decimal parsing and plain-wire serialization helpers."
  (:require [binance-clj.errors :as errors])
  (:import [java.math BigDecimal]))

(def ^:private plain-decimal-pattern
  #"^-?\d+(?:\.\d+)?$")

(defn- fail!
  [message data]
  (throw (errors/connector-error :validation message data)))

(defn parse
  "Parses a BigDecimal, integer, or strict plain-decimal string without rounding.

  Exponent notation, floating-point inputs, whitespace, and shorthand forms such
  as `.1` are rejected so the accepted lexical form is always explicit."
  [value]
  (cond
    (instance? BigDecimal value) value

    (integer? value) (BigDecimal. (str value))

    (string? value)
    (if (re-matches plain-decimal-pattern value)
      (BigDecimal. value)
      (fail! "Decimal text must use plain notation without whitespace or exponents."
             {:value-type :string}))

    (or (float? value) (double? value))
    (fail! "Floating-point values are not accepted for exact decimals."
           {:value-type (some-> value class .getName)})

    :else
    (fail! "Value cannot be represented as an exact decimal."
           {:value-type (some-> value class .getName)})))

(defn plain-string
  "Returns a non-scientific decimal string while preserving meaningful scale."
  [value]
  (.toPlainString ^BigDecimal (parse value)))

(defn normalize
  "Returns a numerically canonical BigDecimal without silently rounding it."
  [value]
  (let [decimal (parse value)]
    (if (zero? (.signum ^BigDecimal decimal))
      BigDecimal/ZERO
      (let [stripped (.stripTrailingZeros ^BigDecimal decimal)]
        (if (neg? (.scale ^BigDecimal stripped))
          (.setScale ^BigDecimal stripped 0)
          stripped)))))
