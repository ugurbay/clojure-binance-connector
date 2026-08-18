(ns binance-clj.core-test
  (:require [binance-clj.core :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest runtime-info-test
  (testing "runtime metadata is stable and non-sensitive"
    (let [info (sut/runtime-info)]
      (is (= "binance-clj" (:name info)))
      (is (= 9 (:phase info)))
      (is (= :ready (:status info)))
      (is (string? (:clojure-version info)))
      (is (string? (:java-version info)))
      (is (not-any? #(contains? info %)
                    [:api-key :api-secret :private-key :signature])))))
