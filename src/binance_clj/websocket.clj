(ns binance-clj.websocket
  "Shared resilient WebSocket connection, subscription, and event-buffer manager."
  (:require [binance-clj.errors :as errors]
            [binance-clj.json :as json]
            [binance-clj.transport.websocket :as ws-transport]
            [binance-clj.websocket.buffer :as buffer]
            [clojure.string :as str])
  (:import [java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit]
           [java.util.concurrent.atomic AtomicLong]))

(def ^:private connection-type ::connection)
(def ^:private maximum-streams 1024)
(def ^:private default-renewal-ms (* (+ (* 23 60) 50) 60 1000))
(def ^:private default-heartbeat-timeout-ms 65000)

(declare connect-internal! schedule-reconnect!)

(defn- fail!
  [category message data]
  (throw (errors/connector-error category message data)))

(defn- daemon-scheduler
  []
  (let [executor
        (Executors/newSingleThreadScheduledExecutor
         (reify ThreadFactory
           (newThread [_ task]
             (doto (Thread. task "binance-clj-websocket")
               (.setDaemon true)))))]
    {:schedule! (fn [delay-ms task]
                  (.schedule ^ScheduledExecutorService executor
                             ^Runnable task
                             delay-ms
                             TimeUnit/MILLISECONDS))
     :close! #(.shutdownNow ^ScheduledExecutorService executor)}))

(defn- connection?
  [value]
  (and (map? value) (= connection-type (::type value))))

(defn- ensure-connection!
  [connection]
  (when-not (connection? connection)
    (fail! :configuration "Invalid WebSocket connection handle." {})))

(defn- next-id
  [connection]
  (.incrementAndGet ^AtomicLong (::request-id connection)))

(defn- connected?
  [connection]
  (= :open (:status @(::state connection))))

(defn- throttle-control!
  [connection]
  (let [control-lock (::control-lock connection)]
    (locking control-lock
      (loop []
        (let [now ((::monotonic-clock connection))
              recent (->> @(::control-timestamps connection)
                          (filter #(> % (- now 1000)))
                          vec)]
          (reset! (::control-timestamps connection) recent)
          (if (< (count recent) 4)
            (swap! (::control-timestamps connection) conj now)
            (do
              ((::sleep-fn connection) (max 1 (- 1001 (- now (first recent)))))
              (recur))))))))

(defn- send-map-internal!
  [connection request]
  (let [{:keys [socket status]} @(::state connection)]
    (when-not (= :open status)
      (fail! :transport "WebSocket connection is not open." {:status status}))
    (throttle-control! connection)
    (ws-transport/send-text! (::transport connection)
                             socket
                             (json/write-websocket-json request))))

(defn send!
  "Sends one JSON request map on an open connection."
  [connection request]
  (ensure-connection! connection)
  (when-not (map? request)
    (fail! :configuration "WebSocket request must be a map." {}))
  (send-map-internal! connection request))

(defn- subscription-request
  [connection method streams]
  {:id (next-id connection)
   :method method
   :params (vec streams)})

(defn- restore!
  [connection]
  (let [streams (sort @(::subscriptions connection))]
    (when (seq streams)
      (send-map-internal! connection
                          (subscription-request connection "SUBSCRIBE" streams))))
  (doseq [request ((::restore-requests-fn connection))]
    (send-map-internal! connection request)))

(defn- reconnect-event?
  [event]
  (contains? #{"eventStreamTerminated" "serverShutdown"}
             (:event-type event)))

(defn- receive-text!
  [connection text]
  (try
    (let [parsed (json/read-json text)
          event ((::normalizer connection) parsed)]
      ((::message-observer connection) event)
      (buffer/offer! (::buffer connection) event)
      (when (reconnect-event? event)
        (schedule-reconnect! connection true)))
    (catch Throwable throwable
      (buffer/offer!
       (::buffer connection)
       {:error-category (or (errors/error-category throwable) :api)
        :event-type :malformed-message
        :kind :malformed}))))

(defn- callbacks
  [connection generation]
  {:on-open (fn [_socket]
              (swap! (::state connection)
                     #(if (= generation (:generation %))
                        (assoc % :handshake-open? true)
                        %)))
   :on-text #(receive-text! connection %)
   :on-ping (fn [_]
              (swap! (::state connection)
                     #(if (= generation (:generation %))
                        (assoc % :last-ping-at ((::clock connection)))
                        %)))
   :on-pong (fn [_]
              (swap! (::state connection)
                     #(if (= generation (:generation %))
                        (assoc % :last-pong-at ((::clock connection)))
                        %)))
   :on-close (fn [status-code _reason]
               (when (and (= generation (:generation @(::state connection)))
                          (not (:closed? @(::state connection))))
                 (swap! (::state connection)
                        assoc
                        :close-status status-code
                        :status :disconnected)
                 (schedule-reconnect! connection false)))
   :on-error (fn [throwable]
               (when (and (= generation (:generation @(::state connection)))
                          (not (:closed? @(::state connection))))
                 (swap! (::state connection)
                        assoc
                        :last-error-category
                        (or (errors/error-category throwable) :transport)
                        :status :disconnected)
                 (schedule-reconnect! connection false)))})

(defn- schedule-renewal!
  [connection generation]
  ((:schedule! (::scheduler connection))
   (::renewal-ms connection)
   (fn []
     (when (and (= generation (:generation @(::state connection)))
                (not (:closed? @(::state connection))))
       (schedule-reconnect! connection true)))))

(defn- schedule-heartbeat-check!
  ([connection generation]
   (schedule-heartbeat-check! connection generation (::heartbeat-timeout-ms connection)))
  ([connection generation delay-ms]
   ((:schedule! (::scheduler connection))
    delay-ms
    (fn []
      (let [state @(::state connection)]
        (when (and (= generation (:generation state))
                   (= :open (:status state))
                   (not (:closed? state)))
          (let [now ((::clock connection))
                last-activity (max (or (:last-opened-at state) 0)
                                   (or (:last-ping-at state) 0))
                elapsed (max 0 (- now last-activity))]
            (if (>= elapsed (::heartbeat-timeout-ms connection))
              (schedule-reconnect! connection true)
              (schedule-heartbeat-check!
               connection
               generation
               (max 1 (- (::heartbeat-timeout-ms connection) elapsed)))))))))))

(defn- backoff-delay-ms
  [connection attempt]
  (let [base (::reconnect-base-ms connection)
        maximum (::reconnect-max-ms connection)
        exponential (min maximum (*' base (bit-shift-left 1 (dec attempt))))
        jitter-upper (quot exponential 4)]
    (+ exponential ((::jitter-fn connection) jitter-upper))))

(defn- schedule-reconnect!
  [connection immediate?]
  (let [scheduled?
        (atom false)
        next-state
        (swap! (::state connection)
               (fn [state]
                 (if (or (:closed? state) (:reconnect-scheduled? state))
                   state
                   (let [attempt (inc (:reconnect-attempt state))]
                     (reset! scheduled? true)
                     (assoc state
                            :reconnect-attempt attempt
                            :reconnect-scheduled? true
                            :status :reconnecting)))))]
    (if @scheduled?
      (let [delay-ms (if immediate?
                       0
                       (backoff-delay-ms connection (:reconnect-attempt next-state)))]
        ((:schedule! (::scheduler connection))
         delay-ms
         (fn []
           (try
             (connect-internal! connection false)
             (catch Throwable _))))
        true)
      false)))

(defn- connect-internal!
  [connection propagate?]
  (locking connection
    (let [before @(::state connection)]
      (when (:closed? before)
        (fail! :client-closed "WebSocket connection is closed." {}))
      (let [generation (inc (:generation before))
            previous (:socket before)]
        (swap! (::state connection)
               assoc
               :generation generation
               :handshake-open? false
               :reconnect-scheduled? false
               :socket nil
               :status :connecting)
        (when previous
          (ws-transport/close-socket (::transport connection) previous))
        (try
          (let [socket (ws-transport/open! (::transport connection)
                                           (::url connection)
                                           (callbacks connection generation))]
            (swap! (::state connection)
                   assoc
                   :last-opened-at ((::clock connection))
                   :reconnect-attempt 0
                   :socket socket
                   :status :open)
            (restore! connection)
            (schedule-renewal! connection generation)
            (schedule-heartbeat-check! connection generation)
            true)
          (catch Throwable throwable
            (swap! (::state connection)
                   assoc
                   :last-error-category
                   (or (errors/error-category throwable) :transport)
                   :status :disconnected)
            (schedule-reconnect! connection false)
            (when propagate? (throw throwable))
            false))))))

(defn create-connection
  "Creates a disconnected, shareable WebSocket manager.

  `restore-requests-fn` is called after every successful connection, enabling
  freshly timestamped/signed User Data Stream subscription requests."
  [{:keys [buffer-capacity clock heartbeat-timeout-ms jitter-fn message-observer
           monotonic-clock normalizer
           overflow-policy reconnect-base-ms reconnect-max-ms renewal-ms
           restore-requests-fn scheduler sleep-fn transport url]
    :or {buffer-capacity 1024
         clock #(System/currentTimeMillis)
         heartbeat-timeout-ms default-heartbeat-timeout-ms
         jitter-fn (fn [upper] (if (pos? upper) (rand-int (inc upper)) 0))
         message-observer (constantly nil)
         monotonic-clock #(quot (System/nanoTime) 1000000)
         normalizer identity
         overflow-policy :drop-oldest
         reconnect-base-ms 250
         reconnect-max-ms 30000
         renewal-ms default-renewal-ms
         restore-requests-fn (constantly [])
         sleep-fn #(Thread/sleep %)}}]
  (when-not (and (string? url)
                 (fn? clock)
                 (fn? jitter-fn)
                 (fn? message-observer)
                 (fn? monotonic-clock)
                 (fn? normalizer)
                 (fn? restore-requests-fn)
                 (fn? sleep-fn))
    (fail! :configuration "WebSocket manager options are invalid." {}))
  (when-not (and (integer? reconnect-base-ms) (pos? reconnect-base-ms)
                 (integer? reconnect-max-ms) (<= reconnect-base-ms reconnect-max-ms)
                 (integer? heartbeat-timeout-ms) (pos? heartbeat-timeout-ms)
                 (integer? renewal-ms) (pos? renewal-ms))
    (fail! :configuration "WebSocket reconnect and renewal timings are invalid." {}))
  (let [selected-transport (or transport (ws-transport/create-transport))
        selected-scheduler (or scheduler (daemon-scheduler))]
    (when-not (ws-transport/transport? selected-transport)
      (fail! :configuration "Invalid WebSocket transport." {}))
    (when-not (and (map? selected-scheduler)
                   (fn? (:schedule! selected-scheduler)))
      (fail! :configuration "Invalid WebSocket scheduler." {}))
    {::type connection-type
     ::buffer (buffer/create-buffer {:capacity buffer-capacity
                                     :overflow-policy overflow-policy})
     ::clock clock
     ::control-lock (Object.)
     ::control-timestamps (atom [])
     ::heartbeat-timeout-ms heartbeat-timeout-ms
     ::jitter-fn jitter-fn
     ::message-observer message-observer
     ::monotonic-clock monotonic-clock
     ::normalizer normalizer
     ::reconnect-base-ms reconnect-base-ms
     ::reconnect-max-ms reconnect-max-ms
     ::renewal-ms renewal-ms
     ::request-id (AtomicLong. 0)
     ::restore-requests-fn restore-requests-fn
     ::scheduler selected-scheduler
     ::sleep-fn sleep-fn
     ::state (atom {:closed? false
                    :generation 0
                    :reconnect-attempt 0
                    :reconnect-scheduled? false
                    :status :new})
     ::subscriptions (atom #{})
     ::transport selected-transport
     ::url url}))

(defn connect!
  "Opens the connection synchronously and restores desired subscriptions."
  [connection]
  (ensure-connection! connection)
  (connect-internal! connection true))

(defn renew!
  "Schedules an immediate planned reconnect using the normal restore path."
  [connection]
  (ensure-connection! connection)
  (when (:closed? @(::state connection))
    (fail! :client-closed "WebSocket connection is closed." {}))
  (schedule-reconnect! connection true))

(defn subscribe!
  "Adds a market stream once and subscribes immediately when connected."
  [connection stream]
  (ensure-connection! connection)
  (when-not (and (string? stream) (not (str/blank? stream)))
    (fail! :validation "Stream name must be a non-blank string." {:field :stream}))
  (let [overflow? (atom false)
        [before _after]
        (swap-vals! (::subscriptions connection)
                    (fn [streams]
                      (if (or (contains? streams stream)
                              (< (count streams) maximum-streams))
                        (conj streams stream)
                        (do (reset! overflow? true) streams))))]
    (when @overflow?
      (fail! :validation "A connection cannot exceed 1024 streams."
             {:field :stream}))
    (let [added? (not (contains? before stream))]
      (when (and added? (connected? connection))
        (send-map-internal! connection
                            (subscription-request connection "SUBSCRIBE" [stream])))
      added?)))

(defn unsubscribe!
  "Removes a desired market stream and sends UNSUBSCRIBE when connected."
  [connection stream]
  (ensure-connection! connection)
  (let [[before _after] (swap-vals! (::subscriptions connection) disj stream)
        removed? (contains? before stream)]
    (when (and removed? (connected? connection))
      (send-map-internal! connection
                          (subscription-request connection "UNSUBSCRIBE" [stream])))
    removed?))

(defn poll-event!
  "Returns the next buffered event, or nil after timeout-ms."
  ([connection] (poll-event! connection 0))
  ([connection timeout-ms]
   (ensure-connection! connection)
   (buffer/poll! (::buffer connection) timeout-ms)))

(defn snapshot
  "Returns safe connection, subscription, and backpressure state."
  [connection]
  (ensure-connection! connection)
  (let [state @(::state connection)]
    (merge (select-keys state
                        [:closed? :close-status :generation :handshake-open?
                         :last-error-category :last-opened-at :last-ping-at
                         :last-pong-at :reconnect-attempt :status])
           {:buffer (buffer/snapshot (::buffer connection))
            :subscriptions (sort @(::subscriptions connection))})))

(defn close!
  "Closes the socket, scheduler, and transport idempotently."
  [connection]
  (ensure-connection! connection)
  (let [[before _after] (swap-vals! (::state connection)
                                    #(assoc % :closed? true :status :closed))]
    (if (:closed? before)
      false
      (do
        (when-let [socket (:socket before)]
          (ws-transport/close-socket (::transport connection) socket))
        (when-let [close-scheduler (:close! (::scheduler connection))]
          (close-scheduler))
        (ws-transport/close-transport! (::transport connection))
        true))))
