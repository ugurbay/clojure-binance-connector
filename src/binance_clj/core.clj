(ns binance-clj.core
  "Small public facade for the binance-clj SDK core."
  (:require [binance-clj.client :as client]))

(def create-client
  "Creates a validated, safely defaulted connector client."
  client/create-client)

(def execute!
  "Executes a registered endpoint through the connector pipeline."
  client/execute!)

(def close!
  "Closes a connector client idempotently."
  client/close!)

(def closed?
  "Returns whether a connector client is closed."
  client/closed?)

(def client-config
  "Returns a non-sensitive view of the client's configuration."
  client/public-config)

(def synchronize-time!
  "Synchronizes the default connector clock against Binance server time."
  client/synchronize-time!)

(defn runtime-info
  "Returns non-sensitive build and runtime metadata for smoke checks."
  []
  {:name "binance-clj"
   :phase 9
   :status :ready
   :clojure-version (clojure-version)
   :java-version (System/getProperty "java.version")})

(defn -main
  "Prints non-sensitive runtime metadata."
  [& _args]
  (println (pr-str (runtime-info))))
