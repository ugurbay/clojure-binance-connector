(ns binance-clj.websocket-buffer-test
  (:require [binance-clj.websocket.buffer :as sut]
            [clojure.test :refer [deftest is]]))

(deftest drop-oldest-overflow-is-bounded-and-observable-test
  (let [buffer (sut/create-buffer {:capacity 2 :overflow-policy :drop-oldest})]
    (is (true? (sut/offer! buffer :first)))
    (is (true? (sut/offer! buffer :second)))
    (is (true? (sut/offer! buffer :third)))
    (is (= :second (sut/poll! buffer 0)))
    (is (= :third (sut/poll! buffer 0)))
    (is (= {:accepted 3 :capacity 2 :depth 0 :dropped 1
            :overflow-policy :drop-oldest}
           (sut/snapshot buffer)))))

(deftest drop-newest-retains-existing-events-test
  (let [buffer (sut/create-buffer {:capacity 1 :overflow-policy :drop-newest})]
    (is (true? (sut/offer! buffer :kept)))
    (is (false? (sut/offer! buffer :discarded)))
    (is (= :kept (sut/poll! buffer 0)))
    (is (= 1 (:dropped (sut/snapshot buffer))))))
