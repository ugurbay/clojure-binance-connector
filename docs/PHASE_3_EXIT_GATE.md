# Faz 3 Çıkış Kapısı

**Tarih:** 2026-08-18
**Sonuç:** PASS

## Uygulanan Bileşenler

- [x] Strict ve kayıpsız `BigDecimal` parse/normalize/plain serialization
- [x] Scientific notation, floating-point ve sessiz rounding reddi
- [x] RFC 3986 UTF-8 percent encoding
- [x] Deterministik REST query/form canonicalization
- [x] Ayrı, alfabetik ve raw UTF-8 WebSocket API canonicalization
- [x] Milisaniye ve mikrosaniye timestamp üretimi
- [x] Midpoint tabanlı server-time offset ve kontrollü synchronized clock
- [x] Merkezi `recvWindow` varsayılanı, maksimumu ve üç basamak precision kontrolü
- [x] Algoritmadan bağımsız `Signer` protocol'ü
- [x] HMAC-SHA256/lowercase-hex signer
- [x] Ed25519/Base64 signer ve unencrypted PKCS#8 PEM loader
- [x] Secret-safe signer string temsili ve error redaction

## Doğrulanan Vektörler

- Binance Spot REST ASCII HMAC örneği: PASS
- Binance Spot REST percent-encoded Unicode HMAC örneği: PASS
- Binance WebSocket API alfabetik payload HMAC örneği: PASS
- RFC 8032 bölüm 7.1 Ed25519 test 1: PASS

Testlerdeki Binance key materyali, resmî dokümanda yalnız örnek amacıyla yayınlanan ve gerçek credential olmayan vektördür. Repository'de kullanıcı/Testnet/production credential'ı yoktur.

## Sınır ve Determinizm Testleri

- Tiny/large/negative decimal, trailing zero ve normalize davranışı
- Exponent, whitespace, shorthand ve `float`/`double` reddi
- Space, `+`, `/`, `&`, `=`, `~` ve full-width Unicode encoding
- Map sıralaması, pair-vector sırası ve nil omission
- Timestamp unit, invalid clock ve exact mikro timestamp
- Offset midpoint ve mutable offset sınırı
- `recvWindow` alt/üst/scale sınırları
- OpenSSH, encrypted PKCS#8 ve raw Ed25519 seed reddi
- Aynı Ed25519 input için aynı signature
- Signer ve error değerlerinde key materyali sızıntısı olmaması

## Faz Sınırı

Faz 3 HTTP bağlantısı, JSON parsing, Binance endpointleri veya rate-limit state'i eklemez. Signed REST/WS payload üretimi bağımsızdır; HTTP transport Faz 4'te exact `:signed-query` değerini wire'a taşıyacaktır.

RSA, encrypted Ed25519 key container ve OpenSSH private-key desteği V1 public yüzeyinde yoktur.

## Doğrulama

- Unit/contract: PASS — 32 test, 139 assertion, 0 failure/error
- Offline integration harness: PASS — 1 test, 1 assertion
- clj-kondo: PASS — 0 warning/error
- cljfmt: PASS
- Runtime dependency artışı: yok

**Sıradaki faz:** Faz 4 — HTTP Transport, JSON, Hatalar ve Rate Limit.
