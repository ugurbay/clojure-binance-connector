(ns binance-clj.harness-test
  (:require [binance-clj.core :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest offline-integration-harness-test
  (testing "integration-test alias can load production namespaces offline"
    (is (= "binance-clj" (:name (sut/runtime-info))))))
