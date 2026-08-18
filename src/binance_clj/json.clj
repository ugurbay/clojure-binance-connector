(ns binance-clj.json
  "Precision-safe JSON parsing and conservative JSON generation."
  (:require [binance-clj.decimal :as decimal]
            [binance-clj.errors :as errors]
            [clojure.data.json :as data-json]
            [clojure.string :as str]
            [clojure.walk :as walk])
  (:import [java.math BigDecimal]))

(defn- fail!
  [message data]
  (throw (errors/connector-error :api message data)))

(defn read-json
  "Reads one complete JSON value with keyword keys and BigDecimal decimals.

  Empty response bodies become nil. Malformed JSON and trailing data are exposed
  as secret-safe normalized API errors without copying the body into ex-data."
  [body]
  (when-not (or (nil? body) (string? body))
    (fail! "JSON response body must be a string or nil."
           {:value-type (some-> body class .getName)}))
  (when-not (str/blank? body)
    (try
      (data-json/read-str body
                          :bigdec true
                          :key-fn keyword
                          :extra-data-fn (fn [_ _]
                                           (fail! "JSON response contains trailing data."
                                                  {})))
      (catch clojure.lang.ExceptionInfo throwable
        (if (errors/normalized-error? throwable)
          (throw throwable)
          (fail! "JSON response could not be parsed."
                 {:cause-class (some-> throwable class .getName)})))
      (catch Exception throwable
        (fail! "JSON response could not be parsed."
               {:cause-class (some-> throwable class .getName)})))))

(defn- json-wire-value
  [value]
  (cond
    (instance? BigDecimal value) (decimal/plain-string value)
    (or (float? value) (double? value) (ratio? value))
    (fail! "Inexact or fractional Clojure numbers cannot be written as JSON."
           {:value-type (some-> value class .getName)})
    :else value))

(defn write-json
  "Writes JSON after converting BigDecimal values to plain decimal strings.

  Binance financial wire values are strings. Float, double, and Ratio values are
  rejected instead of allowing implicit precision loss or scientific notation."
  [value]
  (try
    (data-json/write-str (walk/postwalk json-wire-value value)
                         :escape-unicode false)
    (catch clojure.lang.ExceptionInfo throwable
      (throw throwable))
    (catch Exception throwable
      (fail! "Value could not be written as JSON."
             {:cause-class (some-> throwable class .getName)}))))

(defn write-websocket-json
  "Writes WebSocket API JSON with exact decimal number tokens.

  Unlike REST financial wire values, WebSocket API `recvWindow` is a JSON
  DECIMAL parameter. BigDecimal is therefore retained as a JSON number while
  inexact Clojure numeric types remain forbidden."
  [value]
  (try
    (data-json/write-str
     (walk/postwalk
      (fn [item]
        (if (or (float? item) (double? item) (ratio? item))
          (fail! "Inexact or fractional Clojure numbers cannot be written as JSON."
                 {:value-type (some-> item class .getName)})
          item))
      value)
     :escape-unicode false)
    (catch clojure.lang.ExceptionInfo throwable
      (throw throwable))
    (catch Exception throwable
      (fail! "Value could not be written as WebSocket JSON."
             {:cause-class (some-> throwable class .getName)}))))
