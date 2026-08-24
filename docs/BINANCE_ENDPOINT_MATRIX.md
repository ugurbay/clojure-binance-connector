# Binance Spot V1 Endpoint Matrisi

## Okuma Anahtarı

- `NONE`: API key/imza yok.
- `TRADE`, `USER_DATA`: `X-MBX-APIKEY`, `timestamp`, opsiyonel `recvWindow` ve `signature` gerekir.
- “Weight 0/1” ifadesi 2026-04-02 kuralıdır: başarılı çağrı 0, başarısız çağrı belgelenmiş 1 weight tüketir.
- Response finansal string'leri `BigDecimal` olarak normalize edilir; raw response ve bilinmeyen alanlar kaybedilmez.

Birincil kaynak: sabitlenmiş [REST API belgesi](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/rest-api.md).

## Public ve Market REST

| Public ad | Method/path | Security | Parametreler | Weight | Data source | Temel response / not |
|---|---|---|---|---:|---|---|
| `ping` | `GET /api/v3/ping` | `NONE` | Yok | 1 | Memory | `{}` |
| `server-time` | `GET /api/v3/time` | `NONE` | Yok | 1 | Memory | `serverTime`; clock offset girdisi |
| `exchange-info` | `GET /api/v3/exchangeInfo` | `NONE` | Opsiyonel `symbol`, `symbols`, `permissions`, `showPermissionSets`, `symbolStatus`; yasak kombinasyonlar doğrulanır | 20 | Memory | Rate limits, symbols, filters ve izinler kayıpsız tutulur |
| `depth` | `GET /api/v3/depth` | `NONE` | Zorunlu `symbol`; opsiyonel `limit` (default 100, max 5000), `symbolStatus` | 5/25/50/250 | Memory | `lastUpdateId`, bids, asks; weight limit aralığına bağlı |
| `ticker-24h` | `GET /api/v3/ticker/24hr` | `NONE` | `symbol` veya `symbols`; opsiyonel `type=FULL|MINI`, `symbolStatus` | 2/40/80 | Memory | Tek symbol object, çok/all symbol array; paramsız çağrı pahalıdır |
| `ticker-price` | `GET /api/v3/ticker/price` | `NONE` | `symbol` veya `symbols`; opsiyonel `symbolStatus` | tek 2, çok/all 4 | Memory | Son fiyat |
| `book-ticker` | `GET /api/v3/ticker/bookTicker` | `NONE` | `symbol` veya `symbols`; opsiyonel `symbolStatus` | tek 2, çok/all 4 | Memory | Best bid/ask price ve quantity |

### Dinamik market weight'leri

`depth`:

| `limit` | Weight |
|---:|---:|
| 1–100 | 5 |
| 101–500 | 25 |
| 501–1000 | 50 |
| 1001–5000 | 250 |

`ticker-24h`:

| Seçim | Weight |
|---|---:|
| Tek `symbol` | 2 |
| `symbols` 1–20 | 2 |
| `symbols` 21–100 | 40 |
| `symbols` 101+ veya tüm semboller | 80 |

## Account ve Trading REST

| Public ad | Method/path | Security | Zorunlu / önemli parametreler | Weight / order etkisi | Data source | Güvenlik ve response notu |
|---|---|---|---|---|---|---|
| `account` | `GET /api/v3/account` | `USER_DATA` | `timestamp`; opsiyonel `omitZeroBalances`, `recvWindow` | 20 | Memory → Database | Balance ve commission alanları decimal |
| `my-trades` | `GET /api/v3/myTrades` | `USER_DATA` | `symbol`, `timestamp`; opsiyonel `orderId`, `startTime`, `endTime`, `fromId`, `limit`, `recvWindow` | `orderId` ile 5, aksi 20 | Memory → Database | `startTime`–`endTime` en fazla 24 saat |
| `test-order` | `POST /api/v3/order/test` | `TRADE` | New order params + `timestamp`; opsiyonel `computeCommissionRates` | 1 veya commission hesabıyla 20; order count 0 | Memory | Validate eder, matching engine'e göndermez |
| `new-order` | `POST /api/v3/order` | `TRADE` | `symbol`, `side`, `type`, `timestamp`; LIMIT: `timeInForce`,`quantity`,`price`; MARKET: `quantity` veya `quoteOrderQty`; STOP_LOSS: `quantity`,`stopPrice` | başarılı/başarısız 0/1; unfilled order count 1 | Matching Engine | Benzersiz `newClientOrderId`; timeout/5xx unknown execution |
| `query-order` | `GET /api/v3/order` | `USER_DATA` | `symbol`, `timestamp`, ayrıca `orderId` veya `origClientOrderId` | 4 | Memory → Database | İki ID varsa önce orderId bulunur, client ID eşleşmesi kontrol edilir |
| `cancel-order` | `DELETE /api/v3/order` | `TRADE` | `symbol`, `timestamp`, ayrıca `orderId` veya `origClientOrderId`; opsiyonel `cancelRestrictions`, `newClientOrderId` | başarılı/başarısız 0/1 | Matching Engine | En hızlı yol yalnız `orderId`; timeout/5xx reconciliation ister |
| `open-orders` | `GET /api/v3/openOrders` | `USER_DATA` | `timestamp`; opsiyonel `symbol`, `recvWindow` | symbol ile 6, all 80 | Memory → Database | Paramsız çağrı pahalıdır |

V1 order enum sınırı `BUY|SELL` ve `MARKET|LIMIT`'tir. Binance endpoint'i daha fazla order tipi desteklese de public connector sözleşmesi bunları kabul etmez.

## REST Ortak Hataları

| Code/status | Anlam | Connector sonucu |
|---|---|---|
| `-1001` | disconnected | transport/API error; trading ise execution kanıtına göre unknown olabilir |
| `-1003`, HTTP 429/418 | rate limit / ban | retry metadata ile rate-limit error |
| `-1006`, `-1007` | unexpected response / timeout | trading mutation için `unknown-execution` |
| `-1013` | invalid message/filter | validation/API rejection |
| `-1015` | too many orders | order rate-limit error |
| `-1021` | invalid timestamp | clock error |
| `-1022` | invalid signature | auth/encoding error |
| HTTP 5xx | server state unknown | trading mutation için `unknown-execution` |

Resmî code kataloğu: [Spot Errors](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/errors.md).

## Market WebSocket Streams

Kaynak: [WebSocket Streams](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/web-socket-streams.md).

| Public ad | Stream | Hız | Payload / not |
|---|---|---:|---|
| `all-mini-tickers` | `!miniTicker@arr` | 1000 ms | Son intervalde değişen semboller; full snapshot değildir |
| `symbol-ticker` | `<symbol>@ticker` | 1000 ms | Tek sembol 24 saat rolling full ticker |
| `book-ticker-stream` | `<symbol>@bookTicker` | real-time | Best bid/ask update |
| `partial-depth` | `<symbol>@depth5|10|20` | 1000 ms veya `@100ms` | Top N snapshot update; V1 full local order book kurmaz |
| lifecycle | `serverShutdown` event | server-driven | Reconnect ve subscription restore |

`!ticker@arr` kaldırılmıştır ve endpoint registry'ye eklenmeyecektir.

## User Data Stream WebSocket API

Kaynaklar: [WebSocket API](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/web-socket-api.md) ve [User Data Stream](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/user-data-stream.md).

| Method/event | Security | Weight | V1 kullanımı |
|---|---|---:|---|
| `userDataStream.subscribe.signature` | `USER_STREAM`, signed | 2 | Birincil HMAC/Ed25519 subscription |
| `userDataStream.unsubscribe` | Connection/session | 2 | Tek `subscriptionId` veya tüm subscriptions |
| `session.subscriptions` | Connection/session | 2 | Restore/diagnostic doğrulaması |
| `session.logon` + `userDataStream.subscribe` | yalnız Ed25519 session | 2 + 2 | İkincil yol; V1 HMAC varsayılanı için zorunlu değil |
| `executionReport` | event | — | Order lifecycle ve reconciliation |
| `outboundAccountPosition` | event | — | Değişen free/locked balances |
| `balanceUpdate` | event | — | Balance delta |
| `eventStreamTerminated` | event | — | Subscription recovery |
