(ns binance-clj.auth.ed25519
  "JDK Ed25519 signer and unencrypted PKCS#8 PEM key loading."
  (:require [binance-clj.auth.signer :as signer]
            [binance-clj.errors :as errors])
  (:import [java.nio.charset StandardCharsets]
           [java.nio.file Files Paths]
           [java.security KeyFactory PrivateKey Signature]
           [java.security.spec PKCS8EncodedKeySpec]
           [java.util Base64]))

(def ^:private pem-pattern
  #"(?s)^\s*-----BEGIN PRIVATE KEY-----\s+([A-Za-z0-9+/=\r\n]+?)\s+-----END PRIVATE KEY-----\s*$")

(defn- fail!
  [message]
  (throw (errors/connector-error :configuration message {})))

(defn- ed25519-key?
  [^PrivateKey private-key]
  (contains? #{"Ed25519" "EdDSA"} (.getAlgorithm private-key)))

(defn load-private-key
  "Loads an Ed25519 private key from a PrivateKey or unencrypted PKCS#8 PEM text.

  OpenSSH, raw seed, and encrypted PEM containers are deliberately rejected."
  [value]
  (cond
    (instance? PrivateKey value)
    (if (ed25519-key? value)
      value
      (fail! "Private key algorithm must be Ed25519."))

    (string? value)
    (if-let [[_ encoded] (re-matches pem-pattern value)]
      (try
        (let [der (.decode (Base64/getMimeDecoder) ^String encoded)
              key (.generatePrivate (KeyFactory/getInstance "Ed25519")
                                    (PKCS8EncodedKeySpec. der))]
          (if (ed25519-key? key)
            key
            (fail! "Private key algorithm must be Ed25519.")))
        (catch clojure.lang.ExceptionInfo throwable (throw throwable))
        (catch Exception _
          (fail! "Private key must be valid unencrypted Ed25519 PKCS#8 PEM.")))
      (fail! "Only unencrypted PKCS#8 PRIVATE KEY PEM is supported."))

    :else
    (fail! "Ed25519 key must be a PrivateKey or PKCS#8 PEM string.")))

(defn load-private-key-file
  "Reads and loads an unencrypted PKCS#8 PEM file."
  [path]
  (try
    (load-private-key
     (Files/readString (Paths/get (str path) (make-array String 0))
                       StandardCharsets/UTF_8))
    (catch clojure.lang.ExceptionInfo throwable (throw throwable))
    (catch Exception _
      (fail! "Ed25519 private-key file could not be read."))))

(deftype Ed25519Signer [private-key]
  signer/Signer
  (algorithm [_] :ed25519)
  (sign [_ payload]
    (when-not (string? payload)
      (throw (errors/connector-error :auth "Signature payload must be a string." {})))
    (let [signature (Signature/getInstance "Ed25519")]
      (.initSign signature ^PrivateKey private-key)
      (.update signature (.getBytes ^String payload StandardCharsets/UTF_8))
      (.encodeToString (Base64/getEncoder) (.sign signature))))

  Object
  (toString [_] "#<Ed25519Signer:redacted>"))

(defn create-signer
  "Creates an Ed25519 signer from a supported private-key representation."
  [private-key]
  (Ed25519Signer. (load-private-key private-key)))
