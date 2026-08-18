# Getting Started and Usage Guide

> English counterpart of [GETTING_STARTED.md](GETTING_STARTED.md). | [Türkçe](GETTING_STARTED.md)

This guide covers the recommended path from a clean installation to safe Spot Testnet use. Before production use, read [DISCLAIMER.en.md](../DISCLAIMER.en.md), [SECURITY.en.md](../SECURITY.en.md), and [KNOWN_LIMITATIONS.en.md](KNOWN_LIMITATIONS.en.md).

## 1. What Am I Installing?

`binance-clj` is not a bot or trading strategy. It is the connector/SDK layer that communicates with Binance Spot:

```text
Your application / REPL
        ↓
binance-clj public API
        ↓
validation → timestamp → signing → transport → parsing
        ↓
Binance Spot REST / WebSocket
```

The connector provides public market data, signed account data, manual MARKET/LIMIT orders, and real-time streams. Strategy, risk decisions, position sizing, and persistent business state belong to the calling application.

## 2. Prerequisites

Check from PowerShell:

```powershell
java -version
clojure -Sdescribe
git --version
```

Target environment:

- Java/JDK 25
- Clojure CLI
- Clojure runtime 1.12.5, pinned in `deps.edn`

If JDK 25 is unavailable on Windows:

```powershell
.\scripts\install-portable-jdk.ps1
```

The script installs a repository-local Temurin JDK under `.toolchains/`; Git ignores that directory.

## 3. Clone and Initial Verification

```powershell
git clone https://github.com/ugurbay/clojure-binance-connector.git
cd clojure-binance-connector
clojure -M:verify-environment
clojure -M:run
```

Expected smoke output:

```clojure
{:name "binance-clj"
 :phase 9
 :status :ready
 :clojure-version "1.12.5"
 :java-version "25..."}
```

Then run the offline gate:

```powershell
.\scripts\verify-phase-9.ps1
```

This command places no real order. It runs unit/contract tests, offline integration tests, the secret scan, lint, formatting checks, benchmarks, and a short soak test.

## 4. Public Testnet Client

Start a REPL:

```powershell
clojure -M:dev
```

```clojure
(require '[binance-clj.client :as client]
         '[binance-clj.spot :as spot])

(def connector
  (client/create-client {:environment :testnet}))
```

Creating a client does not make a network call. The first endpoint call uses the transport.

```clojure
(spot/ping connector)
(spot/server-time connector)
(spot/exchange-info connector {:symbol "BTCUSDT"})
(spot/ticker-price connector "BTCUSDT")
(spot/ticker-24h connector {:symbol "BTCUSDT" :type :mini})
(spot/book-ticker connector "BTCUSDT")
(spot/depth connector "BTCUSDT" {:limit 100})
```

When finished:

```clojure
(client/close! connector)
```

`close!` is idempotent and safe to call more than once.

## 5. Result Envelope

Successful calls share this shape:

```clojure
{:ok? true
 :data <normalized-Binance-response>
 :metadata {:endpoint-id :spot/time
            :environment :testnet
            :http-status 200
            :attempts 1
            :rate-limits {:request-weight {...}
                          :orders {...}
                          :retry-after-seconds nil}}}
```

Known financial response fields become `BigDecimal`; timestamp and ID fields become integers. Unknown response fields are preserved for forward compatibility.

## 6. Prepare Testnet Credentials

Create an API key on the Spot Test Network and set it only for the current PowerShell process:

```powershell
$env:BINANCE_API_KEY='testnet-key'
$env:BINANCE_API_SECRET='testnet-secret'
```

Check presence without printing the value:

```powershell
if ($env:BINANCE_API_KEY) { 'API key is ready' }
if ($env:BINANCE_API_SECRET) { 'API secret is ready' }
```

The connector does not automatically load `.env`. Your application must explicitly pass environment or secret-manager values into the configuration map.

## 7. Signed Client and Time Synchronization

```clojure
(def connector
  (client/create-client
   {:environment :testnet
    :credentials {:api-key (System/getenv "BINANCE_API_KEY")
                  :api-secret (System/getenv "BINANCE_API_SECRET")}}))

(client/synchronize-time! connector)
```

Synchronize time before a signed workflow and periodically for long-lived clients. Client creation still performs no hidden network request.

```clojure
(spot/account connector {:omit-zero-balances? true})
(spot/my-trades connector {:symbol "BTCUSDT" :limit 100})
(spot/open-orders connector "BTCUSDT")
```

## 8. Fetch Current Symbol Filters

Do not validate orders with stale or hard-coded filters:

```clojure
(def symbol-info
  (get-in (spot/exchange-info connector {:symbol "BTCUSDT"})
          [:data :symbols 0]))
```

`PRICE_FILTER`, `LOT_SIZE`, and notional rules can change. Binance makes the final decision on the wire.

## 9. Non-Executing Test Order

```clojure
(spot/test-order
 connector
 symbol-info
 {:symbol "BTCUSDT"
  :side :buy
  :type :limit
  :time-in-force :gtc
  :quantity 0.001M
  :price 10000M})
```

`test-order` calls signed `POST /api/v3/order/test` but leaves no live order in the matching engine. It still requires TRADE permission and a valid signature.

## 10. New Order and Lifecycle

For a real Testnet or production order, use the high-level API:

```clojure
(def lifecycle
  (spot/submit-order!
   connector
   symbol-info
   {:symbol "BTCUSDT"
    :side :buy
    :type :limit
    :time-in-force :gtc
    :quantity 0.001M
    :price 10000M}
   {:reconciliation-policy
    {:max-query-attempts 5
     :query-delay-ms 250
     :max-query-delay-ms 2000}}))
```

| State | Meaning | Caller action |
|---|---|---|
| `:confirmed` | Definitive command success | Process order ID/status |
| `:reconciled` | Initial result unknown; query/UDS observed the order | Process observed order state |
| `:rejected` | Definitive validation/API rejection | Correct the cause; do not reuse the same ID |
| `:unresolved` | Query budget could not prove the outcome | Do not POST again; continue through REST/UDS |

Critical rule: never blindly resend the same business order after a timeout or relevant `5xx` response.

## 11. Query and Cancel

```clojure
(spot/query-order connector
                  {:symbol "BTCUSDT"
                   :original-client-order-id "your-client-order-id"})

(spot/cancel-order connector
                   {:symbol "BTCUSDT"
                    :order-id 123456789})
```

Cancel is also state-changing. It is not automatically retried after network uncertainty; query final state separately.

## 12. Market WebSocket

```clojure
(require '[binance-clj.spot.streams :as streams])

(def market-stream
  (streams/create-stream connector
                         {:buffer-capacity 2048
                          :overflow-policy :drop-oldest}))

(streams/subscribe! market-stream (streams/book-ticker "BTCUSDT"))
(streams/subscribe! market-stream (streams/partial-depth "BTCUSDT" 20 100))
(streams/connect! market-stream)

(streams/poll-event! market-stream 1000)
(streams/snapshot market-stream)
```

Registering subscriptions before connection helps batch the initial restore. Use snapshots to monitor drops, depth, and reconnect state.

## 13. Signed User Data Stream

```clojure
(require '[binance-clj.spot.user-stream :as user-stream])

(client/synchronize-time! connector)
(def account-stream (user-stream/create-stream connector))
(user-stream/connect! account-stream)

(def event (user-stream/poll-event! account-stream 1000))
(def updated-lifecycle
  (user-stream/reconcile-order lifecycle event))
```

UDS restore creates a fresh timestamp and signature on every new connection. A reconnect can contain an event gap, so reconcile critical order/account state through REST.

## 14. Moving to Production

Production requires two explicit conditions:

```clojure
(client/create-client
 {:environment :production
  :enable-live-trading? true
  :credentials {...}})
```

Minimum production checklist:

- Independently review the code and dependencies.
- Pin a tag or commit.
- Use a separate, restricted production key without withdrawal permission.
- Use an IP allowlist and secret manager.
- Implement position and order limits in the bot layer.
- Test `:unknown`/`:unresolved`, rate-limit, and WebSocket-gap procedures.
- Keep a manual kill switch and Binance UI access ready.
- Start with a small amount and never use funds you cannot afford to lose.

## 15. Remove Credentials

After testing:

```powershell
Remove-Item Env:BINANCE_API_KEY
Remove-Item Env:BINANCE_API_SECRET
```

If a credential was exposed, removing it from the environment is insufficient: revoke or rotate it at Binance.

## 16. Further Reading

- [API Reference](API_REFERENCE.en.md)
- [Troubleshooting](TROUBLESHOOTING.en.md)
- [Architecture Decisions](ARCHITECTURE.en.md)
- [Endpoint Matrix](BINANCE_ENDPOINT_MATRIX.en.md)
- [Known Limitations](KNOWN_LIMITATIONS.en.md)
- [Phase 9 Release Evidence](PHASE_9_EXIT_GATE.en.md)
