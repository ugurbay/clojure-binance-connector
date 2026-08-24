# Known Limitations

> English counterpart of [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). | [Türkçe](KNOWN_LIMITATIONS.md)

## V1 Scope Boundaries

- Binance Spot manual-trading connector only.
- Only `BUY`/`SELL` and `MARKET`/`LIMIT` orders.
- No Futures, Margin, Options, OCO/OTO, SOR, wallet transfers/deposits/withdrawals, bot strategy, or UI.
- JSON only; SBE and FIX are unsupported.
- HMAC-SHA256 is fully supported and Ed25519 signer infrastructure is included. RSA is not a public V1 feature.
- The Ed25519 loader accepts only unencrypted PKCS#8 `PRIVATE KEY` PEM or JDK `PrivateKey`. Encrypted PKCS#8, OpenSSH, and raw 32-byte seeds are outside V1.

## Official-Source Limitations

- Official OpenAPI v1.22.0 is dated 2024-10-08 and does not cover all 2026 Spot behavior. It is not the current behavioral or code-generation authority.
- Official Python generated models may use `float` for financial fields and are not the source for Clojure domain types.
- Go and JavaScript SDKs are also closed generated-model networks. Preserving current financial wire fields as strings is a useful check, but their domain models are not copied.
- Go common `2.6.0` and JavaScript common `2.4.5` retry behavior is insufficiently safe for state-changing commands and is not authoritative here.
- Binance error text can change; only numeric code and HTTP status are treated as stable classification inputs.

## Testnet

- Spot Testnet is not always updated at the same time as the live exchange.
- Data may reset roughly monthly without notice, removing order history and open orders.
- Balances are virtual and non-transferable; `/sapi/*` is unsupported.
- Limits and filters are generally production-like but are never hard-coded; `exchangeInfo` is authoritative.
- Network tests remain opt-in. Phase 5 validated seven public REST endpoints without credentials. Phase 6 validated HMAC account access and non-executing `order/test` against real Testnet.

## REST and Trading

- Production `api1`–`api4` alternatives may be less stable; V1 has no automatic host failover.
- Parameterless `ticker/24hr` and `openOrders` can cost weight 80; APIs encourage symbol selection where practical.
- `exchangeInfo` and account/order queries can fall back Memory → Database, causing brief eventual consistency.
- Trading timeout, `-1006`, `-1007`, and relevant `5xx` do not prove success or failure. No automatic resubmit occurs; if bounded query finds nothing, the result stays `unresolved`.
- Cancellation is also state-changing and is not generically retried after network/`5xx` uncertainty.
- New-order request weight and unfilled-order count impact are each 1. For rejected responses without headers, a local order count is only a conservative estimate.
- `STOP_LOSS` receives static `PRICE_FILTER`, `LOT_SIZE`, and market-notional preflight. Whether its trigger is on the valid side of the live market is a matching-engine rule; the connector does not guess it and preserves Binance's rejection.
- V1 sends all REST parameters in the query string and does not expose mixed form-body behavior.
- Binance has no separate `RAW_REQUESTS` response header. The transport tracks actual local wire attempts, while `exchangeInfo` rate-limit data is preserved but not converted into an automatic concurrency budget.
- Valid all-market ticker calls can be expensive. The connector tracks dynamic weight but does not forbid them.
- Safe-read retry backoff is deterministic exponential; a stronger shared concurrency budget may be added after further measurement.
- Time synchronization is explicit through `client/synchronize-time!`, not hidden during creation. Long-lived clients should synchronize periodically and before signed workflows. Custom clocks own their offset management.
- Client-order-ID reuse protection is process-local and lasts for one client lifetime. The application must ensure persistent/global uniqueness across restarts.
- Exhausting reconciliation does not prove that an order was absent. Never POST the same order after `unresolved`; continue through manual/longer query or UDS observation.
- Bounded REST reconciliation is synchronous and holds the caller during delays. A later UDS event can resolve the lifecycle but cannot shorten elapsed time retroactively.

## Filters and Precision

- MARKET `MIN_NOTIONAL`/`NOTIONAL` validation can depend on a moving average/reference price. Binance remains authoritative after any race between local preflight and server evaluation.
- V1 implements only `PRICE_FILTER`, `LOT_SIZE`, `MARKET_LOT_SIZE`, `MIN_NOTIONAL`, and `NOTIONAL`. Other filters are preserved raw without claiming complete local validation.
- `PERCENT_PRICE`, `PERCENT_PRICE_BY_SIDE`, `ICEBERG_PARTS`, `MAX_NUM_ORDERS`, `MAX_NUM_ALGO_ORDERS`, `MAX_NUM_ICEBERG_ORDERS`, `MAX_POSITION`, and exchange/asset filters are outside Phase 6 preflight. Binance can reject an order that passes locally.
- MARKET `quoteOrderQty` is used directly as the local notional estimate. Actual base quantity, average price, and liquidity are determined by the matching engine.
- `symbol-info` is a caller-provided `exchangeInfo` snapshot. There is no hidden cache or refresh; a stale snapshot can produce a stale local result.
- Binance can add filters or enums. Unknown values are preserved, and safe trading validation must treat them explicitly rather than silently accepting them.

## WebSocket

- `!ticker@arr` was removed on 2026-03-26. V1 uses `!miniTicker@arr` for market summaries and per-symbol `<symbol>@ticker` for full tickers.
- `!miniTicker@arr` contains only changed symbols; obtain the initial full snapshot through REST.
- Partial depth covers only the top 5/10/20 levels. V1 does not reconstruct a complete local order book from diff stream plus REST snapshot.
- Connections end after 24 hours; reconnect/restore can produce a short event gap. Reconcile UDS gaps through REST.
- Market-data-only `data-stream.binance.vision` cannot provide User Data Stream.
- Connections are shared at connector level rather than opened per UI/consumer.
- The bounded event buffer defaults to 1,024 items and `drop-oldest`; `drop-newest` is optional. Neither blocks the network callback, so overflow loses events. Monitor drops and reconcile critical gaps through REST.
- JSON streams are supported; SBE binary market/account sessions are outside V1.
- One manager sends control requests and batches restore. Avoid heavy manual subscribe/unsubscribe traffic that could exceed Binance's five incoming messages per second per connection.

## Integration Points Not Yet Validated Live

- Live Ed25519 signed `order/test` acceptance; HMAC acceptance passed, but there is no Ed25519 live harness yet.
- Exchange visibility duration after a real command timeout/`5xx`; Phase 7 verifies deterministic simulation, but intentionally inducing an uncertain command is outside safe live acceptance.
- Restore and event-gap observation during a real network outage; deterministic forced-close/restore testing is complete.

These are credential-dependent integration acceptance items, not unresolved Phase 1 protocol questions.

HMAC `userDataStream.subscribe.signature`, real Testnet `FILLED executionReport`, and `outboundAccountPosition` were validated live in Phase 8. Non-marketable LIMIT create/query/open-orders/cancel, UDS `NEW`/`CANCELED`, final query, and absence of open orders were validated live in Phase 9.
