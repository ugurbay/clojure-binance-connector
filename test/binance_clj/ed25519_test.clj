(ns binance-clj.ed25519-test
  (:require [binance-clj.auth.ed25519 :as sut]
            [binance-clj.auth.signer :as signer]
            [binance-clj.errors :as errors]
            [clojure.test :refer [deftest is]])
  (:import [java.util Base64]))

(defn- hex->bytes
  [value]
  (byte-array
   (map (fn [[left right]]
          (unchecked-byte (Integer/parseInt (str left right) 16)))
        (partition 2 value))))

(defn- bytes->hex
  [bytes]
  (apply str (map #(format "%02x" (bit-and (int %) 0xff)) bytes)))

(def ^:private rfc-8032-test-1-seed
  "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")

(def ^:private rfc-8032-test-1-signature
  (str "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
       "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"))

(defn- test-private-key-pem
  []
  (let [pkcs8-prefix "302e020100300506032b657004220420"
        der (hex->bytes (str pkcs8-prefix rfc-8032-test-1-seed))]
    (str "-----BEGIN PRIVATE KEY-----\n"
         (.encodeToString (Base64/getEncoder) der)
         "\n-----END PRIVATE KEY-----")))

(deftest rfc-8032-ed25519-vector-test
  (let [selected-signer (sut/create-signer (test-private-key-pem))
        signature (signer/sign selected-signer "")]
    (is (= :ed25519 (signer/algorithm selected-signer)))
    (is (= rfc-8032-test-1-signature
           (bytes->hex (.decode (Base64/getDecoder) ^String signature))))
    (is (= signature (signer/sign selected-signer "")))
    (is (= "#<Ed25519Signer:redacted>" (str selected-signer)))
    (is (not (re-find (re-pattern rfc-8032-test-1-seed)
                      (pr-str selected-signer))))))

(deftest unsupported-private-key-container-test
  (doseq [value ["-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----"
                 "-----BEGIN ENCRYPTED PRIVATE KEY-----\nabc\n-----END ENCRYPTED PRIVATE KEY-----"
                 rfc-8032-test-1-seed]]
    (let [error (try
                  (sut/create-signer value)
                  nil
                  (catch clojure.lang.ExceptionInfo throwable throwable))]
      (is (= :configuration (errors/error-category error)))
      (is (not (re-find (re-pattern rfc-8032-test-1-seed)
                        (pr-str (ex-data error))))))))
