# Public API Reference

> English counterpart of [API_REFERENCE.md](API_REFERENCE.md). | [Türkçe](API_REFERENCE.md)

This document summarizes the public namespaces intended for V1 consumers. Source docstrings are authoritative for arity and validation details. Transport, pipeline, and validator namespaces inside `binance-clj` are not part of the public application contract.

## Common Result and Error Model

A successful REST call returns:

```clojure
{:ok? true
 :data <endpoint-response>
 :metadata {:endpoint-id keyword
            :environment :testnet|:production
            :http-status integer
            :attempts integer
            :rate-limits map}}
```

Errors are raised as `ExceptionInfo`. Safe `ex-data` contains a `:binance-clj/error` category:

```clojure
#{:api :auth :client-closed :configuration :rate-limit
  :timeout :transport :unknown-execution :validation}
```

For Binance-originated errors, safe data may include `:binance-code`, an allowlisted `:binance-reason`, `:http-status`, and rate-limit metadata. Raw signed queries, signatures, and credentials are never included.

## `binance-clj.core`

| Function | Description |
|---|---|
| `create-client` | Creates a validated connector client without opening a network connection. |
| `execute!` | Executes a registry endpoint through the pipeline; advanced use. |
| `close!` | Idempotently closes the client and owned transport/WebSocket resources. |
| `closed?` | Returns client closed state. |
| `client-config` | Returns a credential-free configuration view. |
| `synchronize-time!` | Updates the default clock offset from Binance server time. |
| `runtime-info` | Returns phase/status and runtime metadata. |

Most applications should use `binance-clj.client` and `binance-clj.spot`.

## `binance-clj.client`

### `create-client`

```clojure
(client/create-client
 {:environment :testnet
  :credentials {:api-key "..." :api-secret "..."}
  :connect-timeout-ms 10000
  :request-timeout-ms 15000
  :recv-window 5000M
  :max-read-retries 2
  :retry-base-delay-ms 200})
```

| Field | Default | Notes |
|---|---:|---|
| `:environment` | `:testnet` | `:testnet` or `:production` |
| `:enable-live-trading?` | `false` | Must be `true` for production commands |
| `:credentials` | `{}` | `:api-key` plus HMAC `:api-secret` or Ed25519 `:private-key` |
| `:recv-window` | `5000M` | Maximum `60000`, at most 3 decimals |
| `:request-timeout-ms` | `15000` | Positive integer |
| `:max-read-retries` | `2` | Safe reads only; commands are not retried |
| `:time-unit` | `:millisecond` | `:millisecond` or `:microsecond` |

Supported lifecycle helpers are `client/public-config`, `client/close!`, `client/closed?`, `client/synchronize-time!`, and `client/next-client-order-id`.

## `binance-clj.spot` — Public REST

| Function | Example | Binance domain |
|---|---|---|
| `ping` | `(spot/ping c)` | Connectivity test |
| `server-time` | `(spot/server-time c)` | Server timestamp |
| `exchange-info` | `(spot/exchange-info c {:symbol "BTCUSDT"})` | Symbols, filters, and limits |
| `ticker-price` | `(spot/ticker-price c "BTCUSDT")` | Latest price |
| `ticker-24h` | `(spot/ticker-24h c {:symbol "BTCUSDT" :type :mini})` | 24-hour ticker |
| `book-ticker` | `(spot/book-ticker c "BTCUSDT")` | Best bid/ask |
| `depth` | `(spot/depth c "BTCUSDT" {:limit 100})` | Order-book snapshot |
| `klines` | `(spot/klines c "BTCUSDT" "1d" {:start-time 0 :limit 1})` | Candlestick/kline history |

Where supported, multi-symbol calls accept a symbol vector or `{:symbols [...]}`. Parameterless all-market calls may consume high request weight.

`klines` validates the interval using Binance's case-sensitive contract. Optional
`:start-time`, `:end-time`, `:limit`, and `:time-zone` fields are supported. Price,
quantity, and volume positions return as `BigDecimal`; timestamps and trade counts
remain integers.

## `binance-clj.spot` — Signed Account

| Function | Main parameters |
|---|---|
| `account` | Optional `{:omit-zero-balances? true}` |
| `my-trades` | `{:symbol "BTCUSDT"}` plus optional ID/time/limit |
| `query-order` | `:symbol` plus `:order-id` or `:original-client-order-id` |
| `open-orders` | Optional symbol; an all-symbol call costs more |

Call `client/synchronize-time!` before signed workflows.

## `binance-clj.spot` — Trading

### `validate-order`

Pure local validation; no network request:

```clojure
(spot/validate-order symbol-info order)
(spot/validate-order symbol-info market-order {:reference-price 65000M})
```

### `test-order`

Runs local validation plus Binance's non-executing `order/test` endpoint:

```clojure
(spot/test-order connector symbol-info order)
```

### `new-order`

A real state-changing command. Network uncertainty may produce an `:unknown-execution` exception. There is no automatic retry.

### `submit-order!`

Recommended high-level API: one submit followed by bounded query reconciliation.

```clojure
(spot/submit-order! connector symbol-info order
                    {:reconciliation-policy
                     {:max-query-attempts 5
                      :query-delay-ms 250
                      :max-query-delay-ms 2000}})
```

Main lifecycle-map fields are `:state`, `:resolution`, `:client-order-id`, `:symbol`, `:submit-attempts`, `:query-attempts`, `:result` or `:order`, and secret-safe `:events`/`:error`.

### `cancel-order`

```clojure
(spot/cancel-order connector {:symbol "BTCUSDT" :order-id 123})
```

Cancellation is also never automatically retried.

## Order Map Contract

LIMIT:

```clojure
{:symbol "BTCUSDT"
 :side :buy|:sell
 :type :limit
 :time-in-force :gtc|:ioc|:fok
 :quantity 0.001M
 :price 10000M
 :new-client-order-id "optional-unique-id"}
```

MARKET:

```clojure
{:symbol "BTCUSDT"
 :side :buy|:sell
 :type :market
 :quantity 0.001M}
```

or, for supported symbols:

```clojure
{:symbol "BTCUSDT"
 :side :buy
 :type :market
 :quote-order-qty 10M}
```

Market-executing STOP_LOSS for catastrophe protection:

```clojure
{:symbol "BTCUSDT"
 :side :sell
 :type :stop-loss
 :quantity 0.001M
 :stop-price 62000M}
```

`STOP_LOSS` requires `quantity` and `stop-price`; it rejects `price`, `time-in-force`, and `quote-order-qty`. `stop-price` is checked against `PRICE_FILTER`. Because the trigger executes a MARKET order, callers must provide a current `:reference-price` option to `validate-order`, `test-order`, `new-order`, and `submit-order!` whenever market-notional filters apply. Whether the trigger is on the correct side of the current market is a dynamic Binance rule validated by the server.

Financial values may be `BigDecimal`, integers, or strict plain-decimal strings. `float`, `double`, exponent notation, and silent rounding are rejected.

## `binance-clj.spot.streams`

| Helper | Example result |
|---|---|
| `(all-mini-tickers)` | `!miniTicker@arr` |
| `(ticker "BTCUSDT")` | `btcusdt@ticker` |
| `(book-ticker "BTCUSDT")` | `btcusdt@bookTicker` |
| `(partial-depth "BTCUSDT" 20 100)` | `btcusdt@depth20@100ms` |

```clojure
(def s (streams/create-stream connector options))
(streams/subscribe! s stream-name)
(streams/connect! s)
(streams/poll-event! s 1000)
(streams/snapshot s)
(streams/renew! s)
(streams/unsubscribe! s stream-name)
(streams/close! s)
```

Options may include `:buffer-capacity`, `:overflow-policy`, reconnect policy, and test boundaries. Start with defaults and monitor snapshot metrics.

## `binance-clj.spot.user-stream`

```clojure
(def uds (user-stream/create-stream connector))
(user-stream/connect! uds)
(user-stream/poll-event! uds 1000)
(user-stream/snapshot uds)
(user-stream/renew! uds)
(user-stream/unsubscribe! uds)
(user-stream/subscribe! uds)
(user-stream/close! uds)
```

`user-stream/reconcile-order` applies a matching `executionReport` event to an unknown or unresolved lifecycle.

## Decimal and Encoding Helpers

`binance-clj.decimal` provides:

- `parse`: exact decimal parsing;
- `plain-string`: wire text without scientific notation; and
- `normalize`: removes redundant trailing-zero scale without changing the value.

`binance-clj.encoding` contains low-level canonical query/payload tools. Prefer the client pipeline in signed application flows.

## Compatibility and Boundaries

See [BINANCE_ENDPOINT_MATRIX.en.md](BINANCE_ENDPOINT_MATRIX.en.md) for the endpoint/weight/security table and [KNOWN_LIMITATIONS.en.md](KNOWN_LIMITATIONS.en.md) for unknown or unimplemented behavior. Binance's server contract can change; follow [BINANCE_VERSION.en.md](BINANCE_VERSION.en.md) before each release.
