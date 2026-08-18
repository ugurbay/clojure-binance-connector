# Binance Spot Phase 1 Research

> English counterpart of [BINANCE_RESEARCH.md](BINANCE_RESEARCH.md). | [Türkçe](BINANCE_RESEARCH.md)

## Status

Phase 1 research completed on **2026-08-18** using only official Binance sources. Pinned commits are in [BINANCE_VERSION.en.md](BINANCE_VERSION.en.md), and V1 endpoints are in [BINANCE_ENDPOINT_MATRIX.en.md](BINANCE_ENDPOINT_MATRIX.en.md).

Source priority: official [Spot API Docs](https://github.com/binance/binance-spot-api-docs), official [Spot OpenAPI](https://github.com/binance/binance-api-swagger), then official [connectors](https://github.com/binance/binance-connector-python).

## REST Environments and Transport

Production REST defaults to `https://api.binance.com`; `api-gcp` and `api1`–`api4` are alternatives, with the latter potentially faster but less stable. `https://data-api.binance.vision` is public market data only. Spot Testnet uses `https://testnet.binance.vision/api` or `https://api1.testnet.binance.vision/api`; endpoint paths remain `/api/v3/...`.

- V1 uses JSON, not SBE.
- Time defaults to milliseconds. Binance also supports `X-MBX-TIME-UNIT: MICROSECOND`.
- GET uses query parameters. POST/PUT/DELETE may use query or form body, with query taking precedence for duplicate names.

### V1 Serialization Decision

All signed REST parameters use one deterministic query string. This avoids ambiguity in Binance's delimiter-free query-plus-body signature rule. The signed UTF-8 bytes are exactly the encoded query sent on the wire.

## Security and Signing

Security types are `NONE`, `TRADE`, `USER_DATA`, and `USER_STREAM`. Non-`NONE` REST calls are signed and send the API key in `X-MBX-APIKEY`. TRADE is not enabled by default for a new key.

V1 fully supports HMAC-SHA256, supplies verified Ed25519 signer infrastructure, and leaves RSA as a future extension.

### REST Canonicalization

1. Remove `nil`; convert names and values to Binance wire forms.
2. Serialize finance as plain decimals, without exponent notation or silent rounding.
3. Percent-encode stable `name=value` pairs.
4. Sign encoded payload UTF-8 bytes, including non-ASCII encodings.
5. Emit lowercase hex for HMAC or Base64 for Ed25519; percent-encode the latter in query.
6. Append `signature` last.

Binance does not mandate one REST parameter order, but deterministic order improves testing and observability. Signed and transmitted payloads must match exactly.

### WebSocket API Canonicalization

Remove `signature`, sort parameter names alphabetically, build raw UTF-8 `name=value&...`, and store HMAC hex or asymmetric Base64 in `params.signature`. This is not a URL query. REST percent-encoding is not reused; separate canonicalizers and official vectors test each contract.

## Timestamp and `recvWindow`

Signed requests require millisecond or microsecond `timestamp`. `recvWindow` is always milliseconds but may contain three fractional digits for microsecond precision; default is 5,000 and maximum 60,000. Binance recommends 5,000 or less. Server checks time both at receipt and before forwarding to matching engine; timestamp may be at most one second ahead and must remain within receive window.

V1 keeps 5,000 ms and stores `/time`-measured offset in an injectable clock. `-1021` triggers resynchronization rather than blind trading retry.

## Rate Limits and Response Metadata

`exchangeInfo` reports current `RAW_REQUESTS`, `REQUEST_WEIGHT`, and `ORDERS`. Static weight is planning input; response metadata observes actual use.

- REST weight is IP-based and exposed through `X-MBX-USED-WEIGHT-*`.
- Successful orders may include `X-MBX-ORDER-COUNT-*`; rejected responses need not.
- Unfilled-order count is account-wide across API keys.
- `429` requires backoff; REST `Retry-After` is seconds.
- Repeated violations cause `418` bans ranging from minutes to days.
- WebSocket API `retryAfter` is an epoch timestamp, not REST header semantics.
- Opening WebSocket API costs weight 2; default responses include `rateLimits`.

Since 2026-04-02, selected successful trading calls can have request weight 0 while failures use documented weight. New and cancel order follow this rule. V1 preserves both worst-case static values and headers.

## Errors and Order Safety

REST errors are `{code,msg}`. Message text may change; numeric code is the programmatic foundation.

| Situation | Classification | V1 behavior |
|---|---|---|
| Validation / `-1013` | definitive rejection | Local/API validation error |
| `-1021` | timestamp | Refresh offset; do not repeat trading POST without a new user action |
| `-1022` | signature | Auth/encoding error; no retry |
| `429` | rate limit | Honor `Retry-After` and central limiting |
| `418` | IP ban | Stop until ban expires |
| `-1006`, `-1007` | unknown execution | Reconcile through order query/UDS |
| Trading timeout or relevant `5xx` | unknown execution | Never blindly POST the same order again |
| Other `4xx` | client/API error | Normalize code and safe reason |

Matching-engine timeout after ten seconds can return `-1007` even if the order executed. Each order therefore uses a unique `newClientOrderId`, and uncertainty is resolved through `GET /api/v3/order` or UDS.

## Spot Filters and Financial Correctness

| Filter | Local check |
|---|---|
| `PRICE_FILTER` | min/max and exact `price % tickSize == 0`; zero disables a rule |
| `LOT_SIZE` | min/max and exact quantity step |
| `MARKET_LOT_SIZE` | MARKET-specific min/max/step |
| `MIN_NOTIONAL` | `price * quantity >= minNotional`, respecting market flags and average window |
| `NOTIONAL` | min/max notional and MARKET application flags |

Financial fields are `BigDecimal`; invalid values are never auto-rounded. Tick/step divisibility uses exact remainder, not inferred decimal scale. MARKET notional can depend on a moving reference price, so local preflight is best effort and Binance remains final authority. Unknown filters survive parsing and are never silently labeled fully validated.

## WebSocket Streams

Production uses `wss://stream.binance.com:9443` or port 443; Testnet uses `wss://stream.testnet.binance.vision`. V1 uses combined `/stream` plus live SUBSCRIBE/UNSUBSCRIBE.

- Connections last 24 hours and require planned renewal.
- Server pings every 20 seconds; respond quickly with the same payload. Missing pong for one minute disconnects.
- Incoming control traffic is limited to five messages/second.
- One connection supports up to 1,024 streams; one IP gets 300 connection attempts per five minutes.
- `serverShutdown` triggers prompt reconnect and restore.
- Reconnect uses exponential backoff with jitter; user close does not reconnect.

Removed `!ticker@arr` is replaced by `!miniTicker@arr`, per-symbol `<symbol>@ticker`, `<symbol>@bookTicker`, and top-depth `<symbol>@depth5|10|20` with optional `@100ms`. Mini-ticker arrays include only changed symbols, not a complete snapshot.

## WebSocket API and User Data Stream

Production WebSocket API is `wss://ws-api.binance.com:443/ws-api/v3`; Testnet is `wss://ws-api.testnet.binance.vision/ws-api/v3`. Legacy listen-key REST endpoints were removed on 2026-02-20.

| Method | Key support | V1 decision |
|---|---|---|
| `userDataStream.subscribe.signature` | Signed HMAC/RSA/Ed25519 request | **Primary**, supporting HMAC-default and Ed25519 |
| `session.logon` + `userDataStream.subscribe` | Ed25519 authenticated session only | Secondary/future optimization |

Signature subscription sends `apiKey`, `timestamp`, `signature`, optional `recvWindow`, and costs weight 2. One account subscription is allowed per connection, up to 1,000 active per session and 65,535 over session lifetime. Events use `{subscriptionId,event}`.

V1 normalizes `executionReport`, `outboundAccountPosition`, `balanceUpdate`, and `eventStreamTerminated`; unknown events retain raw metadata. Termination/disconnect/shutdown triggers restore while REST query supports order reconciliation through any gap.

## Spot Testnet

Create keys at `https://testnet.binance.vision/`. Only `/api/*` is supported; balances are virtual and `/sapi/*` is absent. Testnet can reset monthly without notice, preserving keys but deleting orders/history and refreshing balances. It can differ from production and has a separate changelog. Production URLs or credentials never become an automatic fallback.

## Official SDK Analysis

Adopt separate REST/WebSocket lifecycles, a single lazy facade, persistent sessions, explicit close, response/rate-limit envelopes, registries, reconnect restore, contract fixtures, and small runnable examples.

Adapt generated models into immutable Clojure maps and normalizers; replace financial `float` with `BigDecimal`; separate safe reads from command retries; enrich errors with numeric codes, headers, retry data, and unknown execution; isolate callbacks through bounded queues.

Do not copy per-endpoint generated class networks, silent fallback on schema changes, global mutable registries, secret-bearing request logs, or uniform retries across methods.

## Fixed Decisions for Phases 2–8

1. Declarative EDN endpoint registry.
2. Separate REST and WebSocket API signing canonicalizers.
3. Financial JSON strings to `BigDecimal`; timestamps/IDs to integers.
4. Testnet default and dual production-order guard.
5. Trading timeout/`5xx`/`-1006`/`-1007` means unknown execution.
6. Required unique `newClientOrderId`.
7. Central rate state from static weights plus response metadata.
8. Primary UDS path is `userDataStream.subscribe.signature`.
9. No removed `!ticker@arr`; use current streams.
10. No generated OpenAPI or Python model translation.

## Validation Records

Phase 3 reproduced official ASCII and full-width Unicode REST HMAC vectors, the official WebSocket HMAC vector, and RFC 8032 Ed25519 test 1. Signed timestamps, receive-window bounds, and raw-UTF-8 WebSocket behavior are contract-tested.

Phase 4 bound official HTTP/error/limit behavior to tests: `5xx` can be unknown for commands; REST `Retry-After` is seconds; `418` is never automatically retried; response headers populate observation state; one JDK client owns a reusable pool; `data.json :bigdec true` is mandatory.

The Go/JavaScript cross-check confirmed architecture but tightened safety: retry cannot be inferred from HTTP method because cancel is state-changing `DELETE`. Every command uses `:retry-policy :never`.

Phase 8 rechecked the pinned Spot Docs, including 24-hour connections, 20-second ping, one-minute pong deadline, five control messages/second, 1,024 streams, WebSocket shutdown behavior, and current signature-subscription limits. Implementation replies to pong in the transport callback, batches restore, regenerates UDS signatures, and renews at 23h50m. Live credential-free `BTCUSDT@bookTicker` acceptance passed.
