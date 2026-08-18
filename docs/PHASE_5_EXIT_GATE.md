# Faz 5 Çıkış Kapısı

**Tarih:** 2026-08-18
**Sonuç:** PASS

## Uygulanan Public Spot Uçları

- [x] `GET /api/v3/ping`
- [x] `GET /api/v3/time`
- [x] `GET /api/v3/exchangeInfo`
- [x] `GET /api/v3/ticker/price`
- [x] `GET /api/v3/ticker/24hr`
- [x] `GET /api/v3/ticker/bookTicker`
- [x] `GET /api/v3/depth`

Davranış kaynağı, projede sabitlenen [resmî Binance Spot REST sözleşmesidir](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/rest-api.md).

## Sözleşme ve Normalizasyon

- [x] Endpointler default registry'de declarative spec
- [x] Sembol, sembol listesi, enum, limit ve parametre kombinasyonu doğrulaması
- [x] `symbols`/`permissions` için JSON-array wire biçimi ve RFC 3986 encoding
- [x] `ticker/24hr`, ticker price/book ve depth için parametreye bağlı weight
- [x] `exchangeInfo` symbol/filter yapısında unknown alan koruması
- [x] Ticker, filter ve depth finansal değerlerinde strict `BigDecimal`
- [x] Timestamp/id/count değerlerinin integer kalması
- [x] Malformed Binance response'un raw payload sızdırmadan `:api` hatası olması
- [x] Public facade'da validation hatalarının transport çağrısından önce üretilmesi

## Test Matrisi

- General endpoint facade ve metadata
- Tek sembol, sembol dizisi ve all-market ticker seçimleri
- FULL/MINI 24 saat ticker
- Book ticker object/array biçimleri
- Depth limit sınırları: `1`, `100`, `101`, `500`, `501`, `1000`, `1001`, `5000`
- Dynamic weight sınırları: 20/21/100/101 sembol
- JSON-array query percent encoding
- Geçersiz parametre kombinasyonları ve bilinmeyen key'ler
- Non-map facade girdisinin normalized validation olması
- Malformed server time, decimal, depth ve exchangeInfo yanıtları
- Unknown top-level, symbol, filter ve depth-level alanlarının korunması

## Doğrulama

- Unit/mock: PASS — 58 test, 343 assertion, 0 failure/error
- Varsayılan ağsız integration harness: PASS — 2 test, 2 assertion
- Anahtarsız Spot Testnet: PASS — 2 test, 8 assertion; yedi public endpoint
- clj-kondo: PASS — 0 warning/error
- cljfmt: PASS
- Runtime: JDK 25 / Clojure 1.12.5
- Faz 5 yeni runtime dependency eklemedi

Canlı kabul testi:

```powershell
.\scripts\verify-phase-5.ps1 -RunPublicTestnet
```

**Sıradaki faz:** Faz 6 — Signed REST, Filtreler ve Emir Güvenliği.
