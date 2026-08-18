(ns binance-clj.pipeline
  "Transport-independent request execution pipeline."
  (:require [binance-clj.endpoint :as endpoint]
            [binance-clj.errors :as errors]
            [binance-clj.time :as time]
            [binance-clj.transport :as transport]))

(def stage-order
  "Stable request lifecycle order. Endpoint resolution is deliberately first."
  [:endpoint
   :validation
   :serialization
   :timestamp
   :signing
   :transport
   :parsing
   :normalization])

(def ^:private stage-error-categories
  {:endpoint :validation
   :validation :validation
   :serialization :validation
   :timestamp :configuration
   :signing :auth
   :transport :transport
   :parsing :api
   :normalization :api})

(defn- fail!
  [category message data]
  (throw (errors/connector-error category message data)))

(defn- default-endpoint-stage
  [{:keys [endpoint-id registry] :as context}]
  (assoc context :endpoint (endpoint/lookup registry endpoint-id)))

(defn- default-validation-stage
  [{:keys [endpoint params] :as context}]
  (when-not (map? params)
    (fail! :validation "Endpoint params must be a map." {:endpoint-id (:id endpoint)}))
  (if-let [validate-fn (or (:validate endpoint)
                           (:validate (:services context)))]
    (validate-fn context)
    context))

(defn- default-serialization-stage
  [{:keys [config endpoint params] :as context}]
  (let [declared-weight (:weight endpoint)
        weight (or (:request-weight context)
                   declared-weight
                   0)]
    (when-not (and (integer? weight) (not (neg? weight)))
      (fail! :configuration "Resolved endpoint weight must be a non-negative integer."
             {:endpoint-id (:id endpoint)}))
    (assoc context
           :request
           (cond->
            {:base-url (:rest-base-url config)
             :endpoint-id (:id endpoint)
             :execution (:execution endpoint)
             :method (:method endpoint)
             :params params
             :path (:path endpoint)
             :permission (:permission endpoint)
             :request-timeout-ms (:request-timeout-ms config)
             :retry-policy (:retry-policy endpoint)
             :security (:security endpoint)
             :time-unit (:time-unit config)
             :weight weight}
             (not= :none (:security endpoint))
             (assoc :api-key (get-in config [:credentials :api-key]))))))

(defn- default-timestamp-stage
  [{:keys [config endpoint] :as context}]
  (if (= :signed (:security endpoint))
    (let [timestamp (time/current-timestamp (:clock config) (:time-unit config))]
      (assoc-in context [:request :timestamp] timestamp))
    context))

(defn- default-signing-stage
  [{:keys [endpoint services] :as context}]
  (if (= :signed (:security endpoint))
    (if-let [sign-fn (:sign services)]
      (sign-fn context)
      (fail! :auth "Signed endpoint requires an injected signer."
             {:endpoint-id (:id endpoint)}))
    context))

(defn- default-transport-stage
  [{:keys [config endpoint request] :as context}]
  (if-let [selected-transport (:transport config)]
    (assoc context :transport-response (transport/send! selected-transport request))
    (fail! :configuration "Request execution requires an injected transport."
           {:endpoint-id (:id endpoint)})))

(defn- default-parsing-stage
  [{:keys [endpoint services transport-response] :as context}]
  (let [parser (or (:parser endpoint) (:parse services))]
    (assoc context
           :parsed-response
           (cond
             parser (parser (if (transport/response? transport-response)
                              (:body transport-response)
                              transport-response))
             (transport/response? transport-response) (:body transport-response)
             :else transport-response))))

(defn- default-normalization-stage
  [{:keys [config endpoint parsed-response transport-response] :as context}]
  (assoc context
         :result
         {:ok? true
          :data parsed-response
          :metadata (merge {:endpoint-id (:id endpoint)
                            :environment (:environment config)}
                           (when (transport/response? transport-response)
                             (:metadata transport-response)))}))

(def ^:private default-stages
  {:endpoint default-endpoint-stage
   :validation default-validation-stage
   :serialization default-serialization-stage
   :timestamp default-timestamp-stage
   :signing default-signing-stage
   :transport default-transport-stage
   :parsing default-parsing-stage
   :normalization default-normalization-stage})

(defn- execute-stage
  [context stage overrides]
  (let [stage-fn (get overrides stage (get default-stages stage))]
    (try
      (let [next-context (stage-fn (assoc context :stage stage))]
        (when-not (map? next-context)
          (fail! :configuration "Pipeline stage must return a context map."
                 {:stage stage}))
        next-context)
      (catch Throwable throwable
        (throw (errors/normalize throwable
                                 (get stage-error-categories stage :api)
                                 {:endpoint-id (:endpoint-id context)
                                  :stage stage}))))))

(defn execute!
  "Runs one endpoint request through the stable stage order and returns a result map."
  [{:keys [config endpoint-id params registry services stage-overrides]}]
  (let [initial-context {:config config
                         :endpoint-id endpoint-id
                         :params (or params {})
                         :registry registry
                         :services (or services {})}
        final-context (reduce #(execute-stage %1 %2 (or stage-overrides {}))
                              initial-context
                              stage-order)]
    (:result final-context)))
