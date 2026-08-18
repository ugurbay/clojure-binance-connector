# Faz 2 Çıkış Kapısı

**Tarih:** 2026-08-18
**Sonuç:** PASS

## Uygulanan Sözleşmeler

- [x] Testnet varsayımlı config şeması ve production çift guard modeli
- [x] Güvenli REST/WebSocket base URL seçimi ve override validation
- [x] `recvWindow`, timeout, credential, clock ve transport validation
- [x] Bağlantı açmayan `create-client` ve idempotent `close!` lifecycle
- [x] Qualified id taşıyan immutable declarative endpoint registry
- [x] `:read`/`:command` execution semantiği ve command için zorunlu `:never` retry
- [x] Sekiz aşamalı transport-independent generic request pipeline
- [x] Protocol ve function-map transport enjeksiyonu
- [x] Clock, signer, parser ve pipeline stage enjeksiyonu
- [x] Stable error taxonomy ve recursive secret redaction
- [x] Public immutable map girdi/çıktı sözleşmesi

## Saf Çekirdek Testleri

- Config defaults, environment/base URL ve validation
- `recvWindow` sınır/scale ve floating-point reddi
- Ambiguous credential ve unknown option reddi
- Endpoint spec/registry/duplicate validation
- HTTP metodundan bağımsız command retry yasağı
- Pipeline aşama sırası
- Clock, signer ve transport enjeksiyonu
- Input map'in değiştirilmemesi
- Boundary exception normalization ve secret sızıntısı testi
- Client close-at-most-once ve closed-client davranışı
- Injection contract validation

## Faz Sınırı

Faz 2'de gerçek HTTP, JSON parser, HMAC/Ed25519 signer veya Binance endpoint implementasyonu eklenmedi. Pipeline request'i abstract map, transport response'u opaque değer olarak ele alır. Böylece public API HTTP library, JSON representation, signer implementation veya ham Binance response biçimine bağlı değildir.

Yeni runtime dependency eklenmemiştir.

## Doğrulama

- Unit: PASS — 18 test, 68 assertion, 0 failure/error.
- Offline integration harness: PASS
- clj-kondo: PASS
- cljfmt: PASS
- Secret scan: PASS

**Sıradaki faz:** Faz 3 — Decimal, Encoding, Zaman ve Authentication.
