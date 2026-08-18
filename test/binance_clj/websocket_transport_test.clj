(ns binance-clj.websocket-transport-test
  (:require [binance-clj.transport.websocket]
            [clojure.test :refer [deftest is]])
  (:import [java.net.http WebSocket]
           [java.nio ByteBuffer]
           [java.nio.charset StandardCharsets]
           [java.util.concurrent CompletableFuture]))

(defn- byte-buffer-text
  [^ByteBuffer buffer]
  (let [copy (.duplicate buffer)
        bytes (byte-array (.remaining copy))]
    (.get copy bytes)
    (String. bytes StandardCharsets/UTF_8)))

(defn- socket-harness
  [pongs requests]
  (reify WebSocket
    (abort [_])
    (getSubprotocol [_] "")
    (isInputClosed [_] false)
    (isOutputClosed [_] false)
    (request [this amount]
      (swap! requests conj amount)
      this)
    (sendBinary [this _ _] (CompletableFuture/completedFuture this))
    (sendClose [this _ _] (CompletableFuture/completedFuture this))
    (sendPing [this _] (CompletableFuture/completedFuture this))
    (sendPong [this payload]
      (swap! pongs conj (byte-buffer-text payload))
      (CompletableFuture/completedFuture this))
    (sendText [this _ _] (CompletableFuture/completedFuture this))))

(deftest listener-reassembles-text-and-mirrors-ping-payload-test
  (let [messages (atom [])
        pings (atom [])
        pongs (atom [])
        requests (atom [])
        listener-fn (deref (ns-resolve 'binance-clj.transport.websocket 'listener))
        listener (listener-fn {:on-ping #(swap! pings conj %)
                               :on-text #(swap! messages conj %)})
        socket (socket-harness pongs requests)]
    (.onOpen listener socket)
    (.onText listener socket "{\"part\":" false)
    (.onText listener socket "true}" true)
    (.onPing listener socket
             (ByteBuffer/wrap (.getBytes "binance-ping" StandardCharsets/UTF_8)))
    (is (= ["{\"part\":true}"] @messages))
    (is (= ["binance-ping"] @pongs))
    (is (= [12] @pings))
    (is (= [1 1 1 1] @requests))))
