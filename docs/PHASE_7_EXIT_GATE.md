# Faz 7 Çıkış Kapısı

**Tarih:** 2026-08-18
**Sonuç:** PASS

Faz 7, belirsiz new-order execution sonucunda aynı command'i tekrar göndermeyen ve sonucu client order id ile kanıtlamaya çalışan güvenli lifecycle sözleşmesini tamamlamıştır. Bu fazın kabulü deterministik mock/contract senaryolarıdır; gerçek emir veya zorla canlı timeout üretilmemiştir.

## Resmî Sözleşme Temeli

- [Binance Spot REST API](https://developers.binance.com/en/docs/products/spot/rest-api): `5xx` execution unknown, new order, query order ve `newClientOrderId`
- [Binance Spot errors](https://github.com/binance/binance-spot-api-docs/blob/master/errors.md): `-1006`, `-1007` ve `-2013`

Resmî sözleşmeye göre `5xx` başarısızlık kabul edilemez; execution sonucu unknown olabilir. Query order, `newClientOrderId` değerini `origClientOrderId` olarak kabul eder. Aynı id yalnız açık order benzersizliği için server tarafından korunur; connector client yaşam süresi boyunca daha muhafazakâr tekrar kullanım yasağı uygular.

## Lifecycle ve Güvenlik

- [x] `pending`, `confirmed`, `rejected`, `unknown`, `reconciled`, `unresolved`
- [x] Command submission tam 1 attempt; otomatik ikinci POST yok
- [x] Unknown exception data'sında güvenli symbol/client-order-id
- [x] Signed query-order ile `origClientOrderId` reconciliation
- [x] Varsayılan 5 logical query attempt
- [x] 250 ms exponential delay, 2000 ms local cap
- [x] `429 Retry-After` süresine uyum
- [x] `418` ve non-retryable query hatasında erken `unresolved`
- [x] Tekrarlanan `-2013` sonucunun yanlış `rejected` yapılmaması
- [x] Client yaşam süresinde duplicate client-order-id'nin wire öncesi reddi
- [x] Secret-safe lifecycle event/error özeti

## Test Matrisi

- Immediate command success → `confirmed`, query yok
- Deterministic API rejection → `rejected`, query yok
- Timeout/unknown → ilk query'de order bulundu
- Order-not-found → transient query failure → order bulundu
- Query bütçesi boyunca order-not-found → `unresolved`
- Non-retryable auth query error → tek query ile `unresolved`
- Unexpected submit boundary error → unknown + reconciliation
- `429` + Retry-After → server delay korunur
- `418` → ikinci query yok
- Aynı client-order-id ile ikinci command → wire öncesi validation
- Facade query parametresi → exact `origClientOrderId`
- Her belirsizlik senaryosunda new-order POST sayısı → `1`

## Doğrulama

- Unit/mock: PASS — 87 test, 527 assertion, 0 failure/error
- Varsayılan ağsız integration harness: PASS
- clj-kondo: PASS — 0 warning/error
- cljfmt: PASS
- Runtime hedefi: JDK 25 / Clojure 1.12.5
- Faz 7 yeni runtime dependency eklemedi

Kabul komutu:

```powershell
.\scripts\verify-phase-7.ps1
```

**Sıradaki faz:** Faz 8 — WebSocket Streams ve User Data Stream. Faz 8 execution eventlerini bu lifecycle modeline observation kaynağı olarak bağlayacaktır.
