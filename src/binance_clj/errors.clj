(ns binance-clj.errors
  "Normalized, secret-safe error values for the connector core."
  (:require [clojure.string :as str]))

(def categories
  "Stable error categories exposed by the public API."
  #{:api
    :auth
    :client-closed
    :configuration
    :rate-limit
    :timeout
    :transport
    :unknown-execution
    :validation})

(def ^:private sensitive-key-names
  #{"apikey"
    "apisecret"
    "authorization"
    "credential"
    "credentials"
    "payload"
    "privatekey"
    "privatekeypassphrase"
    "querystring"
    "secret"
    "signedquery"
    "signature"
    "xmbxapikey"})

(defn- normalized-key-name
  [key]
  (-> (if (keyword? key) (name key) (str key))
      str/lower-case
      (str/replace #"[^a-z]" "")))

(defn sensitive-key?
  "Returns true when key names a credential or generated signature field."
  [key]
  (contains? sensitive-key-names (normalized-key-name key)))

(defn redact
  "Recursively replaces credential-bearing values without mutating input data."
  [value]
  (cond
    (map? value)
    (into (empty value)
          (map (fn [[key item]]
                 [key (if (sensitive-key? key)
                        :binance-clj/redacted
                        (redact item))]))
          value)

    (vector? value) (mapv redact value)
    (set? value) (into #{} (map redact) value)
    (sequential? value) (doall (map redact value))
    :else value))

(defn connector-error
  "Creates a normalized ExceptionInfo with redacted structured data."
  ([category message]
   (connector-error category message {} nil))
  ([category message data]
   (connector-error category message data nil))
  ([category message data cause]
   (let [safe-category (if (contains? categories category) category :api)
         safe-data (-> data
                       redact
                       (assoc :binance-clj/error safe-category))]
     (ex-info message safe-data cause))))

(defn error-category
  "Returns the stable category of a normalized connector error."
  [throwable]
  (:binance-clj/error (ex-data throwable)))

(defn normalized-error?
  "Returns true when throwable already follows the connector error contract."
  [throwable]
  (contains? categories (error-category throwable)))

(defn normalize
  "Preserves connector errors and safely wraps unexpected boundary failures."
  ([throwable category]
   (normalize throwable category {}))
  ([throwable category data]
   (if (normalized-error? throwable)
     throwable
     (connector-error category
                      "Connector operation failed."
                      (assoc data :cause-class (some-> throwable class .getName))))))
