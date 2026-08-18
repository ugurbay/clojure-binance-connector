(ns binance-clj.json-test
  (:require [binance-clj.errors :as errors]
            [binance-clj.json :as sut]
            [clojure.test :refer [deftest is testing]])
  (:import [java.math BigDecimal]))

(deftest precision-safe-read-test
  (let [parsed (sut/read-json
                (str "{\"decimal\":0.12345678901234567890123456789,"
                     "\"largeId\":900719925474099312345,"
                     "\"price\":\"0.01000000\","
                     "\"unknown\":{\"kept\":true}}"))]
    (is (instance? BigDecimal (:decimal parsed)))
    (is (= (BigDecimal. "0.12345678901234567890123456789") (:decimal parsed)))
    (is (= 900719925474099312345N (:largeId parsed)))
    (is (= "0.01000000" (:price parsed)))
    (is (= {:kept true} (:unknown parsed)))))

(deftest empty-and-invalid-json-test
  (is (nil? (sut/read-json nil)))
  (is (nil? (sut/read-json "  \r\n")))
  (doseq [body ["{" "{} trailing"]]
    (let [error (try
                  (sut/read-json body)
                  nil
                  (catch clojure.lang.ExceptionInfo throwable throwable))]
      (is (= :api (errors/error-category error)))
      (is (not (contains? (ex-data error) :body))))))

(deftest conservative-write-test
  (testing "BigDecimal is emitted as a plain financial string"
    (is (= "{\"price\":\"0.0000000100\",\"symbol\":\"１２３\"}"
           (sut/write-json {:price 0.0000000100M :symbol "１２３"}))))
  (doseq [unsafe [0.1 1/3]]
    (is (= :api
           (errors/error-category
            (try
              (sut/write-json {:value unsafe})
              nil
              (catch clojure.lang.ExceptionInfo throwable throwable)))))))

(deftest websocket-json-keeps-exact-protocol-decimals-as-number-tokens-test
  (is (= "{\"recvWindow\":5000.125,\"timestamp\":1000}"
         (sut/write-websocket-json {:recvWindow 5000.125M :timestamp 1000})))
  (is (= :api
         (errors/error-category
          (try
            (sut/write-websocket-json {:recvWindow 5000.125})
            nil
            (catch clojure.lang.ExceptionInfo throwable throwable))))))
