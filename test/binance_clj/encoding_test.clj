(ns binance-clj.encoding-test
  (:require [binance-clj.encoding :as sut]
            [binance-clj.errors :as errors]
            [clojure.test :refer [deftest is testing]]))

(deftest rfc3986-percent-encoding-test
  (testing "spaces, reserved ASCII, and UTF-8 are encoded with uppercase hex"
    (is (= "A%20B%2BC%2F%26%3D~"
           (sut/percent-encode "A B+C/&=~")))
    (is (= "%EF%BC%91%EF%BC%92%EF%BC%93"
           (sut/percent-encode "１２３")))))

(deftest deterministic-rest-query-test
  (testing "maps sort by their wire key and omit nil optional values"
    (is (= "a=1&price=1.2300&space=a%20b&z=last"
           (sut/canonical-query {:z "last"
                                 :space "a b"
                                 :price 1.2300M
                                 :missing nil
                                 :a 1}))))
  (testing "ordered pairs retain endpoint-defined order"
    (is (= "symbol=LTCBTC&side=BUY&type=LIMIT"
           (sut/canonical-query [[:symbol "LTCBTC"]
                                 [:side :BUY]
                                 [:type :LIMIT]])))))

(deftest websocket-payload-test
  (is (= "a=first&symbol=１２３&z=last"
         (sut/canonical-ws-payload {:z "last"
                                    :signature "ignored"
                                    :symbol "１２３"
                                    :a "first"}))))

(deftest unsafe-wire-type-test
  (doseq [input [{:price 0.1}
                 {:nested {:not "scalar"}}]]
    (let [error (try
                  (sut/canonical-query input)
                  nil
                  (catch clojure.lang.ExceptionInfo throwable throwable))]
      (is (= :validation (errors/error-category error))))))
