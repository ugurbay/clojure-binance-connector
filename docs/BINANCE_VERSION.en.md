# Binance Compatibility Baseline

> English counterpart of [BINANCE_VERSION.md](BINANCE_VERSION.md). | [Türkçe](BINANCE_VERSION.md)

## Review Record

- Review date: **2026-08-18**
- Target: Binance Spot REST, WebSocket Streams, WebSocket API, and User Data Stream
- Default runtime environment: Spot Testnet
- Data format: JSON

Commit IDs below are reproducible references for Phase 1 research. Runtime does not assume a fixed Binance server version: `exchangeInfo`, response headers, and live error responses remain the current server authority.

## Pinned Official Sources

| Source | Reference | Date/version | Role in this project |
|---|---|---|---|
| [Binance Spot API Docs](https://github.com/binance/binance-spot-api-docs/tree/976cc580553890e92031b77306147c0ed1de5a46) | `976cc580553890e92031b77306147c0ed1de5a46` | 2026-08-14 | Primary behavioral source |
| [Binance API Swagger](https://github.com/binance/binance-api-swagger/tree/64b327c0addadd191ca0dca8e6b4cebd8a97c05c) | `64b327c0addadd191ca0dca8e6b4cebd8a97c05c` | v1.22.0, 2024-10-08 | Secondary REST schema check |
| [Binance Python Connectors](https://github.com/binance/binance-connector-python/tree/8fc3614aa480849fee019634e65341481b637c94) | `8fc3614aa480849fee019634e65341481b637c94` | 2026-08-14 | SDK design comparison |
| [Binance Go Connectors](https://github.com/binance/binance-connector-go/tree/553d82485895a6ccabd881e6bf838d384a65fc1a) | `553d82485895a6ccabd881e6bf838d384a65fc1a` | Spot 1.10.0 / common 2.6.0 | SDK cross-check |
| [Binance JavaScript Connectors](https://github.com/binance/binance-connector-js/tree/092e4f289e9047114fb8ec66256510cc207e16bb) | `092e4f289e9047114fb8ec66256510cc207e16bb` | Spot 32.0.1 / common 2.4.5 | SDK cross-check |

## OpenAPI Status

The official Swagger repository's `spot_api.yaml` uses OpenAPI `3.0.2`, document version `1.0`, and repository release v1.22.0. It covers V1 REST paths but does not model dynamic timestamp/signature generation or Spot changes after 2024-10-08 involving User Data Stream, filters, streams, and limits.

OpenAPI is therefore only a shape cross-check. Spot API Docs take precedence for current parameters, weights, errors, and lifecycle behavior. The project will not generate a Clojure client or models from OpenAPI.

## Official Python Spot SDK Status

At the reviewed HEAD:

- `binance-sdk-spot` is version `9.2.0` with a 2026-06-09 package changelog.
- It requires `binance-common` `4.0.0`; monorepo common HEAD is `4.2.0`.
- The repository publishes per-connector packages rather than GitHub Releases.
- REST API, WebSocket API, and WebSocket Streams have separate configuration/lifecycle components.

Generated Python models often type financial fields as `float`. This project studies lifecycle, configuration separation, response/rate-limit envelopes, tests, and examples, but does not copy generated class models or floating-point domain types.

## Official Go and JavaScript Spot SDK Status

- Both are product-oriented, modular, OpenAPI-generated connectors.
- REST, WebSocket API, and WebSocket Streams use separate configuration/lifecycle components.
- Current Spot response models generally preserve financial fields as strings; JavaScript also supports large-integer JSON parsing.
- Retry behavior is client-wide: Go retries `500`–`504` regardless of method; JavaScript retries network/`500`–`504` for `GET`/`DELETE`. Neither rule is copied for trading commands.
- Both WebSocket API signer helpers sort alphabetically and URL-encode. The official raw-UTF-8 non-ASCII WebSocket test vector is authoritative.

See [OFFICIAL_SDK_CROSSCHECK.en.md](OFFICIAL_SDK_CROSSCHECK.en.md).

## Compatibility Refresh Protocol

Before each release candidate and after a Binance source change:

1. Check Spot API Docs `master` HEAD and `CHANGELOG.md`.
2. Check `testnet/CHANGELOG.md` and `testnet/general-info.md`.
3. Review diffs in `rest-api.md`, `filters.md`, `errors.md`, `web-socket-streams.md`, `web-socket-api.md`, and `user-data-stream.md`.
4. Revalidate the V1 endpoint matrix, weights, signing vectors, and stream names.
5. Record new source commits and the review date here.
6. If behavior changed, update the contract test before implementation.

## Phase 9 Release Recheck

On 2026-08-18, Spot API Docs `master` was rechecked at `976cc580553890e92031b77306147c0ed1de5a46`. No new difference changed V1 decisions for new/query/cancel order behavior, execution types, User Data Stream, or Spot Testnet resets and virtual balances.

Full Spot Testnet acceptance passed the same day. A non-marketable BTCUSDT LIMIT derived from current `exchangeInfo` filters was created; observed through REST query/open-orders and UDS `NEW`; cancelled once; and verified as `CANCELED` through UDS/final query with no open order remaining. Public/signed REST, live WebSocket renewal/restore, and exact environment-credential leakage scanning passed together. Runtime closed at Phase 9 / `READY`.
