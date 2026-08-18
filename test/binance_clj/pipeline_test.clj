(ns binance-clj.pipeline-test
  (:require [binance-clj.config :as config]
            [binance-clj.endpoint :as endpoint]
            [binance-clj.errors :as errors]
            [binance-clj.pipeline :as sut]
            [binance-clj.transport :as transport]
            [clojure.test :refer [deftest is]]))

(def signed-spec
  "Signed read fixture used to exercise injected clock and signer boundaries."
  {:id :spot/account
   :method :get
   :path "/api/v3/account"
   :security :signed
   :execution :read})

(deftest pipeline-stage-order-test
  (let [visited (atom [])
        overrides (into {}
                        (map (fn [stage]
                               [stage
                                (fn [context]
                                  (swap! visited conj stage)
                                  (if (= :normalization stage)
                                    (assoc context :result {:ok? true})
                                    context))]))
                        sut/stage-order)]
    (is (= {:ok? true}
           (sut/execute! {:config {}
                          :endpoint-id :test/endpoint
                          :params {}
                          :registry {}
                          :stage-overrides overrides})))
    (is (= sut/stage-order @visited))))

(deftest clock-signer-and-transport-injection-test
  (let [captured-request (atom nil)
        input-params {:symbol "BTCUSDT"}
        client-config (config/normalize
                       {:clock (constantly 1720000000123)
                        :transport {:send! (fn [request]
                                             (reset! captured-request request)
                                             {:payload {:balances []}})}})
        result (sut/execute!
                {:config client-config
                 :endpoint-id :spot/account
                 :params input-params
                 :registry (endpoint/create-registry [signed-spec])
                 :services {:sign (fn [context]
                                    (assoc-in context [:request :signature]
                                              :test-signature))
                            :parse :payload}})]
    (is (= input-params {:symbol "BTCUSDT"}))
    (is (= 1720000000123 (:timestamp @captured-request)))
    (is (= :test-signature (:signature @captured-request)))
    (is (= {:ok? true
            :data {:balances []}
            :metadata {:endpoint-id :spot/account :environment :testnet}}
           result))))

(deftest boundary-errors-are-normalized-without-secret-data-test
  (let [client-config (config/normalize
                       {:credentials {:api-key "visible-key"
                                      :api-secret "hidden-secret"}
                        :transport {:send! (fn [_]
                                             (throw (ex-info "hidden-secret"
                                                             {:signature "signed-secret"})))}})
        error (try
                (sut/execute! {:config client-config
                               :endpoint-id :spot/account
                               :params {}
                               :registry (endpoint/create-registry [signed-spec])
                               :services {:sign identity}})
                nil
                (catch clojure.lang.ExceptionInfo throwable throwable))]
    (is (= :transport (errors/error-category error)))
    (is (= "Connector operation failed." (ex-message error)))
    (is (nil? (ex-cause error)))
    (is (not (re-find #"hidden-secret|signed-secret|visible-key"
                      (str (ex-message error) (pr-str (ex-data error))))))))

(deftest endpoint-validation-runs-before-transport-test
  (let [transport-called? (atom false)
        spec (assoc signed-spec
                    :validate
                    (fn [context]
                      (throw (errors/connector-error
                              :validation
                              "Rejected before transport."
                              {:endpoint-id (get-in context [:endpoint :id])}))))
        error (try
                (sut/execute!
                 {:config (config/normalize
                           {:transport {:send! (fn [_]
                                                 (reset! transport-called? true))}})
                  :endpoint-id :spot/account
                  :params {}
                  :registry (endpoint/create-registry [spec])})
                nil
                (catch clojure.lang.ExceptionInfo throwable throwable))]
    (is (= :validation (errors/error-category error)))
    (is (false? @transport-called?))))

(deftest normalized-http-envelope-flows-through-result-test
  (let [client-config
        (config/normalize
         {:transport
          {:send! (fn [_]
                    (transport/response
                     {:serverTime 42}
                     {:attempts 1
                      :headers {"x-mbx-used-weight-1m" ["1"]}
                      :http-status 200
                      :rate-limits {:request-weight {[:minute 1] 1}}}))}})
        result (sut/execute!
                {:config client-config
                 :endpoint-id :spot/ping
                 :params {}
                 :registry
                 (endpoint/create-registry
                  [{:id :spot/ping
                    :method :get
                    :path "/api/v3/ping"
                    :security :none
                    :execution :read
                    :weight 1}])})]
    (is (= {:serverTime 42} (:data result)))
    (is (= 200 (get-in result [:metadata :http-status])))
    (is (= {[:minute 1] 1}
           (get-in result [:metadata :rate-limits :request-weight])))))
