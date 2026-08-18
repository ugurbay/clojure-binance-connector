# Changelog

Önemli değişiklikler bu dosyada kaydedilir. Proje [Semantic Versioning](https://semver.org/) yaklaşımını hedefler.

## [1.0.0] - 2026-08-18

İlk public V1 release adayı.

### Eklendi

- Testnet varsayılan, production çift guard'lı client config
- Public Spot REST: ping, time, exchange info, ticker, book ticker ve depth
- Signed account, trades, test order, query/open orders, new/cancel order
- HMAC-SHA256 ve Ed25519 signer altyapısı
- Exact `BigDecimal` finansal model ve Binance filter preflight
- Rate-limit metadata, `429`/`418` davranışı ve safe-read retry
- No-resubmit unknown-execution reconciliation lifecycle
- Market WebSocket ve signed User Data Stream
- Reconnect, planned renewal, subscription restore ve bounded event buffer
- Unit, contract, offline integration ve opt-in full Spot Testnet release harness
- Secret leakage scan, benchmark, soak ve kapsamlı mimari/araştırma belgeleri

### Doğrulandı

- JDK 25 / Clojure 1.12.5
- 103 unit/contract test, 624 assertion
- 10 offline integration test, 20 assertion
- Full Spot Testnet: 10 test, 47 assertion
- Non-marketable LIMIT `NEW → query/openOrders → cancel → CANCELED`
- Credential leakage: 0; lint/format: temiz; soak drop/thread growth: 0

### Güvenlik

- Production trading varsayılan kapalı
- State-changing command'larda otomatik retry yok
- Credential ve signature redaction
- `.env`, key dosyaları ve local toolchain git dışında

[1.0.0]: https://github.com/ugurbay/clojure-binance-connector/releases/tag/v1.0.0
