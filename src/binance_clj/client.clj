(ns binance-clj.client
  "Public data-oriented client lifecycle and execution facade."
  (:require [binance-clj.auth :as auth]
            [binance-clj.auth.ed25519 :as ed25519]
            [binance-clj.auth.hmac :as hmac]
            [binance-clj.config :as config]
            [binance-clj.endpoint :as endpoint]
            [binance-clj.errors :as errors]
            [binance-clj.pipeline :as pipeline]
            [binance-clj.spot.endpoints :as spot-endpoints]
            [binance-clj.time :as time]
            [binance-clj.transport :as transport]
            [binance-clj.transport.http :as http]
            [clojure.string :as str]))

(def ^:private client-type
  ::client)

(def ^:private service-keys
  #{:claim-client-order-id :new-client-order-id :parse :sign :sign-websocket :validate})

(defn- fail!
  [message data]
  (throw (errors/connector-error :configuration message data)))

(defn- validate-injections!
  [services stage-overrides]
  (when-not (map? services)
    (fail! "services must be a map." {:field :services}))
  (when-let [unknown (seq (remove service-keys (keys services)))]
    (fail! "services contains unknown keys." {:field :services :unknown-keys unknown}))
  (when-not (every? fn? (vals services))
    (fail! "Every injected service must be a function." {:field :services}))
  (when-not (map? stage-overrides)
    (fail! "stage-overrides must be a map." {:field :stage-overrides}))
  (when-let [unknown (seq (remove (set pipeline/stage-order) (keys stage-overrides)))]
    (fail! "stage-overrides contains unknown stages."
           {:field :stage-overrides :unknown-stages unknown}))
  (when-not (every? fn? (vals stage-overrides))
    (fail! "Every stage override must be a function." {:field :stage-overrides})))

(defn- normalize-registry
  [registry]
  (cond
    (nil? registry) (endpoint/create-registry spot-endpoints/specs)

    (map? registry)
    (do
      (doseq [[id spec] registry]
        (when-not (= id (:id spec))
          (fail! "Registry key must match endpoint spec id." {:endpoint-id id})))
      (endpoint/create-registry (vals registry)))

    :else (endpoint/create-registry registry)))

(defn- configured-signer
  [normalized-config]
  (let [{:keys [api-secret private-key]} (:credentials normalized-config)]
    (cond
      api-secret (hmac/create-signer api-secret)
      private-key (ed25519/create-signer private-key)
      :else nil)))

(defn- signing-service
  [selected-signer]
  (fn [{:keys [config request] :as context}]
    (let [parameters (assoc (:params request)
                            :recvWindow (:recv-window config)
                            :timestamp (:timestamp request))
          signed (auth/sign-rest selected-signer parameters)]
      (assoc context
             :request
             (assoc request
                    :params parameters
                    :signed-query (:signed-query signed))))))

(defn- websocket-signing-service
  [selected-signer]
  (fn [parameters]
    (auth/sign-websocket selected-signer parameters)))

(defn- generated-client-order-id
  []
  (str "clj-" (str/replace (str (random-uuid)) "-" "")))

(defn- claim-client-order-id-service
  [claimed-order-ids]
  (fn [client-order-id]
    (let [[before _after] (swap-vals! claimed-order-ids conj client-order-id)]
      (when (contains? before client-order-id)
        (throw (errors/connector-error
                :validation
                "Client order id was already used by this client."
                {:field :new-client-order-id})))
      client-order-id)))

(defn- default-services
  [normalized-config claimed-order-ids]
  (let [selected-signer (configured-signer normalized-config)]
    (cond-> {:claim-client-order-id
             (claim-client-order-id-service claimed-order-ids)
             :new-client-order-id generated-client-order-id}
      selected-signer (assoc :sign (signing-service selected-signer)
                             :sign-websocket
                             (websocket-signing-service selected-signer)))))

(defn create-client
  "Creates a client map without opening a network connection.

  Pipeline services and stage overrides exist for boundary injection and contract
  tests. A reusable JDK HTTP transport is created by default without connecting."
  ([] (create-client {}))
  ([{:keys [registry services stage-overrides] :as options}]
   (let [clock-sync (when-not (contains? options :clock)
                      (time/synchronized-clock config/system-clock))
         services (or services {})
         stage-overrides (or stage-overrides {})
         _ (validate-injections! services stage-overrides)
         normalized-config (config/normalize
                            (cond-> (dissoc options
                                            :registry
                                            :services
                                            :stage-overrides)
                              clock-sync (assoc :clock (:now clock-sync))))
         normalized-config (if (:transport normalized-config)
                             normalized-config
                             (assoc normalized-config
                                    :transport
                                    (http/create-transport
                                     {:connect-timeout-ms
                                      (:connect-timeout-ms normalized-config)
                                      :max-read-retries
                                      (:max-read-retries normalized-config)
                                      :retry-base-delay-ms
                                      (:retry-base-delay-ms normalized-config)})))
         normalized-registry (normalize-registry registry)
         claimed-order-ids (atom #{})
         normalized-services (merge (default-services normalized-config
                                                      claimed-order-ids)
                                    services)]
     {::type client-type
      ::config normalized-config
      ::registry normalized-registry
      ::services normalized-services
      ::stage-overrides stage-overrides
      ::clock-sync clock-sync
      ::claimed-order-ids claimed-order-ids
      ::state (atom {:closeables #{} :closed? false})})))

(defn client?
  "Returns true when value is a client created by this namespace."
  [value]
  (and (map? value) (= client-type (::type value))))

(defn- ensure-client!
  [client]
  (when-not (client? client)
    (throw (errors/connector-error :configuration "Invalid connector client." {}))))

(defn closed?
  "Returns whether client lifecycle has been closed."
  [client]
  (ensure-client! client)
  (:closed? @(::state client)))

(defn public-config
  "Returns the client's non-sensitive, serializable configuration view."
  [client]
  (ensure-client! client)
  (config/public-view (::config client)))

(defn execute!
  "Executes endpoint-id with immutable params through the generic pipeline."
  ([client endpoint-id]
   (execute! client endpoint-id {}))
  ([client endpoint-id params]
   (ensure-client! client)
   (when (closed? client)
     (throw (errors/connector-error :client-closed
                                    "Connector client is closed."
                                    {:endpoint-id endpoint-id})))
   (pipeline/execute! {:config (::config client)
                       :endpoint-id endpoint-id
                       :params params
                       :registry (::registry client)
                       :services (::services client)
                       :stage-overrides (::stage-overrides client)})))

(defn synchronize-time!
  "Synchronizes the default client clock against Binance server time.

  Returns safe timing metadata. Clients configured with a custom `:clock` own
  that clock's synchronization and cannot use this helper."
  [client]
  (ensure-client! client)
  (when (closed? client)
    (throw (errors/connector-error :client-closed
                                   "Connector client is closed."
                                   {:operation :synchronize-time})))
  (let [clock-sync (::clock-sync client)]
    (when-not clock-sync
      (fail! "A client with a custom clock must synchronize that clock externally."
             {:field :clock}))
    (let [request-start-ms (config/system-clock)
          response (execute! client :spot/server-time)
          response-end-ms (config/system-clock)
          server-time-ms (get-in response [:data :serverTime])
          offset-ms ((:synchronize! clock-sync)
                     server-time-ms
                     request-start-ms
                     response-end-ms)]
      {:offset-ms offset-ms
       :round-trip-ms (- response-end-ms request-start-ms)
       :server-time-ms server-time-ms})))

(defn next-client-order-id
  "Generates a client order id through the client's configured service."
  [client]
  (ensure-client! client)
  (when (closed? client)
    (throw (errors/connector-error :client-closed
                                   "Connector client is closed."
                                   {:operation :new-client-order-id})))
  (let [generator (:new-client-order-id (::services client))]
    (when-not (fn? generator)
      (fail! "Client order id generator is unavailable."
             {:field :new-client-order-id}))
    (generator)))

(defn websocket-url
  "Returns the configured secure URL for an internal WebSocket channel."
  [client channel]
  (ensure-client! client)
  (when (closed? client)
    (throw (errors/connector-error :client-closed
                                   "Connector client is closed."
                                   {:operation :websocket-url})))
  (case channel
    :market-streams (:ws-streams-base-url (::config client))
    :websocket-api (:ws-api-base-url (::config client))
    (fail! "Unknown WebSocket channel." {:field :channel})))

(defn signed-user-stream-request
  "Builds a fresh secret-bearing UDS request for the WebSocket transport boundary.

  This internal connector function must never be logged or placed in diagnostics."
  [client request-id]
  (ensure-client! client)
  (when (closed? client)
    (throw (errors/connector-error :client-closed
                                   "Connector client is closed."
                                   {:operation :user-stream-subscribe})))
  (let [config (::config client)
        api-key (get-in config [:credentials :api-key])
        sign-websocket (:sign-websocket (::services client))]
    (when (str/blank? api-key)
      (throw (errors/connector-error :auth
                                     "User Data Stream requires an API key."
                                     {})))
    (when-not (fn? sign-websocket)
      (throw (errors/connector-error :auth
                                     "User Data Stream signing is unavailable."
                                     {})))
    (let [parameters {:apiKey api-key
                      :recvWindow (:recv-window config)
                      :timestamp ((:clock config))}
          signed (sign-websocket parameters)]
      {:id request-id
       :method "userDataStream.subscribe.signature"
       :params (assoc parameters :signature (:signature signed))})))

(defn register-closeable!
  "Registers an idempotent no-argument close function under client ownership."
  [client close-fn]
  (ensure-client! client)
  (when-not (fn? close-fn)
    (fail! "Client closeable must be a function." {:field :closeable}))
  (swap! (::state client)
         (fn [state]
           (when (:closed? state)
             (throw (errors/connector-error :client-closed
                                            "Connector client is closed."
                                            {:operation :register-closeable})))
           (update state :closeables conj close-fn)))
  close-fn)

(defn close!
  "Closes the transport at most once. Repeated calls are safe and return false."
  [client]
  (ensure-client! client)
  (let [state (::state client)
        [before _after] (swap-vals! state #(assoc % :closed? true))]
    (if (false? (:closed? before))
      (do
        (doseq [close-fn (:closeables before)]
          (try (close-fn) (catch Throwable _)))
        (try
          (transport/close! (:transport (::config client)))
          (catch Throwable throwable
            (throw (errors/normalize throwable :transport {:operation :close}))))
        true)
      false)))
