(ns binance-clj.transport
  "Small transport boundary shared by the pure request pipeline and adapters.")

(defprotocol Transport
  "Transport implementations execute abstract request maps and own resources."
  (send-request! [transport request]
    "Executes an abstract connector request and returns an opaque response value.")
  (close-transport! [transport]
    "Releases transport-owned resources."))

(def ^:private response-type
  ::response)

(defn response
  "Creates a transport response envelope consumed by the generic pipeline."
  [body metadata]
  {::type response-type
   :body body
   :metadata metadata})

(defn response?
  "Returns true for normalized transport response envelopes."
  [value]
  (and (map? value) (= response-type (::type value))))

(defn transport?
  "Returns true for protocol implementations or function-map transports."
  [value]
  (or (satisfies? Transport value)
      (and (map? value)
           (fn? (:send! value))
           (or (nil? (:close! value))
               (fn? (:close! value))))))

(defn send!
  "Executes request through a protocol or `{:send! fn}` adapter."
  [transport request]
  (if (satisfies? Transport transport)
    (send-request! transport request)
    ((:send! transport) request)))

(defn close!
  "Closes a protocol or function-map transport; missing function-map close is a no-op."
  [transport]
  (cond
    (nil? transport) nil
    (satisfies? Transport transport) (close-transport! transport)
    (fn? (:close! transport)) ((:close! transport))
    :else nil))
