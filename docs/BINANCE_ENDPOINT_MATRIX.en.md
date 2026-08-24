# Binance Spot V1 Endpoint Matrix

> English counterpart of [BINANCE_ENDPOINT_MATRIX.md](BINANCE_ENDPOINT_MATRIX.md). | [Türkçe](BINANCE_ENDPOINT_MATRIX.md)

## Legend

- `NONE`: no API key or signature.
- `TRADE`, `USER_DATA`: require `X-MBX-APIKEY`, `timestamp`, optional `recvWindow`, and `signature`.

## Public and Market REST

| Public name | Method/path | Security | Parameters | Weight | Data source | Main response / note |
|---|---|---|---|---:|---|---|
| `ping` | `GET /api/v3/ping` | `NONE` | None | 1 | Memory | `{}` |
| `server-time` | `GET /api/v3/time` | `NONE` | None | 1 | Memory | `serverTime`; clock-offset input |
| `exchange-info` | `GET /api/v3/exchangeInfo` | `NONE` | Optional `symbol`, `symbols`, `permissions`, `showPermissionSets`, `symbolStatus`; forbidden combinations validated | 20 | Memory | Rate limits, symbols, filters, and permissions preserved losslessly |
| `depth` | `GET /api/v3/depth` | `NONE` | Required `symbol`; optional `limit` (default 100, max 5000), `symbolStatus` | 5/25/50/250 | Memory | `lastUpdateId`, bids, asks; weight depends on limit |
| `ticker-24h` | `GET /api/v3/ticker/24hr` | `NONE` | `symbol` or `symbols`; optional `type=FULL|MINI`, `symbolStatus` | 2/40/80 | Memory | One symbol object or multi/all array; parameterless is expensive |
| `ticker-price` | `GET /api/v3/ticker/price` | `NONE` | `symbol` or `symbols`; optional `symbolStatus` | one 2, multi/all 4 | Memory | Latest price |
| `book-ticker` | `GET /api/v3/ticker/bookTicker` | `NONE` | `symbol` or `symbols`; optional `symbolStatus` | one 2, multi/all 4 | Memory | Best bid/ask price and quantity |

### Dynamic Market Weights

| Depth `limit` | Weight |
|---:|---:|
| 1–100 | 5 |
| 101–500 | 25 |
| 501–1000 | 50 |
| 1001–5000 | 250 |

| Ticker selection | Weight |
|---|---:|
| One `symbol` | 2 |
| `symbols` 1–20 | 2 |
| `symbols` 21–100 | 40 |
| `symbols` 101+ or all symbols | 80 |

## Account and Trading REST

| Public name | Method/path | Security | Required / important parameters | Weight / order impact | Data source | Safety and response note |
|---|---|---|---|---|---|---|
| `account` | `GET /api/v3/account` | `USER_DATA` | `timestamp`; optional `omitZeroBalances`, `recvWindow` | 20 | Memory → Database | Balance and commission fields are decimal |
| `my-trades` | `GET /api/v3/myTrades` | `USER_DATA` | `symbol`, `timestamp`; optional `orderId`, `startTime`, `endTime`, `fromId`, `limit`, `recvWindow` | 5 with `orderId`, otherwise 20 | Memory → Database | `startTime`–`endTime` maximum 24 hours |
| `test-order` | `POST /api/v3/order/test` | `TRADE` | New-order parameters + `timestamp`; optional `computeCommissionRates` | 1, or 20 with commission calculation; order count 0 | Memory | Validates without reaching matching engine |
| `new-order` | `POST /api/v3/order` | `TRADE` | `symbol`, `side`, `type`, `timestamp`; LIMIT adds `timeInForce`,`quantity`,`price`; MARKET uses `quantity` or `quoteOrderQty`; STOP_LOSS adds `quantity`,`stopPrice` | successful/failed 0/1; unfilled count +1 | Matching Engine | Unique `newClientOrderId`; timeout/5xx means unknown execution |
| `query-order` | `GET /api/v3/order` | `USER_DATA` | `symbol`, `timestamp`, plus `orderId` or `origClientOrderId` | 4 | Memory → Database | With both IDs, order ID is found first and client ID checked |
| `cancel-order` | `DELETE /api/v3/order` | `TRADE` | `symbol`, `timestamp`, plus `orderId` or `origClientOrderId`; optional `cancelRestrictions`, `newClientOrderId` | successful/failed 0/1 | Matching Engine | Fastest with `orderId` only; timeout/5xx requires reconciliation |
| `open-orders` | `GET /api/v3/openOrders` | `USER_DATA` | `timestamp`; optional `symbol`, `recvWindow` | 6 with symbol, otherwise 80 | Memory → Database | Parameterless call is expensive |

## Common REST Errors

| Code/status | Meaning | Connector result |
|---|---|---|
| `-1001` | disconnected | transport/API error; trading may be unknown based on execution evidence |
| `-1003`, HTTP 429/418 | rate limit / ban | rate-limit error with retry metadata |
| `-1006`, `-1007` | unexpected response / timeout | `unknown-execution` for trading mutation |
| `-1013` | invalid message/filter | validation/API rejection |
| `-1015` | too many orders | order rate-limit error |
| `-1021` | invalid timestamp | clock error |
| `-1022` | invalid signature | authentication/encoding error |
| HTTP 5xx | server state unknown | `unknown-execution` for trading mutation |

## Market WebSocket Streams

| Public name | Stream | Frequency | Payload / note |
|---|---|---|---|
| `all-mini-tickers` | `!miniTicker@arr` | 1000 ms | Only symbols changed in the interval; not a full snapshot |
| `symbol-ticker` | `<symbol>@ticker` | 1000 ms | One-symbol full rolling 24-hour ticker |
| `book-ticker-stream` | `<symbol>@bookTicker` | real time | Best bid/ask update |
| `partial-depth` | `<symbol>@depth5|10|20` | 1000 ms or `@100ms` | Top-N snapshot update; V1 builds no full local book |
| lifecycle | `serverShutdown` event | server-driven | Reconnect and restore subscriptions |

## User Data Stream WebSocket API

| Method/event | Security | Weight | V1 use |
|---|---|---:|---|
| `userDataStream.subscribe.signature` | `USER_STREAM`, signed | 2 | Primary HMAC/Ed25519 subscription |
| `userDataStream.unsubscribe` | Connection/session | 2 | One `subscriptionId` or all subscriptions |
| `session.subscriptions` | Connection/session | 2 | Restore/diagnostic verification |
| `session.logon` + `userDataStream.subscribe` | Ed25519 session only | 2 + 2 | Secondary path; not required for HMAC-default V1 |
| `executionReport` | event | — | Order lifecycle and reconciliation |
| `outboundAccountPosition` | event | — | Changed free/locked balances |
| `balanceUpdate` | event | — | Balance delta |
| `eventStreamTerminated` | event | — | Subscription recovery |
