(ns binance-clj.http-transport-test
  (:require [binance-clj.errors :as errors]
            [binance-clj.transport :as transport]
            [binance-clj.transport.http :as sut]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import [java.io IOException]
           [java.net.http HttpRequest HttpTimeoutException]))

(def base-request
  "Safe read request fixture used by the injected HTTP sender tests."
  {:base-url "https://example.com"
   :endpoint-id :spot/ping
   :execution :read
   :method :get
   :params {:symbol "BTC USDT"}
   :path "/api/v3/ping"
   :request-timeout-ms 1500
   :retry-policy :safe
   :security :none
   :time-unit :millisecond
   :weight 1})

(defn- queued-sender
  [responses calls captured]
  (fn [_client request]
    (swap! calls inc)
    (reset! captured request)
    (let [response (first @responses)]
      (swap! responses #(vec (rest %)))
      (if (instance? Throwable response)
        (throw response)
        response))))

(defn- mock-transport
  [response-values options]
  (let [responses (atom (vec response-values))
        calls (atom 0)
        captured (atom nil)
        delays (atom [])
        http (sut/create-transport
              (merge {:send-fn (queued-sender responses calls captured)
                      :sleep-fn #(swap! delays conj %)}
                     options))]
    {:calls calls
     :captured captured
     :delays delays
     :http http}))

(defn- captured-error
  [operation]
  (try
    (operation)
    nil
    (catch clojure.lang.ExceptionInfo throwable throwable)))

(deftest successful-request-and-metadata-test
  (let [{:keys [calls captured http]}
        (mock-transport
         [{:status 200
           :headers {"X-MBX-USED-WEIGHT-1M" ["12"]
                     "Content-Type" ["application/json"]}
           :body "{\"serverTime\":1499827319559,\"price\":\"0.01000000\"}"}]
         {})
        response (transport/send! http base-request)
        ^HttpRequest request @captured]
    (try
      (is (transport/response? response))
      (is (= {:serverTime 1499827319559 :price "0.01000000"}
             (:body response)))
      (is (= 200 (get-in response [:metadata :http-status])))
      (is (= {[:minute 1] 12}
             (get-in response [:metadata :rate-limits :request-weight])))
      (is (= 1 @calls))
      (is (= "GET" (.method request)))
      (is (= "https://example.com/api/v3/ping?symbol=BTC%20USDT"
             (str (.uri request))))
      (is (= 1500 (.toMillis (.orElseThrow (.timeout request)))))
      (is (= "application/json"
             (.orElseThrow (.firstValue (.headers request) "Accept"))))
      (is (= 1 (:raw-request-count (sut/rate-limit-snapshot http))))
      (finally
        (transport/close! http)))))

(deftest empty-success-and-malformed-success-test
  (testing "204/blank bodies normalize to nil"
    (let [{:keys [http]} (mock-transport [{:status 204 :headers {} :body ""}] {})]
      (try
        (is (nil? (:body (transport/send! http base-request))))
        (finally (transport/close! http)))))
  (testing "malformed 2xx JSON is an API error"
    (let [{:keys [http]} (mock-transport [{:status 200 :headers {} :body "{"}] {})
          error (captured-error #(transport/send! http base-request))]
      (try
        (is (= :api (errors/error-category error)))
        (finally (transport/close! http))))))

(deftest safe-read-retry-test
  (let [{:keys [calls delays http]}
        (mock-transport
         [{:status 503 :headers {} :body "not-json"}
          (IOException. "temporary")
          {:status 200 :headers {} :body "{}"}]
         {:max-read-retries 2 :retry-base-delay-ms 25})]
    (try
      (is (= {} (:body (transport/send! http base-request))))
      (is (= 3 @calls))
      (is (= [25 50] @delays))
      (is (= 3 (:raw-request-count (sut/rate-limit-snapshot http))))
      (finally (transport/close! http)))))

(deftest retry-after-and-ip-ban-test
  (testing "429 safe read waits the published number of seconds"
    (let [{:keys [calls delays http]}
          (mock-transport
           [{:status 429
             :headers {"Retry-After" "2"}
             :body "{\"code\":-1003,\"msg\":\"slow down\"}"}
            {:status 200 :headers {} :body "{}"}]
           {:max-read-retries 1})]
      (try
        (is (= {} (:body (transport/send! http base-request))))
        (is (= 2 @calls))
        (is (= [2000] @delays))
        (finally (transport/close! http)))))
  (testing "418 is never automatically retried"
    (let [{:keys [calls http]}
          (mock-transport
           [{:status 418
             :headers {"Retry-After" "120"}
             :body "{\"code\":-1003,\"msg\":\"banned\"}"}]
           {})
          error (captured-error #(transport/send! http base-request))]
      (try
        (is (= :rate-limit (errors/error-category error)))
        (is (= 418 (:http-status (ex-data error))))
        (is (= 120 (:retry-after-seconds (ex-data error))))
        (is (= 1 @calls))
        (finally (transport/close! http))))))

(deftest rate-limit-without-valid-retry-after-is-not-retried-test
  (let [{:keys [calls delays http]}
        (mock-transport
         [{:status 429
           :headers {"Retry-After" "invalid"}
           :body "{\"code\":-1003,\"msg\":\"slow down\"}"}]
         {:max-read-retries 5})
        error (captured-error #(transport/send! http base-request))]
    (try
      (is (= :rate-limit (errors/error-category error)))
      (is (= 1 @calls))
      (is (empty? @delays))
      (finally (transport/close! http)))))

(deftest command-uncertainty-is-never-retried-test
  (doseq [response [{:status 503 :headers {} :body "{}"}
                    (HttpTimeoutException. "timed out")]]
    (let [{:keys [calls delays http]}
          (mock-transport [response] {:max-read-retries 5})
          command-request (assoc base-request
                                 :endpoint-id :spot/order
                                 :execution :command
                                 :method :post
                                 :retry-policy :never)
          error (captured-error #(transport/send! http command-request))]
      (try
        (is (= :unknown-execution (errors/error-category error)))
        (is (= 1 @calls))
        (is (empty? @delays))
        (finally (transport/close! http))))))

(deftest command-binance-timeout-code-is-unknown-test
  (let [{:keys [calls http]}
        (mock-transport
         [{:status 400
           :headers {}
           :body "{\"code\":-1007,\"msg\":\"execution unknown\"}"}]
         {:max-read-retries 5})
        command-request (assoc base-request
                               :execution :command
                               :method :post
                               :retry-policy :never)
        error (captured-error #(transport/send! http command-request))]
    (try
      (is (= :unknown-execution (errors/error-category error)))
      (is (= -1007 (:binance-code (ex-data error))))
      (is (= 1 @calls))
      (finally (transport/close! http)))))

(deftest exhausted-safe-timeout-is-normalized-test
  (let [{:keys [calls delays http]}
        (mock-transport
         [(HttpTimeoutException. "one")
          (HttpTimeoutException. "two")]
         {:max-read-retries 1 :retry-base-delay-ms 10})
        error (captured-error #(transport/send! http base-request))]
    (try
      (is (= :timeout (errors/error-category error)))
      (is (= 2 (:attempts (ex-data error))))
      (is (= 2 @calls))
      (is (= [10] @delays))
      (finally (transport/close! http)))))

(deftest normalized-binance-error-test
  (doseq [[body expected-category expected-code expected-reason]
          [["{\"code\":-1121,\"msg\":\"Invalid symbol.\"}" :api -1121 nil]
           ["{\"code\":-1100,\"msg\":\"Illegal characters found in parameter.\"}"
            :api -1100 :illegal-characters]
           ["{\"code\":-2015,\"msg\":\"Rejected\"}" :auth -2015
            :api-key-ip-or-permission-rejected]
           [(str "{\"code\":-2010,\"msg\":"
                 "\"Account has insufficient balance for requested action.\"}")
            :api -2010 :insufficient-balance]
           ["{\"code\":-1013,\"msg\":\"Filter failure: PERCENT_PRICE_BY_SIDE\"}"
            :api -1013 :filter-failure]
           ["{\"code\":-2010,\"msg\":\"Market is closed.\"}"
            :api -2010 :new-order-rejected]
           ["not-json" :api nil nil]]]
    (let [{:keys [http]}
          (mock-transport [{:status 400 :headers {} :body body}]
                          {:max-read-retries 0})
          error (captured-error #(transport/send! http base-request))]
      (try
        (is (= expected-category (errors/error-category error)))
        (is (= expected-code (:binance-code (ex-data error))))
        (is (= expected-reason (:binance-reason (ex-data error))))
        (if (= :filter-failure expected-reason)
          (is (= "PERCENT_PRICE_BY_SIDE" (:binance-filter (ex-data error))))
          (is (nil? (:binance-filter (ex-data error)))))
        (is (= 400 (:http-status (ex-data error))))
        (is (not (str/includes? (pr-str (ex-data error)) body)))
        (finally (transport/close! http))))))

(deftest authenticated-request-and-secret-safe-boundary-test
  (let [captured (atom nil)
        secret-key "not-for-output"
        http (sut/create-transport
              {:max-read-retries 0
               :send-fn (fn [_ request]
                          (reset! captured request)
                          (throw (ex-info secret-key {:api-key secret-key})))})
        request (assoc base-request
                       :api-key secret-key
                       :security :api-key
                       :time-unit :microsecond)
        error (captured-error #(transport/send! http request))]
    (try
      (is (= :transport (errors/error-category error)))
      (is (= "MICROSECOND"
             (.orElseThrow (.firstValue (.headers ^HttpRequest @captured)
                                        "X-MBX-TIME-UNIT"))))
      (is (= secret-key
             (.orElseThrow (.firstValue (.headers ^HttpRequest @captured)
                                        "X-MBX-APIKEY"))))
      (is (not (str/includes? (str (ex-message error) (pr-str (ex-data error)))
                              secret-key)))
      (finally (transport/close! http)))))

(deftest signed-request-requires-exact-signed-query-test
  (let [{:keys [http]} (mock-transport [] {})
        error (captured-error
               #(transport/send! http
                                 (assoc base-request
                                        :api-key "public-key"
                                        :security :signed)))]
    (try
      (is (= :auth (errors/error-category error)))
      (finally (transport/close! http)))))
