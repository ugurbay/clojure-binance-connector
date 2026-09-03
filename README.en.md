# binance-clj

**English** | [Türkçe](README.md) | [Complete bilingual documentation](docs/README.en.md)

A native, data-oriented Clojure connector for the Binance Spot REST API, market WebSocket streams, WebSocket API, and signed User Data Stream.

**Connector V1 is release-gate complete (`:phase 9`, `:status :ready`).** The final Spot Testnet acceptance covered public and signed REST, live WebSocket renewal/restore, signed user events, and a non-marketable LIMIT lifecycle from creation through cancellation.

[![CI](https://github.com/ugurbay/clojure-binance-connector/actions/workflows/ci.yml/badge.svg)](https://github.com/ugurbay/clojure-binance-connector/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Clojars](https://img.shields.io/clojars/v/io.github.ugurbay/binance-clj.svg)](https://clojars.org/io.github.ugurbay/binance-clj)

> [!WARNING]
> This software can send real orders and may cause financial loss. It is not financial, investment, legal, tax, or accounting advice. Testnet acceptance is not a production guarantee. Read [DISCLAIMER.en.md](DISCLAIMER.en.md) before use.

> [!IMPORTANT]
> This project was developed with substantial assistance from **OpenAI ChatGPT/Codex**, under maintainer direction and acceptance testing. It is not audited, certified, sponsored, or endorsed by OpenAI or Binance. See [NOTICE.en.md](NOTICE.en.md).

## Highlights

- Spot Testnet is the default environment.
- Production trading requires both `:environment :production` and `:enable-live-trading? true`.
- Financial values use `BigDecimal`; floating-point order inputs are rejected.
- Signed REST payloads support HMAC-SHA256; an Ed25519 signer/key loader is included.
- State-changing commands are never retried automatically.
- A timed-out or relevant `5xx` order is treated as unknown, then reconciled by client order ID without resubmission.
- REST rate-limit metadata, `429 Retry-After`, and `418` behavior are normalized.
- Market streams and signed user events support reconnect, planned renewal, subscription restore, and bounded buffering.
- Credentials and signatures are redacted and checked by a repository secret scanner.

## Requirements

- JDK 25
- Clojure CLI
- Git

The project pins Clojure `1.12.5` in `deps.edn`.

## Install from Clojars

Add the library to your Clojure CLI project's `deps.edn`:

```clojure
{:deps
 {io.github.ugurbay/binance-clj {:mvn/version "1.0.3"}}}
```

For Leiningen projects:

```clojure
:dependencies [[io.github.ugurbay/binance-clj "1.0.3"]]
```

See the [Clojars Release Guide](docs/CLOJARS_RELEASE.en.md) for artifact publication and verification.

## Build from Source

```powershell
git clone https://github.com/ugurbay/clojure-binance-connector.git
cd clojure-binance-connector
clojure -M:verify-environment
clojure -X:test
clojure -X:integration-test
```

On Windows, a repository-local Temurin 25 runtime can be installed without replacing the system JDK:

```powershell
.\scripts\install-portable-jdk.ps1
```

## Safe First Connection

Public endpoints do not require credentials:

```clojure
(require '[binance-clj.client :as client]
         '[binance-clj.spot :as spot])

(def connector (client/create-client {:environment :testnet}))

(spot/ping connector)
(spot/server-time connector)
(spot/exchange-info connector {:symbol "BTCUSDT"})
(spot/ticker-price connector "BTCUSDT")
(spot/book-ticker connector "BTCUSDT")
(spot/depth connector "BTCUSDT" {:limit 100})
(spot/klines connector "BTCUSDT" "1d" {:start-time 0 :limit 1})

(client/close! connector)
```

Every successful operation returns an envelope:

```clojure
{:ok? true
 :data {...}
 :metadata {:endpoint-id :spot/time
            :environment :testnet
            :http-status 200
            :attempts 1
            :rate-limits {...}}}
```

## Signed Testnet Usage

Keep credentials in the current process or a secure secret manager. Never commit them.

```powershell
$env:BINANCE_API_KEY='your-testnet-api-key'
$env:BINANCE_API_SECRET='your-testnet-api-secret'
```

```clojure
(def connector
  (client/create-client
   {:environment :testnet
    :credentials {:api-key (System/getenv "BINANCE_API_KEY")
                  :api-secret (System/getenv "BINANCE_API_SECRET")}}))

(client/synchronize-time! connector)

(def symbol-info
  (get-in (spot/exchange-info connector {:symbol "BTCUSDT"})
          [:data :symbols 0]))

(spot/account connector {:omit-zero-balances? true})

;; Validates locally and on Binance, but does not enter the matching engine.
(spot/test-order connector
                 symbol-info
                 {:symbol "BTCUSDT"
                  :side :buy
                  :type :limit
                  :time-in-force :gtc
                  :quantity 0.001M
                  :price 10000M})
```

## Order Safety and Reconciliation

Prefer `spot/submit-order!` over directly calling `spot/new-order`. It sends the new-order command exactly once. If execution becomes unknown, it queries by the preallocated client order ID instead of posting the command again.

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
    :price 10000M}))

(case (:state lifecycle)
  :confirmed  :command-confirmed
  :reconciled :observed-by-query
  :rejected   :definitively-rejected
  :unresolved :manual-or-user-stream-follow-up-required)
```

Never interpret `:unresolved` as “the order does not exist,” and never resubmit the same business order blindly.

## WebSocket Streams

```clojure
(require '[binance-clj.spot.streams :as streams]
         '[binance-clj.spot.user-stream :as user-stream])

(def market-stream (streams/create-stream connector))
(streams/subscribe! market-stream (streams/aggregate-trades "BTCUSDT"))
(streams/subscribe! market-stream (streams/book-ticker "BTCUSDT"))
(streams/connect! market-stream)
(streams/poll-event! market-stream 1000)

(client/synchronize-time! connector)
(def account-stream (user-stream/create-stream connector))
(user-stream/connect! account-stream)
(def event (user-stream/poll-event! account-stream 1000))

;; A later executionReport can resolve an unknown/unresolved lifecycle.
(user-stream/reconcile-order lifecycle event)
```

The default event buffer holds 1024 events and applies `:drop-oldest` on overflow. Consumers must observe dropped-event counters and reconcile critical gaps through REST.

## Production Guard

Production trading remains disabled unless both conditions are present:

```clojure
{:environment :production
 :enable-live-trading? true}
```

Enabling the guard is not a recommendation to trade. Before production use, independently review the implementation, pin a release, restrict the API key, test failure handling, and operate with limits you can afford to lose.

## Verification

```powershell
# Offline release gate; no credentials and no order
.\scripts\verify-phase-9.ps1

# Explicit Spot Testnet release acceptance; creates and cancels one virtual LIMIT order
.\scripts\verify-phase-9.ps1 -RunFullTestnet
```

The final release run passed:

- 103 unit/contract tests, 624 assertions
- 10 offline integration tests, 20 assertions
- 10 full Testnet integration tests, 47 assertions
- Exact scan of two process credential values: zero leaks
- Lint/format clean; bounded soak: zero drops and zero thread growth

## Scope

V1 covers Binance Spot manual trading connectivity. It does not provide strategies, signals, automated portfolio logic, Futures, Margin, Options, wallet transfers, a UI, persistent bot state, or a full local order book.

## Documentation

- [Turkish README](README.md)
- [Complete bilingual documentation index](docs/README.en.md)
- [Getting Started](docs/GETTING_STARTED.en.md)
- [API Reference](docs/API_REFERENCE.en.md)
- [Troubleshooting](docs/TROUBLESHOOTING.en.md)
- [Architecture](docs/ARCHITECTURE.en.md)
- [Endpoint Matrix](docs/BINANCE_ENDPOINT_MATRIX.en.md)
- [Known Limitations](docs/KNOWN_LIMITATIONS.en.md)
- [Security Policy](SECURITY.en.md)
- [Contribution Guide](CONTRIBUTING.en.md)
- [Risk and Legal Disclaimer](DISCLAIMER.en.md)
- [AI and Trademark Notice](NOTICE.en.md)

## License

MIT. See [LICENSE](LICENSE). The license includes warranty and liability limitations; [DISCLAIMER.en.md](DISCLAIMER.en.md) provides additional trading-specific warnings. Limitations apply only to the extent permitted by applicable law.
