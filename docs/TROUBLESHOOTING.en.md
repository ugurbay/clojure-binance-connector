# Troubleshooting Guide

> English counterpart of [TROUBLESHOOTING.md](TROUBLESHOOTING.md). | [Türkçe](TROUBLESHOOTING.md)

Collect this information first without printing credential values:

```powershell
java -version
clojure -Sdescribe
clojure -M:run
clojure -M:verify-environment
```

## `java` or `clojure` Not Found

- Verify the JDK 25 and Clojure CLI installations.
- On Windows, you can use `scripts/install-portable-jdk.ps1`.
- Reopen PowerShell after a new installation.
- Verification scripts automatically select `.toolchains/jdk-25*` when a portable JDK is present.

## `invalid-timestamp` / Binance `-1021`

Likely causes are clock drift, a stale offset in a long-lived client, or the wrong time unit.

```clojure
(client/synchronize-time! connector)
```

Retry the signed workflow after synchronization. If you injected a custom `:clock`, offset management is your responsibility. Blindly increasing `recvWindow` does not fix the underlying clock problem.

## `invalid-signature` / Binance `-1022`

- Confirm that the key and secret belong to the same Testnet or production account.
- Make sure you did not put the API key in the HMAC-secret field.
- Do not construct the payload yourself; use the connector pipeline.
- Verify URL and environment alignment.
- Never print a secret in debug logs.

If credentials were exposed, revoke or rotate them at Binance.

## `api-key-ip-or-permission-rejected` / `-2015`

- Check API-key permissions and the IP allowlist.
- Signed account calls need read permission; `order/test` and new orders require TRADE permission.
- A Testnet key cannot be used in production, or vice versa.
- This connector does not require withdrawal permission.

## `order-not-found` / `-2013`

A query immediately after a new order can temporarily return `-2013` during the Memory → Database visibility window. This alone does not prove rejection.

- Use the lifecycle returned by `submit-order!`.
- Do not POST the same business order again.
- Follow the client order ID through bounded queries and UDS `executionReport` events.
- Send `:unresolved` cases to a manual or asynchronous reconciliation queue.

## `illegal-characters` / `-1100`

A wire parameter violates Binance's lexical contract:

- Do not use exponent notation such as `1E-8`.
- Do not send `float` or `double`.
- Supply financial input as `BigDecimal` or a plain-decimal string.
- If a calculation creates redundant trailing-zero scale, use `decimal/normalize` without changing the value.
- Client order IDs must use only permitted characters and length.

## Filter Failure / `-1013`

- Refresh the `exchangeInfo` snapshot.
- Check `PRICE_FILTER`, `LOT_SIZE`, `MIN_NOTIONAL`, and `NOTIONAL`.
- Do not silently round to tick or step; generate an exact value according to your business rule.
- Server-authoritative filters such as `PERCENT_PRICE(_BY_SIDE)` are not fully reproduced by local V1 preflight.
- Check symbol status and account open-order limits.

## `insufficient-balance`

Use `free` balance from the account response; `locked` funds are unavailable. For BUY, account for quote notional; for SELL, base quantity and fees. Testnet resets may alter balances and order history.

## `429` or `418`

- `429`: request/order limit exceeded. The connector honors `Retry-After` for safe reads when available, but the application must still reduce request rate.
- `418`: IP ban. The connector does not retry automatically. Do not create a request storm before the ban expires.
- Monitor request-weight and order-count metadata.
- Limit expensive parameterless all-symbol calls.

## New Order During Timeout, Transport Error, or `5xx`

The outcome of a state-changing command can be unknown. The most important rule is not to resend the same order.

```clojure
(def lifecycle (spot/submit-order! connector symbol-info order))
```

For `:unresolved`, use the client order ID with REST queries and signed UDS. Raise a visible alert for a human operator.

## WebSocket Does Not Connect

- Check that the firewall/proxy permits `wss` traffic.
- Remember that market streams and WebSocket API URLs differ.
- Synchronize time and verify credentials before signed UDS.
- Inspect `:status`, `:last-error-category`, and `:reconnect-attempt` in the snapshot.
- A `nil` event-poll result can be a normal timeout; do not confuse it with disconnection.

## Event Loss / Buffer Drops

```clojure
(streams/snapshot stream)
(user-stream/snapshot stream)
```

If `:dropped` increases, the consumer is too slow:

- move event work off the polling thread into a work queue;
- increase buffer capacity carefully;
- reconcile critical order/account gaps through REST; and
- never merely log and ignore drops.

## Connection Renewal at About 24 Hours

Binance WebSocket connections have a lifetime limit. The connector performs planned renewal/reconnect and restores subscriptions. A short gap remains possible; REST snapshots and reconciliation must be part of the design.

## Why Are Tests Skipped?

Default `clojure -X:integration-test` is offline. Live gates require explicit environment flags, which the release script scopes for you:

```powershell
.\scripts\verify-phase-9.ps1 -RunFullTestnet
```

This command requires real Spot Testnet credentials and creates then cancels one virtual LIMIT order.

## Secret Scan Fails

The scanner may have found an exact process credential value in repository text. Do not print the value again:

1. Do not add the file to Git.
2. Rotate the credential at Binance.
3. If it reached Git history, separately assess history rewriting and force-push impact.
4. Run `clojure -M:secret-scan` again.

## Before Opening an Issue

```powershell
clojure -X:test
clojure -X:integration-test
clojure -M:secret-scan
clojure -M:lint
clojure -M:format-check
```

Include operating system, JDK/Clojure versions, commit/tag, a minimal example, and secret-safe error `ex-data`. Never include an API key, secret, private key, signature, or signed query.
