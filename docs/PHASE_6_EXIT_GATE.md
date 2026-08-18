# Faz 6 Çıkış Kapısı

**Tarih:** 2026-08-18
**Sonuç:** PASS

Faz 6'nın bütün ağsız, public ve credential'lı Spot Testnet kapıları geçmiştir. Kabul sırasında hiçbir gerçek emir oluşturulmamış; yalnız account read ve matching engine'de çalışmayan `order/test` kullanılmıştır.

## Resmî Sözleşme Temeli

- [Binance Spot REST API](https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md): signed request, account, my trades ve order endpointleri
- [Binance Spot Filters](https://github.com/binance/binance-spot-api-docs/blob/master/filters.md): price, lot, market lot ve notional kuralları
- [Binance Spot Errors](https://github.com/binance/binance-spot-api-docs/blob/master/errors.md): auth ve trading error code'ları
- [Clojure data structures / numbers](https://clojure.org/reference/data_structures): arbitrary precision `BigDecimal` temeli

## Uygulanan Signed Spot Uçları

- [x] `GET /api/v3/account`
- [x] `GET /api/v3/myTrades`
- [x] `POST /api/v3/order/test`
- [x] `POST /api/v3/order`
- [x] `GET /api/v3/order`
- [x] `DELETE /api/v3/order`
- [x] `GET /api/v3/openOrders`

## Güvenlik ve Doğruluk Kontrolleri

- [x] Config credential'ından otomatik HMAC-SHA256 veya Ed25519 signer
- [x] Public server-time midpoint ölçümüyle explicit, offset-aware client clock
- [x] Exact signed query; `recvWindow`, timestamp ve signature wire bütünlüğü
- [x] API key header ve endpoint permission metadata
- [x] `BUY`/`SELL`, `MARKET`/`LIMIT` parametre sözleşmeleri
- [x] `PRICE_FILTER`, `LOT_SIZE`, `MARKET_LOT_SIZE`, `MIN_NOTIONAL`, `NOTIONAL`
- [x] Raw ve normalized exchangeInfo decimal değerlerinde strict `BigDecimal`
- [x] Eksik/bozuk/duplicate bilinen filtrelerin yerelde reddi
- [x] Rounding yapmadan inclusive min/max ve exact tick/step kontrolü
- [x] Benzersiz ve wire-safe `newClientOrderId`
- [x] Production new/cancel için varsayılan kapalı ikinci güvenlik kilidi
- [x] New/cancel command'larında otomatik retry yasağı
- [x] API key/IP/permission, signature/timestamp, balance ve order hata reason'ları
- [x] Unknown response alanlarını koruyan finansal `BigDecimal` normalizasyonu

## Test Kanıtı

- Unit/mock: PASS — 77 test, 480 assertion, 0 failure/error
- Varsayılan ağsız integration harness: PASS — 3 test, 3 assertion
- Anahtarsız public Spot Testnet regresyonu: PASS — 3 test, 9 assertion; yedi public endpoint
- clj-kondo: PASS — 0 warning/error
- cljfmt: PASS
- Runtime: JDK 25 / Clojure 1.12.5
- Faz 6 yeni runtime dependency eklemedi
- [x] Signed Spot Testnet: HMAC account read ve non-executing MARKET `order/test`

İlk signed kabul denemesi Binance `-1021` ile durdu: Windows saati Testnet saatinden yaklaşık 2,5 saniye ileride ve Windows Time servisi kapalıydı. Bu bulgu üzerine acceptance akışına `client/synchronize-time!` eklendi. Yeni Testnet anahtarıyla tekrar çalıştırılan signed account ve `order/test` kabulü geçti.

## Son Kabul Komutu

Gerçek credential değerlerini dosyaya veya sohbete yazmadan yalnız yerel PowerShell process environment'ına koyun:

```powershell
$env:BINANCE_API_KEY='testnet-api-key'
$env:BINANCE_API_SECRET='testnet-api-secret'
.\scripts\verify-phase-6.ps1 -RunPublicTestnet -RunSignedTestnet
```

Signed test `POST /api/v3/order/test` kullanır; matching engine'de emir gerçekleştirmez. Script hiçbir production environment veya live-trading flag'i açmaz.

**Kapanış:** Faz 6 tamamlandı. Sıradaki aşama Faz 7 — Unknown Execution ve Emir Reconciliation.
