(ns binance-clj.decimal-test
  (:require [binance-clj.decimal :as sut]
            [binance-clj.errors :as errors]
            [clojure.test :refer [deftest is testing]])
  (:import [java.math BigDecimal]))

(deftest exact-plain-decimal-test
  (testing "tiny, large, negative, and scale-preserving values stay plain"
    (doseq [[input expected] [["0.00000000000000000001" "0.00000000000000000001"]
                              ["999999999999999999999999.99999999"
                               "999999999999999999999999.99999999"]
                              ["-12.5000" "-12.5000"]
                              [42 "42"]
                              [1.2300M "1.2300"]]]
      (is (= expected (sut/plain-string input)))))
  (testing "normalization removes redundant scale but never rounds"
    (is (= (BigDecimal. "1.23") (sut/normalize "1.2300")))
    (is (= "1000" (sut/plain-string (sut/normalize "1000.00"))))))

(deftest unsafe-decimal-input-test
  (doseq [input [0.1 0.1M "1e-8" "1E+8" ".1" "1." "+1" " 1" "1 "]]
    (let [error (try
                  (when-not (= input 0.1M)
                    (sut/parse input))
                  nil
                  (catch clojure.lang.ExceptionInfo throwable throwable))]
      (if (= input 0.1M)
        (is (= "0.1" (sut/plain-string input)))
        (is (= :validation (errors/error-category error)))))))
