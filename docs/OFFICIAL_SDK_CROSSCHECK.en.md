# Official Go and JavaScript SDK Cross-Check

> English counterpart of [OFFICIAL_SDK_CROSSCHECK.md](OFFICIAL_SDK_CROSSCHECK.md). | [Türkçe](OFFICIAL_SDK_CROSSCHECK.md)

**Review date:** 2026-08-18
**Result:** Phase 1's core decisions were confirmed; retry classification was tightened by binding it to endpoint execution semantics.

## Reviewed Sources

| SDK | Commit | Spot/common version |
|---|---|---|
| [Binance Go Connector](https://github.com/binance/binance-connector-go/tree/553d82485895a6ccabd881e6bf838d384a65fc1a) | `553d82485895a6ccabd881e6bf838d384a65fc1a` | Spot `1.10.0`, common `2.6.0` |
| [Binance JavaScript Connector](https://github.com/binance/binance-connector-js/tree/092e4f289e9047114fb8ec66256510cc207e16bb) | `092e4f289e9047114fb8ec66256510cc207e16bb` | `@binance/spot` `32.0.1`, `@binance/common` `2.4.5` |

Both repositories are current product-oriented, modular, OpenAPI-generated connector implementations. Older monolithic Node/TypeScript connectors are not current design sources.

## Decision Matrix

| Topic | Go / JavaScript finding | `binance-clj` decision |
|---|---|---|
| Client separation | REST, WebSocket API, and Streams have separate config/lifecycle | **Confirmed:** retain separate boundaries behind one facade |
| Public model | Large generated struct/interface networks | **Not copied:** immutable Clojure maps and small normalizers |
| Financial fields | Price/quantity/balance generally remain strings | **Strengthened:** strings on wire, `BigDecimal` internally/publicly |
| Large integers | JS uses `json-with-bigint`; Go uses `int64`/typed models | IDs/timestamps remain integers and never become financial values |
| REST signing | Serialization and signed payload are produced together | Exact wire-payload and percent-encoding confirmed |
| WS API signing | Both sort parameters alphabetically | Alphabetical ordering confirmed |
| WS Unicode | Common helpers URL-encode | Official raw-UTF-8 vector wins; Phase 3 test protects it |
| Authentication | HMAC, RSA, and Ed25519 paths | HMAC + Ed25519 in V1; RSA remains out of scope |
| Timestamp | SDKs read system time directly | Keep clock injection for deterministic tests and server offset |
| Response metadata | Status, headers, and rate-limit metadata preserved | Normalized metadata envelope confirmed |
| Error model | Shared classes focus on HTTP status | Preserve richer numeric code, status, retry metadata, and unknown execution |
| Reconnect | Registry, `serverShutdown`, reconnect, restore | Confirmed, plus bounded queue, jitter, and explicit-close distinction |
| Default environment | SDKs default to production | Intentionally keep Testnet as the safer default |

## Critical Retry Finding

Go common `2.6.0` retries REST `500`–`504` three times without checking HTTP method. JavaScript common `2.4.5` limits network/`500`–`504` retries to `GET` and `DELETE`. On Binance Spot, `DELETE /api/v3/order` is a state-changing cancel command, so HTTP method alone cannot establish retry safety.

Therefore `binance-clj`:

- never derives retry safety only from `GET`/`POST`/`DELETE`;
- marks every endpoint `:execution :read|:command`;
- requires `:retry-policy :never` for every command; and
- reconciles uncertain create/cancel outcomes through query and UDS.

This expands “do not retry a trading POST” into “do not automatically retry any state-changing trading command.”

## Intentional Differences

1. No generated model network or closed SDK enum design.
2. No production URL default.
3. No fixed/global retry count.
4. No method-based inference of retry safety.
5. No shared REST/WebSocket canonicalizer.
6. Reconnect uses exponential backoff plus jitter, not only a fixed delay.
7. Raw exception/request content is not copied into diagnostics; errors are normalized and secret-safe.

## Final Confirmation

No error required reversing Phase 1 decisions on scope, endpoints, Testnet safety, `BigDecimal`, separate REST/WS signing, current User Data Stream method, metadata envelope, unknown execution, or reconnect/restore. Retry scope was tightened as described and incorporated into the Phase 2 endpoint contract.
