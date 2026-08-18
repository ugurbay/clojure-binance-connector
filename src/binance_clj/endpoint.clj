(ns binance-clj.endpoint
  "Declarative endpoint specifications and immutable registry operations."
  (:require [binance-clj.errors :as errors]
            [clojure.string :as str]))

(def http-methods
  "HTTP methods available to V1 endpoint declarations."
  #{:delete :get :post :put})

(def security-types
  "Authentication contracts available to endpoint declarations."
  #{:api-key :none :signed})

(def permission-types
  "Binance API-key permission families used by authenticated endpoint specs."
  #{:trade :user-data :user-stream})

(def execution-types
  "Execution semantics used for safety decisions such as retry policy."
  #{:command :read})

(def retry-policies
  "Retry classifications. Commands are always `:never` in V1."
  #{:never :safe})

(def ^:private required-keys
  #{:execution :id :method :path :security})

(def ^:private allowed-keys
  (into required-keys
        #{:description :params :parser :permission :retry-policy :validate :weight}))

(defn- fail!
  [message data]
  (throw (errors/connector-error :configuration message data)))

(defn validate-spec
  "Validates and returns one endpoint spec with an explicit retry policy."
  [spec]
  (when-not (map? spec)
    (fail! "Endpoint specification must be a map." {}))
  (when-let [missing (seq (remove #(contains? spec %) required-keys))]
    (fail! "Endpoint specification is missing required keys."
           {:endpoint-id (:id spec) :missing-keys missing}))
  (when-let [unknown (seq (remove allowed-keys (keys spec)))]
    (fail! "Endpoint specification contains unknown keys."
           {:endpoint-id (:id spec) :unknown-keys unknown}))
  (when-not (qualified-keyword? (:id spec))
    (fail! "Endpoint id must be a qualified keyword." {:endpoint-id (:id spec)}))
  (when-not (contains? http-methods (:method spec))
    (fail! "Endpoint method is unsupported." {:endpoint-id (:id spec)}))
  (when-not (and (string? (:path spec))
                 (str/starts-with? (:path spec) "/api/"))
    (fail! "Endpoint path must start with /api/." {:endpoint-id (:id spec)}))
  (when-not (contains? security-types (:security spec))
    (fail! "Endpoint security type is unsupported." {:endpoint-id (:id spec)}))
  (when (and (contains? spec :permission)
             (not (contains? permission-types (:permission spec))))
    (fail! "Endpoint permission type is unsupported." {:endpoint-id (:id spec)}))
  (when (and (= :none (:security spec)) (contains? spec :permission))
    (fail! "Public endpoints cannot declare an API-key permission."
           {:endpoint-id (:id spec)}))
  (when-not (contains? execution-types (:execution spec))
    (fail! "Endpoint execution type is unsupported." {:endpoint-id (:id spec)}))
  (when (and (:parser spec) (not (fn? (:parser spec))))
    (fail! "Endpoint parser must be a function." {:endpoint-id (:id spec)}))
  (when (and (contains? spec :weight)
             (not (and (integer? (:weight spec))
                       (not (neg? (:weight spec))))))
    (fail! "Endpoint weight must be a non-negative integer."
           {:endpoint-id (:id spec)}))
  (let [retry-policy (get spec :retry-policy
                          (if (= :command (:execution spec)) :never :safe))]
    (when-not (contains? retry-policies retry-policy)
      (fail! "Endpoint retry policy is unsupported." {:endpoint-id (:id spec)}))
    (when (and (= :command (:execution spec))
               (not= :never retry-policy))
      (fail! "Command endpoints cannot be automatically retried."
             {:endpoint-id (:id spec) :retry-policy retry-policy}))
    (assoc spec :retry-policy retry-policy)))

(defn register
  "Returns registry with spec added; duplicate endpoint ids are rejected."
  [registry spec]
  (let [validated (validate-spec spec)
        id (:id validated)]
    (when (contains? registry id)
      (fail! "Endpoint id is already registered." {:endpoint-id id}))
    (assoc registry id validated)))

(defn create-registry
  "Builds an immutable endpoint-id to spec map from a sequence of specs."
  ([] {})
  ([specs]
   (when-not (sequential? specs)
     (fail! "Endpoint registry input must be a sequence." {}))
   (reduce register {} specs)))

(defn lookup
  "Returns endpoint spec or a normalized validation error for an unknown id."
  [registry endpoint-id]
  (or (get registry endpoint-id)
      (throw (errors/connector-error :validation
                                     "Unknown endpoint."
                                     {:endpoint-id endpoint-id}))))
