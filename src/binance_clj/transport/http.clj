(ns binance-clj.transport.http
  "JDK HttpClient-based Binance REST transport with safety-aware retries."
  (:require [binance-clj.encoding :as encoding]
            [binance-clj.errors :as errors]
            [binance-clj.json :as json]
            [binance-clj.rate-limit :as rate-limit]
            [binance-clj.transport :as transport]
            [clojure.string :as str])
  (:import [java.io IOException]
           [java.net URI]
           [java.net.http HttpClient HttpClient$Redirect HttpRequest
            HttpRequest$BodyPublishers HttpResponse HttpResponse$BodyHandlers
            HttpTimeoutException]
           [java.nio.charset StandardCharsets]
           [java.time Duration]))

(def ^:private supported-methods
  #{:delete :get :post :put})

(def ^:private auth-error-codes
  #{-1022 -2014 -2015})

(def ^:private rate-limit-error-codes
  #{-1003 -1015})

(def ^:private unknown-execution-codes
  #{-1006 -1007})

(defn- fail!
  [category message data]
  (throw (errors/connector-error category message data)))

(defn- request-query
  [{:keys [params security signed-query]}]
  (if (= :signed security)
    (if (string? signed-query)
      signed-query
      (fail! :auth "Signed REST request is missing its signed query." {}))
    (encoding/canonical-query (or params {}))))

(defn- request-headers
  [{:keys [api-key headers security time-unit]}]
  (when-not (or (nil? headers) (map? headers))
    (fail! :configuration "Request headers must be a map." {:field :headers}))
  (when (and (not= :none security) (str/blank? api-key))
    (fail! :auth "Authenticated endpoint requires an API key." {}))
  (merge {"Accept" "application/json"}
         (when (= :microsecond time-unit)
           {"X-MBX-TIME-UNIT" "MICROSECOND"})
         (when-not (= :none security)
           {"X-MBX-APIKEY" api-key})
         headers))

(defn build-http-request
  "Builds the exact JDK request from an abstract connector request map."
  [{:keys [base-url method path request-timeout-ms] :as request}]
  (when-not (contains? supported-methods method)
    (fail! :configuration "HTTP request method is unsupported." {:method method}))
  (when-not (and (string? base-url) (string? path))
    (fail! :configuration "HTTP request requires base-url and path." {}))
  (when-not (and (integer? request-timeout-ms) (pos? request-timeout-ms))
    (fail! :configuration "HTTP request timeout must be a positive integer." {}))
  (let [query (request-query request)
        uri (URI/create
             (str (str/replace base-url #"/+$" "")
                  path
                  (when-not (str/blank? query) (str "?" query))))]
    (when-not (= "https" (str/lower-case (.getScheme uri)))
      (fail! :configuration "REST transport only permits HTTPS requests." {}))
    (let [builder (-> (HttpRequest/newBuilder uri)
                      (.timeout (Duration/ofMillis request-timeout-ms))
                      (.method (str/upper-case (name method))
                               (HttpRequest$BodyPublishers/noBody)))]
      (doseq [[header value] (request-headers request)]
        (.header builder (str header) (str value)))
      (.build builder))))

(defn- jdk-send
  [^HttpClient client ^HttpRequest request]
  (let [^HttpResponse response
        (.send client request (HttpResponse$BodyHandlers/ofString StandardCharsets/UTF_8))]
    {:status (.statusCode response)
     :headers (into {} (.map (.headers response)))
     :body (.body response)}))

(defn- validate-raw-response
  [raw]
  (when-not (and (map? raw)
                 (integer? (:status raw))
                 (<= 100 (:status raw) 599)
                 (map? (:headers raw))
                 (or (nil? (:body raw)) (string? (:body raw))))
    (fail! :transport "HTTP sender returned an invalid response contract." {}))
  raw)

(defn- success-status?
  [status]
  (<= 200 status 299))

(defn- server-error?
  [status]
  (<= 500 status 599))

(defn- error-json
  [body]
  (try
    (json/read-json body)
    (catch clojure.lang.ExceptionInfo _ nil)))

(defn- response-error-category
  [request status code]
  (cond
    (or (contains? #{418 429} status)
        (contains? rate-limit-error-codes code)) :rate-limit
    (and (= :command (:execution request))
         (or (server-error? status)
             (contains? unknown-execution-codes code))) :unknown-execution
    (or (= 401 status) (contains? auth-error-codes code)) :auth
    (= -1007 code) :timeout
    :else :api))

(defn- response-error-reason
  [code message]
  (let [normalized-message (some-> message str/lower-case)]
    (cond
      (and (= -1013 code)
           (string? message)
           (re-matches #"(?i)^Filter failure: [A-Z0-9_]+$" message)) :filter-failure
      (= -1100 code) :illegal-characters
      (= -2015 code) :api-key-ip-or-permission-rejected
      (= -2014 code) :api-key-format-invalid
      (= -2013 code) :order-not-found
      (= -2011 code) :cancel-rejected
      (and (= -2010 code)
           (string? normalized-message)
           (str/includes? normalized-message "insufficient balance")) :insufficient-balance
      (and (= -2010 code)
           (= "duplicate order sent." normalized-message)) :duplicate-order
      (and (= -2010 code)
           (= "order would immediately match and take." normalized-message))
      :would-immediately-match
      (and (= -2010 code)
           (= "order would trigger immediately." normalized-message))
      :would-trigger-immediately
      (= -2010 code) :new-order-rejected
      (= -1022 code) :invalid-signature
      (= -1021 code) :invalid-timestamp
      :else nil)))

(defn- response-filter-name
  [code message]
  (when (and (= -1013 code) (string? message))
    (some-> (re-matches #"(?i)^Filter failure: ([A-Z0-9_]+)$" message)
            second
            str/upper-case)))

(defn- throw-response-error!
  [request status headers attempts]
  (let [parsed (error-json (:body request))
        code (when (map? parsed) (:code parsed))
        message (when (map? parsed) (:msg parsed))
        rate-data (rate-limit/parse-headers headers)
        category (response-error-category request status code)
        reason (response-error-reason code message)
        filter-name (response-filter-name code message)]
    (fail! category
           (case category
             :rate-limit "Binance rate limit rejected the request."
             :unknown-execution "Command execution status is unknown."
             :auth "Binance rejected request authentication."
             :timeout "Binance timed out while processing the request."
             "Binance API request failed.")
           (cond-> {:attempts attempts
                    :binance-code code
                    :binance-reason reason
                    :http-status status
                    :rate-limits rate-data
                    :retry-after-seconds (:retry-after-seconds rate-data)}
             filter-name (assoc :binance-filter filter-name)))))

(defn- retryable-status?
  [request status retry-after-seconds attempt max-read-retries]
  (and (= :safe (:retry-policy request))
       (<= attempt max-read-retries)
       (or (server-error? status)
           (and (= 429 status) (some? retry-after-seconds)))))

(defn- retryable-network?
  [request attempt max-read-retries]
  (and (= :safe (:retry-policy request))
       (<= attempt max-read-retries)))

(defn- retry-delay-ms
  [status retry-after-seconds attempt retry-base-delay-ms]
  (if (and (= 429 status) (some? retry-after-seconds))
    (* retry-after-seconds 1000)
    (*' retry-base-delay-ms (bit-shift-left 1 (dec attempt)))))

(defn- throw-network-error!
  [request throwable category attempts]
  (let [final-category (if (= :command (:execution request))
                         :unknown-execution
                         category)]
    (fail! final-category
           (if (= :unknown-execution final-category)
             "Command execution status is unknown."
             "HTTP transport operation failed.")
           {:attempts attempts
            :cause-class (some-> throwable class .getName)})))

(defrecord JdkHttpTransport
           [client send-fn sleep-fn max-read-retries retry-base-delay-ms
            rate-limit-tracker closed?]
  transport/Transport
  (send-request! [_ request]
    (when @closed?
      (fail! :client-closed "HTTP transport is closed." {}))
    (let [http-request (build-http-request request)]
      (letfn [(execute-attempt! [attempt]
                (rate-limit/record-attempt! rate-limit-tracker request)
                (try
                  (let [raw (validate-raw-response (send-fn client http-request))
                        status (:status raw)
                        headers (rate-limit/normalize-headers (:headers raw))
                        rate-data (rate-limit/parse-headers headers)]
                    (rate-limit/record-response! rate-limit-tracker headers)
                    (if (retryable-status? request
                                           status
                                           (:retry-after-seconds rate-data)
                                           attempt
                                           max-read-retries)
                      (do
                        (sleep-fn (retry-delay-ms status
                                                  (:retry-after-seconds rate-data)
                                                  attempt
                                                  retry-base-delay-ms))
                        (execute-attempt! (inc attempt)))
                      (if (success-status? status)
                        (transport/response
                         (json/read-json (:body raw))
                         {:attempts attempt
                          :headers headers
                          :http-status status
                          :rate-limits rate-data
                          :rate-limit-state (rate-limit/snapshot rate-limit-tracker)})
                        (throw-response-error! (assoc request :body (:body raw))
                                               status
                                               headers
                                               attempt))))
                  (catch HttpTimeoutException throwable
                    (if (retryable-network? request attempt max-read-retries)
                      (do
                        (sleep-fn (retry-delay-ms nil nil attempt retry-base-delay-ms))
                        (execute-attempt! (inc attempt)))
                      (throw-network-error! request throwable :timeout attempt)))
                  (catch IOException throwable
                    (if (retryable-network? request attempt max-read-retries)
                      (do
                        (sleep-fn (retry-delay-ms nil nil attempt retry-base-delay-ms))
                        (execute-attempt! (inc attempt)))
                      (throw-network-error! request throwable :transport attempt)))
                  (catch InterruptedException throwable
                    (.interrupt (Thread/currentThread))
                    (throw-network-error! request throwable :transport attempt))
                  (catch clojure.lang.ExceptionInfo throwable
                    (if (errors/normalized-error? throwable)
                      (throw throwable)
                      (throw-network-error! request throwable :transport attempt)))
                  (catch RuntimeException throwable
                    (throw-network-error! request throwable :transport attempt))))]
        (execute-attempt! 1))))

  (close-transport! [_]
    (when (compare-and-set! closed? false true)
      (.close ^HttpClient client))))

(defn create-transport
  "Creates a reusable JDK HTTP transport without opening a network connection."
  ([] (create-transport {}))
  ([{:keys [connect-timeout-ms max-read-retries rate-limit-tracker
            retry-base-delay-ms send-fn sleep-fn]
     :or {connect-timeout-ms 10000
          max-read-retries 2
          retry-base-delay-ms 200
          send-fn jdk-send
          sleep-fn #(Thread/sleep %)}}]
   (when-not (and (integer? connect-timeout-ms) (pos? connect-timeout-ms))
     (fail! :configuration "connect-timeout-ms must be a positive integer." {}))
   (when-not (and (integer? max-read-retries) (<= 0 max-read-retries 10))
     (fail! :configuration "max-read-retries must be an integer from 0 to 10." {}))
   (when-not (and (integer? retry-base-delay-ms) (pos? retry-base-delay-ms))
     (fail! :configuration "retry-base-delay-ms must be a positive integer." {}))
   (when-not (and (fn? send-fn) (fn? sleep-fn))
     (fail! :configuration "HTTP send and sleep boundaries must be functions." {}))
   (let [tracker (or rate-limit-tracker (rate-limit/create-tracker))]
     (when-not (rate-limit/tracker? tracker)
       (fail! :configuration "Invalid rate-limit tracker." {}))
     (->JdkHttpTransport
      (-> (HttpClient/newBuilder)
          (.connectTimeout (Duration/ofMillis connect-timeout-ms))
          (.followRedirects HttpClient$Redirect/NEVER)
          (.build))
      send-fn
      sleep-fn
      max-read-retries
      retry-base-delay-ms
      tracker
      (atom false)))))

(defn rate-limit-snapshot
  "Returns a non-sensitive snapshot from a concrete HTTP transport."
  [http-transport]
  (when-not (instance? JdkHttpTransport http-transport)
    (fail! :configuration "Expected a JDK HTTP transport." {}))
  (rate-limit/snapshot (:rate-limit-tracker http-transport)))
