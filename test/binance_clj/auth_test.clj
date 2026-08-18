(ns binance-clj.auth-test
  (:require [binance-clj.auth :as sut]
            [binance-clj.auth.hmac :as hmac]
            [binance-clj.auth.signer :as signer]
            [binance-clj.errors :as errors]
            [clojure.test :refer [deftest is testing]]))

(def ^:private official-illustrative-key-material
  "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j")

(deftest official-rest-hmac-vectors-test
  (let [selected-signer (hmac/create-signer official-illustrative-key-material)
        ascii-params [[:symbol "LTCBTC"]
                      [:side "BUY"]
                      [:type "LIMIT"]
                      [:timeInForce "GTC"]
                      [:quantity 1M]
                      [:price 0.1M]
                      [:recvWindow 5000M]
                      [:timestamp 1499827319559]]
        unicode-params (assoc-in (vec ascii-params) [0 1] "１２３４５６")]
    (testing "official ASCII REST example"
      (let [signed (sut/sign-rest selected-signer ascii-params)]
        (is (= (str "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1"
                    "&price=0.1&recvWindow=5000&timestamp=1499827319559")
               (:payload signed)))
        (is (= "c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71"
               (:signature signed)))))
    (testing "official Unicode REST example signs percent-encoded bytes"
      (let [signed (sut/sign-rest selected-signer unicode-params)]
        (is (= (str "symbol=%EF%BC%91%EF%BC%92%EF%BC%93%EF%BC%94%EF%BC%95%EF%BC%96"
                    "&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1"
                    "&recvWindow=5000&timestamp=1499827319559")
               (:payload signed)))
        (is (= "e1353ec6b14d888f1164ae9af8228a3dbd508bc82eb867db8ab6046442f33ef3"
               (:signature signed)))))))

(deftest official-websocket-hmac-vector-test
  (let [selected-signer (hmac/create-signer official-illustrative-key-material)
        params {:apiKey "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
                :symbol "BTCUSDT"
                :side "SELL"
                :type "LIMIT"
                :timeInForce "GTC"
                :quantity 0.01000000M
                :price 52000.00M
                :timestamp 1645423376532
                :recvWindow 100M}
        signed (sut/sign-websocket selected-signer params)]
    (is (= (str "apiKey=vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"
                "&price=52000.00&quantity=0.01000000&recvWindow=100&side=SELL"
                "&symbol=BTCUSDT&timeInForce=GTC&timestamp=1645423376532&type=LIMIT")
           (:payload signed)))
    (is (= "aa1b5712c094bc4e57c05a1a5c1fd8d88dcd628338ea863fec7b88e59fe2db24"
           (:signature signed)))
    (is (= :hmac-sha256 (signer/algorithm selected-signer)))
    (is (= "#<HmacSha256Signer:redacted>" (str selected-signer)))
    (is (not (re-find (re-pattern official-illustrative-key-material)
                      (pr-str selected-signer))))
    (testing "signed diagnostic fields are recursively redacted"
      (let [redacted (errors/redact
                      (assoc signed
                             :query-string "sensitive"
                             :signed-query "sensitive"))]
        (is (= :binance-clj/redacted (:payload redacted)))
        (is (= :binance-clj/redacted (:signature redacted)))
        (is (= :binance-clj/redacted (:signed-query redacted)))
        (is (= :binance-clj/redacted (:query-string redacted)))))))
