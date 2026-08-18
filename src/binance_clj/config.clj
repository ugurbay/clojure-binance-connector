(ns binance-clj.config
  "Client configuration schema, defaults, and safe public projection."
  (:require [binance-clj.errors :as errors]
            [binance-clj.time :as time]
            [binance-clj.transport :as transport]
            [clojure.string :as str])
  (:import [java.net URI]))

(def environments
  "Official production and Spot Testnet base URLs used by V1."
  {:production
   {:rest-base-url "https://api.binance.com"
    :ws-api-base-url "wss://ws-api.binance.com:443/ws-api/v3"
    :ws-streams-base-url "wss://stream.binance.com:9443/stream"}
   :testnet
   {:rest-base-url "https://testnet.binance.vision"
    :ws-api-base-url "wss://ws-api.testnet.binance.vision/ws-api/v3"
    :ws-streams-base-url "wss://stream.testnet.binance.vision/stream"}})

(def default-recv-window
  "Binance's default receive window in milliseconds."
  time/default-recv-window)

(def max-recv-window
  "Binance's maximum accepted receive window in milliseconds."
  time/max-recv-window)

(def ^:private allowed-keys
  #{:clock
    :connect-timeout-ms
    :credentials
    :enable-live-trading?
    :environment
    :max-read-retries
    :recv-window
    :request-timeout-ms
    :rest-base-url
    :retry-base-delay-ms
    :time-unit
    :transport
    :ws-api-base-url
    :ws-streams-base-url})

(def ^:private credential-keys
  #{:api-key :api-secret :private-key})

(defn system-clock
  "Returns the current Unix epoch time in milliseconds."
  []
  (System/currentTimeMillis))

(defn- fail!
  [message data]
  (throw (errors/connector-error :configuration message data)))

(defn- unknown-keys
  [allowed value]
  (seq (remove allowed (keys value))))

(defn- valid-base-url?
  [value expected-schemes]
  (try
    (let [uri (URI. value)]
      (and (contains? expected-schemes (some-> uri .getScheme str/lower-case))
           (some? (.getHost uri))
           (nil? (.getUserInfo uri))
           (nil? (.getQuery uri))
           (nil? (.getFragment uri))))
    (catch Exception _ false)))

(defn- validate-base-url!
  [config key schemes]
  (when-not (and (string? (get config key))
                 (valid-base-url? (get config key) schemes))
    (fail! "Base URL is invalid for its transport."
           {:field key})))

(defn- normalize-credentials
  [credentials]
  (when-not (map? credentials)
    (fail! "credentials must be a map." {:field :credentials}))
  (when-let [keys (unknown-keys credential-keys credentials)]
    (fail! "credentials contains unknown keys." {:field :credentials :unknown-keys keys}))
  (doseq [[key value] credentials]
    (when-not (or (nil? value) (string? value))
      (fail! "Credential values must be strings or nil." {:field key})))
  (when (and (not (str/blank? (:api-secret credentials)))
             (not (str/blank? (:private-key credentials))))
    (fail! "Configure either api-secret or private-key, not both."
           {:field :credentials}))
  (into {} (remove (comp str/blank? val)) credentials))

(defn normalize
  "Validates client config and fills safe Testnet defaults.

  Transport may be omitted while constructing/configuring a client, but execution
  requires an injected protocol implementation or `{:send! fn}` map."
  [config]
  (when-not (map? config)
    (fail! "Client configuration must be a map." {}))
  (when-let [keys (unknown-keys allowed-keys config)]
    (fail! "Client configuration contains unknown keys." {:unknown-keys keys}))
  (let [environment (get config :environment :testnet)
        environment-urls (get environments environment)]
    (when-not environment-urls
      (fail! "environment must be :testnet or :production."
             {:field :environment :value environment}))
    (let [normalized (merge environment-urls
                            {:environment environment
                             :connect-timeout-ms 10000
                             :max-read-retries 2
                             :request-timeout-ms 15000
                             :recv-window default-recv-window
                             :retry-base-delay-ms 200
                             :time-unit :millisecond
                             :enable-live-trading? false
                             :credentials {}
                             :clock system-clock
                             :transport nil}
                            config
                            {:credentials (normalize-credentials
                                           (get config :credentials {}))
                             :recv-window (time/normalize-recv-window
                                           (get config :recv-window default-recv-window))})]
      (when-not (and (integer? (:request-timeout-ms normalized))
                     (pos? (:request-timeout-ms normalized)))
        (fail! "request-timeout-ms must be a positive integer."
               {:field :request-timeout-ms}))
      (when-not (and (integer? (:connect-timeout-ms normalized))
                     (pos? (:connect-timeout-ms normalized)))
        (fail! "connect-timeout-ms must be a positive integer."
               {:field :connect-timeout-ms}))
      (when-not (and (integer? (:max-read-retries normalized))
                     (<= 0 (:max-read-retries normalized) 10))
        (fail! "max-read-retries must be an integer from 0 to 10."
               {:field :max-read-retries}))
      (when-not (and (integer? (:retry-base-delay-ms normalized))
                     (pos? (:retry-base-delay-ms normalized)))
        (fail! "retry-base-delay-ms must be a positive integer."
               {:field :retry-base-delay-ms}))
      (when-not (boolean? (:enable-live-trading? normalized))
        (fail! "enable-live-trading? must be boolean."
               {:field :enable-live-trading?}))
      (when-not (fn? (:clock normalized))
        (fail! "clock must be a zero-argument function." {:field :clock}))
      (when-not (contains? time/timestamp-units (:time-unit normalized))
        (fail! "time-unit must be :millisecond or :microsecond."
               {:field :time-unit}))
      (when-not (or (nil? (:transport normalized))
                    (transport/transport? (:transport normalized)))
        (fail! "transport must implement Transport or provide a :send! function."
               {:field :transport}))
      (validate-base-url! normalized :rest-base-url #{"https"})
      (validate-base-url! normalized :ws-api-base-url #{"wss"})
      (validate-base-url! normalized :ws-streams-base-url #{"wss"})
      normalized)))

(defn live-trading-enabled?
  "Returns true only when both production environment and explicit guard are set."
  [config]
  (and (= :production (:environment config))
       (true? (:enable-live-trading? config))))

(defn public-view
  "Returns config suitable for diagnostics without credentials or runtime objects."
  [config]
  (-> config
      (dissoc :clock :transport)
      errors/redact))
