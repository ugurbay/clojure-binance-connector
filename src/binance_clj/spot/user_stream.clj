(ns binance-clj.spot.user-stream
  "Signed Spot User Data Stream lifecycle over the current WebSocket API."
  (:require [binance-clj.client :as client]
            [binance-clj.errors :as errors]
            [binance-clj.reconciliation :as reconciliation]
            [binance-clj.websocket :as websocket]
            [binance-clj.websocket.normalize :as normalize]))

(def ^:private stream-type ::user-stream)

(defn- ensure-stream!
  [stream]
  (when-not (= stream-type (::type stream))
    (throw (errors/connector-error :configuration
                                   "Invalid User Data Stream handle."
                                   {}))))

(defn create-stream
  "Creates a disconnected signed UDS manager owned by connector-client.

  Every connect/reconnect creates a fresh timestamp and signature."
  ([connector-client] (create-stream connector-client {}))
  ([connector-client options]
   (let [desired? (atom true)
         request-sequence (atom 0)
         subscription-state (atom {:status :pending})
         pending-id (atom nil)
         next-request
         (fn []
           (when @desired?
             (let [id (str "uds-subscribe-" (swap! request-sequence inc))]
               (reset! pending-id id)
               (reset! subscription-state {:status :pending})
               [(client/signed-user-stream-request connector-client id)])))
         observe
         (fn [message]
           (when (and (= :response (:kind message))
                      (= @pending-id (:id message)))
             (if (= 200 (:status message))
               (reset! subscription-state
                       {:status :active
                        :subscription-id (get-in message [:result :subscriptionId])})
               (reset! subscription-state
                       {:error (:error message) :status :failed}))))
         connection
         (websocket/create-connection
          (assoc options
                 :message-observer observe
                 :normalizer normalize/normalize-websocket-api-message
                 :restore-requests-fn next-request
                 :url (client/websocket-url connector-client :websocket-api)))
         stream {::type stream-type
                 ::client connector-client
                 ::connection connection
                 ::desired? desired?
                 ::pending-id pending-id
                 ::request-sequence request-sequence
                 ::subscription-state subscription-state}]
     (client/register-closeable! connector-client #(websocket/close! connection))
     stream)))

(defn connect!
  "Opens the WebSocket API connection and sends a freshly signed subscription."
  [stream]
  (ensure-stream! stream)
  (websocket/connect! (::connection stream)))

(defn subscribe!
  "Requests a subscription after an explicit unsubscribe."
  [stream]
  (ensure-stream! stream)
  (if @(::desired? stream)
    false
    (do
      (reset! (::desired? stream) true)
      (let [id (str "uds-subscribe-" (swap! (::request-sequence stream) inc))]
        (reset! (::pending-id stream) id)
        (reset! (::subscription-state stream) {:status :pending})
        (when (= :open (:status (websocket/snapshot (::connection stream))))
          (websocket/send! (::connection stream)
                           (client/signed-user-stream-request (::client stream) id))))
      true)))

(defn renew!
  "Rotates the WebSocket API connection and creates a fresh signed subscription."
  [stream]
  (ensure-stream! stream)
  (websocket/renew! (::connection stream)))

(defn unsubscribe!
  "Stops the active account subscription without closing the socket."
  [stream]
  (ensure-stream! stream)
  (let [subscription-id (:subscription-id @(::subscription-state stream))]
    (reset! (::desired? stream) false)
    (reset! (::subscription-state stream) {:status :inactive})
    (when (= :open (:status (websocket/snapshot (::connection stream))))
      (websocket/send!
       (::connection stream)
       (cond-> {:id (str "uds-unsubscribe-" (swap! (::request-sequence stream) inc))
                :method "userDataStream.unsubscribe"
                :params {}}
         (some? subscription-id)
         (assoc-in [:params :subscriptionId] subscription-id))))
    true))

(defn poll-event!
  "Returns the next normalized response or account/order event."
  ([stream] (poll-event! stream 0))
  ([stream timeout-ms]
   (ensure-stream! stream)
   (websocket/poll-event! (::connection stream) timeout-ms)))

(defn snapshot
  "Returns secret-safe connection and subscription state."
  [stream]
  (ensure-stream! stream)
  (assoc (websocket/snapshot (::connection stream))
         :user-stream @(::subscription-state stream)))

(defn reconcile-order
  "Applies a normalized executionReport to a Phase 7 lifecycle value."
  [lifecycle event]
  (reconciliation/observe-execution-report lifecycle event))

(defn close!
  "Closes the User Data Stream manager idempotently."
  [stream]
  (ensure-stream! stream)
  (websocket/close! (::connection stream)))
