(ns binance-clj.encoding
  "Deterministic REST and WebSocket API parameter canonicalization."
  (:require [binance-clj.decimal :as decimal]
            [binance-clj.errors :as errors]
            [clojure.string :as str])
  (:import [java.math BigDecimal]
           [java.nio.charset StandardCharsets]))

(def ^:private upper-hex
  "0123456789ABCDEF")

(defn- fail!
  [message data]
  (throw (errors/connector-error :validation message data)))

(defn- wire-key
  [value]
  (cond
    (keyword? value) (name value)
    (string? value) value
    :else (fail! "Parameter names must be strings or keywords."
                 {:value-type (some-> value class .getName)})))

(defn wire-value
  "Serializes one scalar parameter value without precision loss."
  [value]
  (cond
    (string? value) value
    (keyword? value) (name value)
    (instance? BigDecimal value) (decimal/plain-string value)
    (integer? value) (str value)
    (boolean? value) (if value "true" "false")
    (nil? value) nil
    (or (float? value) (double? value))
    (fail! "Floating-point parameters are forbidden."
           {:value-type (some-> value class .getName)})
    :else
    (fail! "Unsupported parameter value type."
           {:value-type (some-> value class .getName)})))

(defn percent-encode
  "Percent-encodes UTF-8 bytes using RFC 3986 unreserved characters."
  [value]
  (let [bytes (.getBytes ^String (str value) StandardCharsets/UTF_8)
        result (StringBuilder.)]
    (doseq [byte-value bytes]
      (let [unsigned (bit-and (int byte-value) 0xff)]
        (if (or (<= (int \A) unsigned (int \Z))
                (<= (int \a) unsigned (int \z))
                (<= (int \0) unsigned (int \9))
                (contains? #{(int \-) (int \.) (int \_) (int \~)} unsigned))
          (.append result (char unsigned))
          (do
            (.append result \%)
            (.append result (.charAt upper-hex (bit-shift-right unsigned 4)))
            (.append result (.charAt upper-hex (bit-and unsigned 0x0f)))))))
    (.toString result)))

(defn- explicit-pairs
  [parameters]
  (cond
    (map? parameters)
    (sort-by (comp wire-key key) parameters)

    (sequential? parameters)
    (map (fn [entry]
           (when-not (and (sequential? entry) (= 2 (count entry)))
             (fail! "Ordered parameters must contain two-item pairs." {}))
           [(first entry) (second entry)])
         parameters)

    :else
    (fail! "Parameters must be a map or an ordered sequence of pairs."
           {:value-type (some-> parameters class .getName)})))

(defn canonical-query
  "Builds the exact deterministic REST query/form payload.

  Maps are sorted by wire key. A sequence of pairs preserves its explicit order,
  allowing endpoint specs to reproduce Binance's documented wire examples. Nil
  values are omitted; scalar keys and values are percent-encoded separately."
  [parameters]
  (->> (explicit-pairs parameters)
       (keep (fn [[key value]]
               (when-some [serialized (wire-value value)]
                 (str (percent-encode (wire-key key))
                      "="
                      (percent-encode serialized)))))
       (str/join "&")))

(defn canonical-ws-payload
  "Builds a Binance WebSocket API signature payload.

  Parameter names are sorted alphabetically, `signature` is excluded, and UTF-8
  values remain unescaped exactly as required by the WebSocket API contract."
  [parameters]
  (when-not (map? parameters)
    (fail! "WebSocket API parameters must be a map." {}))
  (->> parameters
       (remove (fn [[key _]] (= "signature" (wire-key key))))
       (keep (fn [[key value]]
               (when-some [serialized (wire-value value)]
                 [(wire-key key) serialized])))
       (sort-by first)
       (map (fn [[key value]] (str key "=" value)))
       (str/join "&")))
