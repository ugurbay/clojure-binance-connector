(ns binance-clj.auth.hmac
  "HMAC-SHA256 signer with lowercase hexadecimal wire output."
  (:require [binance-clj.auth.signer :as signer]
            [binance-clj.errors :as errors]
            [clojure.string :as str])
  (:import [java.nio.charset StandardCharsets]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(def ^:private lower-hex
  "0123456789abcdef")

(defn- bytes->hex
  [bytes]
  (let [result (StringBuilder. (* 2 (alength ^bytes bytes)))]
    (doseq [byte-value bytes]
      (let [unsigned (bit-and (int byte-value) 0xff)]
        (.append result (.charAt lower-hex (bit-shift-right unsigned 4)))
        (.append result (.charAt lower-hex (bit-and unsigned 0x0f)))))
    (.toString result)))

(deftype HmacSha256Signer [key-bytes]
  signer/Signer
  (algorithm [_] :hmac-sha256)
  (sign [_ payload]
    (when-not (string? payload)
      (throw (errors/connector-error :auth "Signature payload must be a string." {})))
    (let [mac (Mac/getInstance "HmacSHA256")]
      (.init mac (SecretKeySpec. ^bytes key-bytes "HmacSHA256"))
      (bytes->hex (.doFinal mac (.getBytes ^String payload StandardCharsets/UTF_8)))))

  Object
  (toString [_] "#<HmacSha256Signer:redacted>"))

(defn create-signer
  "Creates a secret-safe HMAC-SHA256 signer from non-empty key material."
  [key-material]
  (when-not (and (string? key-material) (not (str/blank? key-material)))
    (throw (errors/connector-error :configuration
                                   "HMAC key material must be a non-empty string."
                                   {})))
  (HmacSha256Signer. (.getBytes ^String key-material StandardCharsets/UTF_8)))
