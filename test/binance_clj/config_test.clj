(ns binance-clj.config-test
  (:require [binance-clj.config :as sut]
            [binance-clj.errors :as errors]
            [clojure.test :refer [deftest is testing]]))

(deftest safe-defaults-test
  (testing "Testnet, timeout, clock, and recvWindow defaults are explicit"
    (let [config (sut/normalize {})]
      (is (= :testnet (:environment config)))
      (is (= "https://testnet.binance.vision" (:rest-base-url config)))
      (is (= "wss://ws-api.testnet.binance.vision/ws-api/v3"
             (:ws-api-base-url config)))
      (is (= 15000 (:request-timeout-ms config)))
      (is (= 10000 (:connect-timeout-ms config)))
      (is (= 2 (:max-read-retries config)))
      (is (= 200 (:retry-base-delay-ms config)))
      (is (= 5000M (:recv-window config)))
      (is (= :millisecond (:time-unit config)))
      (is (fn? (:clock config)))
      (is (false? (sut/live-trading-enabled? config))))))

(deftest environment-and-url-selection-test
  (testing "production requires its explicit environment and live-trading guard"
    (let [locked (sut/normalize {:environment :production})
          enabled (sut/normalize {:environment :production
                                  :enable-live-trading? true})]
      (is (= "https://api.binance.com" (:rest-base-url locked)))
      (is (false? (sut/live-trading-enabled? locked)))
      (is (true? (sut/live-trading-enabled? enabled)))))
  (testing "an explicit secure base URL overrides the environment default"
    (is (= "https://api1.binance.com"
           (:rest-base-url
            (sut/normalize {:rest-base-url "https://api1.binance.com"}))))))

(deftest recv-window-validation-test
  (testing "three fractional millisecond digits are accepted"
    (is (= 6000.346M (:recv-window (sut/normalize {:recv-window 6000.346M})))))
  (doseq [invalid [0M 60000.001M 1.2345M 5000.0]]
    (testing (str "invalid recvWindow is rejected: " invalid)
      (let [error (try
                    (sut/normalize {:recv-window invalid})
                    nil
                    (catch clojure.lang.ExceptionInfo throwable throwable))]
        (is (= :configuration (errors/error-category error)))))))

(deftest timestamp-unit-config-test
  (is (= :microsecond (:time-unit (sut/normalize {:time-unit :microsecond}))))
  (let [error (try
                (sut/normalize {:time-unit :nanosecond})
                nil
                (catch clojure.lang.ExceptionInfo throwable throwable))]
    (is (= :configuration (errors/error-category error)))))

(deftest config-schema-rejects-ambiguous-auth-test
  (let [error (try
                (sut/normalize {:credentials {:api-key "public-key"
                                              :api-secret "hmac-secret"
                                              :private-key "private-key"}})
                nil
                (catch clojure.lang.ExceptionInfo throwable throwable))]
    (is (= :configuration (errors/error-category error)))
    (is (not (re-find #"hmac-secret|private-key" (pr-str (ex-data error)))))))

(deftest encrypted-private-key-passphrase-is-not-silently-accepted-test
  (let [error (try
                (sut/normalize {:credentials {:api-key "public-key"
                                              :private-key "private-key"
                                              :private-key-passphrase "secret"}})
                nil
                (catch clojure.lang.ExceptionInfo throwable throwable))]
    (is (= :configuration (errors/error-category error)))
    (is (= :credentials (:field (ex-data error))))
    (is (not (re-find #"public-key|\"private-key\"|secret"
                      (pr-str (ex-data error)))))))

(deftest public-view-redacts-runtime-and-credentials-test
  (let [config (sut/normalize {:credentials {:api-key "public-key"
                                             :api-secret "top-secret"}
                               :clock (constantly 42)
                               :transport {:send! identity}})
        public (sut/public-view config)]
    (is (not (contains? public :clock)))
    (is (not (contains? public :transport)))
    (is (= :binance-clj/redacted (:credentials public)))
    (is (not (re-find #"public-key|top-secret" (pr-str public))))))

(deftest invalid-url-and-unknown-option-test
  (doseq [input [{:rest-base-url "http://api.binance.com"}
                 {:ws-api-base-url "https://ws-api.binance.com"}
                 {:connect-timeout-ms 0}
                 {:max-read-retries 11}
                 {:retry-base-delay-ms 0}
                 {:typo-timeout 10}]]
    (let [error (try
                  (sut/normalize input)
                  nil
                  (catch clojure.lang.ExceptionInfo throwable throwable))]
      (is (= :configuration (errors/error-category error))))))
