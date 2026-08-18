(ns binance-clj.transport.websocket
  "JDK WebSocket boundary with exact ping/pong handling and text reassembly."
  (:require [binance-clj.errors :as errors]
            [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http HttpClient WebSocket WebSocket$Listener]
           [java.nio ByteBuffer]
           [java.time Duration]
           [java.util.concurrent CompletionException]))

(defprotocol WebSocketTransport
  (open-socket! [transport url callbacks])
  (send-text-frame! [transport socket text])
  (close-socket! [transport socket])
  (close-websocket-transport! [transport]))

(defn transport?
  "Returns true for protocol implementations or injectable function maps."
  [value]
  (or (satisfies? WebSocketTransport value)
      (and (map? value)
           (fn? (:open! value))
           (fn? (:send-text! value))
           (fn? (:close-socket! value)))))

(defn open!
  "Opens a socket through a protocol or injected transport map."
  [transport url callbacks]
  (if (satisfies? WebSocketTransport transport)
    (open-socket! transport url callbacks)
    ((:open! transport) url callbacks)))

(defn send-text!
  "Sends a complete text frame through the transport boundary."
  [transport socket payload]
  (if (satisfies? WebSocketTransport transport)
    (send-text-frame! transport socket payload)
    ((:send-text! transport) socket payload)))

(defn close-socket
  "Closes one socket through the transport boundary."
  [transport socket]
  (if (satisfies? WebSocketTransport transport)
    (close-socket! transport socket)
    ((:close-socket! transport) socket)))

(defn close-transport!
  "Closes the transport and all resources it owns."
  [transport]
  (cond
    (satisfies? WebSocketTransport transport)
    (close-websocket-transport! transport)

    (fn? (:close! transport)) ((:close! transport))
    :else nil))

(defn- fail!
  [category message data]
  (throw (errors/connector-error category message data)))

(defn- valid-url?
  [url]
  (try
    (let [uri (URI/create url)]
      (and (= "wss" (some-> uri .getScheme str/lower-case))
           (some? (.getHost uri))))
    (catch Exception _ false)))

(defn- callback
  [callbacks key & args]
  (when-let [f (get callbacks key)]
    (try
      (apply f args)
      (catch Throwable throwable
        (when (and (not= key :on-error) (fn? (:on-error callbacks)))
          ((:on-error callbacks) throwable))))))

(defn- listener
  [callbacks]
  (let [fragments (StringBuilder.)]
    (reify WebSocket$Listener
      (onOpen [_ socket]
        (.request ^WebSocket socket 1)
        (callback callbacks :on-open socket))

      (onText [_ socket data last?]
        (.append fragments ^CharSequence data)
        (when last?
          (let [message (.toString fragments)]
            (.setLength fragments 0)
            (callback callbacks :on-text message)))
        (.request ^WebSocket socket 1)
        nil)

      (onBinary [_ socket _data _last?]
        (callback callbacks :on-error
                  (errors/connector-error
                   :api
                   "Binary WebSocket frames are unsupported in the JSON connector."
                   {}))
        (.request ^WebSocket socket 1)
        nil)

      (onPing [_ socket payload]
        (let [copy (.duplicate ^ByteBuffer payload)]
          (.sendPong ^WebSocket socket copy)
          (callback callbacks :on-ping (.remaining copy)))
        (.request ^WebSocket socket 1)
        nil)

      (onPong [_ socket payload]
        (callback callbacks :on-pong (.remaining ^ByteBuffer payload))
        (.request ^WebSocket socket 1)
        nil)

      (onClose [_ _socket status-code reason]
        (callback callbacks :on-close status-code reason)
        nil)

      (onError [_ _socket throwable]
        (callback callbacks :on-error throwable)))))

(defrecord JdkWebSocketTransport [^HttpClient client sockets closed?]
  WebSocketTransport
  (open-socket! [_ url callbacks]
    (when @closed?
      (fail! :client-closed "WebSocket transport is closed." {}))
    (when-not (and (string? url) (valid-url? url) (map? callbacks))
      (fail! :configuration "A secure WebSocket URL and callback map are required." {}))
    (try
      (let [socket (-> client
                       (.newWebSocketBuilder)
                       (.buildAsync (URI/create url) (listener callbacks))
                       (.join))]
        (swap! sockets conj socket)
        socket)
      (catch CompletionException throwable
        (throw (errors/normalize (or (.getCause throwable) throwable)
                                 :transport
                                 {:operation :websocket-connect})))
      (catch RuntimeException throwable
        (throw (errors/normalize throwable
                                 :transport
                                 {:operation :websocket-connect})))))

  (send-text-frame! [_ socket text]
    (when @closed?
      (fail! :client-closed "WebSocket transport is closed." {}))
    (when-not (and (instance? WebSocket socket) (string? text))
      (fail! :configuration "WebSocket text send received an invalid value." {}))
    (try
      (-> ^WebSocket socket (.sendText text true) (.join))
      true
      (catch CompletionException throwable
        (throw (errors/normalize (or (.getCause throwable) throwable)
                                 :transport
                                 {:operation :websocket-send})))))

  (close-socket! [_ socket]
    (when socket
      (try
        (-> ^WebSocket socket (.sendClose WebSocket/NORMAL_CLOSURE "") (.join))
        (catch Throwable _
          (.abort ^WebSocket socket))
        (finally
          (swap! sockets disj socket))))
    true)

  (close-websocket-transport! [_]
    (when (compare-and-set! closed? false true)
      (doseq [socket @sockets]
        (try (.abort ^WebSocket socket) (catch Throwable _)))
      (reset! sockets #{})
      (.close client)
      true)))

(defn create-transport
  "Creates a reusable JDK WebSocket transport without opening a connection."
  ([] (create-transport {}))
  ([{:keys [connect-timeout-ms]
     :or {connect-timeout-ms 10000}}]
   (when-not (and (integer? connect-timeout-ms) (pos? connect-timeout-ms))
     (fail! :configuration "WebSocket connect timeout must be a positive integer."
            {:field :connect-timeout-ms}))
   (->JdkWebSocketTransport
    (-> (HttpClient/newBuilder)
        (.connectTimeout (Duration/ofMillis connect-timeout-ms))
        (.build))
    (atom #{})
    (atom false))))
