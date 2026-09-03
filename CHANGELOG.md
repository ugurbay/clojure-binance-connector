# Changelog

Önemli değişiklikler bu dosyada kaydedilir. Proje [Semantic Versioning](https://semver.org/) yaklaşımını hedefler.

## [Unreleased]

## [1.0.4] - 2026-09-03

### Eklendi

- Public Spot `GET /api/v3/klines` için `spot/klines` API'si
- Case-sensitive interval, timestamp, limit ve time-zone doğrulaması
- Fiyat, miktar ve hacim alanlarında exact `BigDecimal` normalizasyonu
- İlk günlük mum zamanından listing-age kataloğu oluşturabilen botlar için credential-free okuma yolu

## [1.0.3] - 2026-08-22

### Eklendi

- Binance Spot `STOP_LOSS` emirleri için public `validate-order`, `test-order`, `new-order` ve `submit-order!` desteği
- `stopPrice` wire mapping, exact `BigDecimal` normalizasyonu ve `PRICE_FILTER`/`LOT_SIZE`/market-notional preflight
- Zorunlu alan, yasak alan, tick alignment ve gerçek request parametrelerini kapsayan contract testleri
- User Data Stream `executionReport` son gerçekleşen miktar alanı `l` için exact `BigDecimal` normalizasyonu

### Güvenlik

- Botun fill sonrası felaket koruması artık raw HTTP kestirmesi olmadan public connector sınırından kurulabilir
- `STOP_LOSS` state-changing command'ı mevcut exactly-one submit ve unknown-execution reconciliation politikasını aynen kullanır

## [1.0.2] - 2026-08-20

### Eklendi

- Public `aggregate-trades` stream helper'ı (`<symbol>@aggTrade`)
- `aggTrade` için exact `BigDecimal` fiyat/miktar ve kararlı alan alias'ları
- Kimlik aralığı, maker flag ve bozuk payload contract testleri
- Kimliksiz production `aggTrade` ile reconnect/restore kabul kapısı

### Netleştirildi

- Partial depth atomik top-N görünümüdür; diff-depth continuity veya tam defter sayılmaz

## [1.0.1] - 2026-08-18

### Eklendi

- Bütün Türkçe Markdown dokümanlarının `.en.md` İngilizce karşılıkları
- Türkçe ve İngilizce belgeler arasında merkezi iki dilli dokümantasyon dizini
- İngilizce README içinden İngilizce güvenlik, hukuk, kullanım, API, mimari ve faz kanıtlarına doğrudan bağlantılar
- Clojars için `io.github.ugurbay/binance-clj` source JAR ve Maven POM üretimi
- Yerel Maven dış-tüketici doğrulaması ve korumalı Clojars yayın script'i
- Türkçe ve İngilizce Clojars hesap, token, release ve kullanım rehberi

### Değiştirildi

- Immutable artifact sürümü `VERSION` içinde `1.0.1` olarak tanımlandı
- Build-only `tools.build` ve `deps-deploy` bağımlılıkları runtime POM'dan ayrıldı

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

[Unreleased]: https://github.com/ugurbay/clojure-binance-connector/compare/v1.0.4...HEAD
[1.0.4]: https://github.com/ugurbay/clojure-binance-connector/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/ugurbay/clojure-binance-connector/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/ugurbay/clojure-binance-connector/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/ugurbay/clojure-binance-connector/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/ugurbay/clojure-binance-connector/releases/tag/v1.0.0
