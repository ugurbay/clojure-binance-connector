(ns binance-clj.rate-limit-test
  (:require [binance-clj.rate-limit :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest binance-header-parsing-test
  (let [parsed (sut/parse-headers
                {"X-MBX-USED-WEIGHT-1M" ["42"]
                 "x-mbx-used-weight-10s" "7"
                 "X-MBX-ORDER-COUNT-10S" ["3"]
                 "Retry-After" ["9"]
                 "ignored" ["value"]})]
    (is (= {[:minute 1] 42 [:second 10] 7} (:request-weight parsed)))
    (is (= {[:second 10] 3} (:orders parsed)))
    (is (= 9 (:retry-after-seconds parsed)))))

(deftest malformed-optional-header-is-ignored-test
  (let [parsed (sut/parse-headers
                {"X-MBX-USED-WEIGHT-1M" "not-a-number"
                 "Retry-After" "tomorrow"})]
    (is (empty? (:request-weight parsed)))
    (is (nil? (:retry-after-seconds parsed)))))

(deftest local-tracker-test
  (let [now (atom 1000)
        tracker (sut/create-tracker #(deref now))]
    (sut/record-attempt! tracker {:weight 20})
    (reset! now 1001)
    (sut/record-response! tracker
                          {"X-MBX-USED-WEIGHT-1M" "120"
                           "X-MBX-ORDER-COUNT-10S" "2"})
    (testing "raw requests and static weights are estimated locally"
      (is (= 1 (:raw-request-count (sut/snapshot tracker))))
      (is (= 20 (:estimated-request-weight (sut/snapshot tracker)))))
    (testing "authoritative response observations replace estimates by interval"
      (is (= {[:minute 1] 120} (:request-weight (sut/snapshot tracker))))
      (is (= {[:second 10] 2} (:orders (sut/snapshot tracker))))
      (is (= 1001 (:updated-at-ms (sut/snapshot tracker)))))))
