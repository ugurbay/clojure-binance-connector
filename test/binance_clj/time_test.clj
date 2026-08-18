(ns binance-clj.time-test
  (:require [binance-clj.errors :as errors]
            [binance-clj.time :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest timestamp-unit-test
  (is (= 1645423376532
         (sut/current-timestamp (constantly 1645423376532) :millisecond)))
  (is (= 1645423376532000
         (sut/current-timestamp (constantly 1645423376532) :microsecond)))
  (doseq [[clock unit] [[(constantly -1) :millisecond]
                        [(constantly 1.5) :millisecond]
                        [(constantly 1) :nanosecond]]]
    (let [error (try
                  (sut/current-timestamp clock unit)
                  nil
                  (catch clojure.lang.ExceptionInfo throwable throwable))]
      (is (= :configuration (errors/error-category error))))))

(deftest midpoint-offset-clock-test
  (testing "network round-trip midpoint is used for server offset"
    (is (= 450 (sut/calculate-offset-ms 1550 1000 1200)))
    (let [base (atom 2000)
          clock (sut/synchronized-clock #(deref base))]
      (is (= 2000 ((:now clock))))
      (is (= 450 ((:synchronize! clock) 1550 1000 1200)))
      (is (= 450 ((:offset-ms clock))))
      (is (= 2450 ((:now clock))))))
  (testing "an invalid negative adjusted time is rejected"
    (let [clock (sut/synchronized-clock (constantly 10))]
      ((:synchronize! clock) 0 100 120)
      (let [error (try
                    ((:now clock))
                    nil
                    (catch clojure.lang.ExceptionInfo throwable throwable))]
        (is (= :configuration (errors/error-category error)))))))

(deftest receive-window-boundary-test
  (doseq [valid ["0.001" 5000M 6000.346M 60000M]]
    (is (= (bigdec valid) (sut/normalize-recv-window valid))))
  (doseq [invalid [0M -1M 60000.001M 1.2345M 5000.0 "5e3"]]
    (let [error (try
                  (sut/normalize-recv-window invalid)
                  nil
                  (catch clojure.lang.ExceptionInfo throwable throwable))]
      (is (= :configuration (errors/error-category error))))))
