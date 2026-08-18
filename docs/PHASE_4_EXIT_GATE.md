# Faz 4 Çıkış Kapısı

**Tarih:** 2026-08-18
**Sonuç:** PASS

## Uygulanan Bileşenler

- [x] Connection-pool kullanan reusable JDK 25 `HttpClient`
- [x] Ayrı connect ve per-request timeout
- [x] HTTPS-only exact query request builder
- [x] `X-MBX-APIKEY`, microsecond ve JSON accept header'ları
- [x] Precision-safe `data.json` parser (`BigDecimal`/BigInt)
- [x] Empty ve malformed JSON response davranışı
- [x] Normalized transport response/result metadata zarfı
- [x] Binance status ve numeric code hata sınıflandırması
- [x] `X-MBX-USED-WEIGHT-*`, `X-MBX-ORDER-COUNT-*`, `Retry-After` parsing
- [x] Local raw request ve estimated static weight tracker
- [x] Safe-read exponential retry
- [x] `429` Retry-After uyumu ve `418` retry yasağı
- [x] Command timeout/network/5xx/`-1006`/`-1007` unknown-execution güvenliği
- [x] Signed request için exact signed-query zorunluluğu
- [x] Request/body/signature/API-key diagnostic redaction

## Mock HTTP Test Matrisi

- 200 JSON ve response metadata
- 204/boş response
- Malformed 2xx JSON
- Unicode/reserved query encoding
- Request timeout ve header üretimi
- 503 → network error → success safe-read retry
- Retry bütçesi tükenen timeout
- `429` + geçerli `Retry-After`
- `429` + malformed `Retry-After`
- `418` IP ban
- Command `503` ve client timeout
- Command `-1007` response
- Binance API ve auth code'ları
- Non-JSON error body
- Unexpected boundary exception redaction
- Missing signed-query'nin wire öncesi reddi
- Rate-limit header parse ve tracker state
- Pipeline data/metadata envelope entegrasyonu

## Faz Sınırı

Faz 4 herhangi bir Binance endpoint public fonksiyonu veya response domain normalizer'ı eklemez. Endpoint registry ve public Spot fonksiyonları Faz 5'tedir. Signed account/trading endpointleri ve filtre validation Faz 6'da etkinleştirilecektir.

Command belirsizliği Faz 4'te doğru kategori ve retry yasağıyla korunur; reconciliation state machine Faz 7 kapsamındadır.

## Doğrulama

- Unit/contract: PASS — 50 test, 232 assertion, 0 failure/error
- Offline integration harness: PASS
- clj-kondo: PASS — 0 warning/error
- cljfmt: PASS
- Runtime: JDK 25 / Clojure 1.12.5
- Yeni runtime dependency: `org.clojure/data.json` 2.5.2

**Sıradaki faz:** Faz 5 — Public Spot REST.
