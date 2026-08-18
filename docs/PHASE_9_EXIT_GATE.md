# Faz 9 SDK Release Gate

**Tarih:** 2026-08-18
**Sonuç:** PASS — CONNECTOR V1 READY

Faz 9 offline ve full Spot Testnet release kapıları tamamlandı. Varsayılan kapı ağsız, credential'sız ve emir göndermeden çalışmaya devam eder; `-RunFullTestnet` kabulü 2026-08-18 tarihinde gerçek Testnet credential'ları ve sanal bakiye ile geçti.

## Offline ve Kalite Kapıları

- [x] Unit/mock/contract testleri
- [x] Varsayılan ağsız integration harness
- [x] JDK 25 / Clojure 1.12.5
- [x] clj-kondo: 0 error/warning
- [x] cljfmt
- [x] Secret leakage scan
- [x] Precision-safe JSON ve BigDecimal regression
- [x] Unknown execution no-resubmit regression
- [x] Forced reconnect/restore mock sözleşmesi
- [x] Dependency-free performance baseline
- [x] 10 saniye bounded event soak; drop/thread growth yok
- [x] Public API ve lifecycle dokümantasyonu

## Full Spot Testnet Kabulü

- [x] Public REST: ping/time/exchangeInfo/ticker/book/depth
- [x] Signed account ve non-executing `order/test`
- [x] Public market WebSocket event
- [x] Canlı planned renewal + subscription restore
- [x] Signed User Data Stream subscription
- [x] Non-marketable LIMIT create
- [x] Query ile `NEW` doğrulama
- [x] `openOrders` içinde aynı client order id
- [x] UDS `NEW executionReport`
- [x] Tek cancel command
- [x] UDS `CANCELED executionReport`
- [x] Final query `CANCELED`
- [x] Final `openOrders` içinde emir yok
- [x] Full Testnet koşusunda exact credential leakage scan

## Güvenlik

Full kabul yalnız `:environment :testnet` client oluşturur. LIMIT fiyat/miktarı sabit değildir; güncel `bookTicker`, `exchangeInfo` filtreleri ve sanal hesap bakiyesinden türetilir. Öncelik, best ask'in yüzde 2 üstünde fonlanabilir SELL; gerekirse best bid'in yüzde 2 altında BUY'dır. Böylece emir kısa kabul penceresinde non-marketable kalır ve cancel yaşam döngüsü gözlenebilir. Binance'in tüm dinamik percent-price filtrelerinde nihai otorite server'dır.

Command tam bir kez gönderilir. New-order sonucu unknown olursa `submit-order!` aynı emri yeniden POST etmez. Test olağan akışta tek cancel gönderir; erken hata halinde önce order query yapar ve yalnız açık emir gözlenirse, daha önce cancel denenmemiş olmak koşuluyla bir cleanup cancel gönderir.

### Canlı Kabul Bulgusu — `-2013` Görünürlük Penceresi

İlk full Testnet denemesinde new-order başarılı olduktan hemen sonraki order query kısa süreli `-2013 ORDER_NOT_FOUND` döndürdü. Bu, `Memory → Database` okuma yolundaki eventual consistency ile uyumludur ve kesin emir reddi değildir. Harness artık önce UDS olayını bekler, ardından yalnız query/open-orders safe read'lerini 250 ms aralıkla en fazla 20 kez gözlemler. Beklenmeyen hata hemen yükseltilir; create/cancel command'ları tekrar edilmez. Submit response içindeki `orderId` erken hata cleanup'ı için ayrıca tutulur.

İkinci denemede submit lifecycle kesin `rejected` döndü; bu durumda order query yapılmasının tanıyı `-2013` ile gölgelemesi engellendi. Harness aynı dinamik emir adayını önce non-executing `POST /api/v3/order/test` ile doğrular ve gerçek submit reddedilirse yalnız allowlist edilmiş category/code/reason/filter veya yerel field/rule bilgisini fail-fast raporlar. Binance'in ham hata mesajı ve request içeriği rapora taşınmaz.

Üçüncü denemedeki non-executing preflight `-1100 ILLEGAL_CHARS` ile kesin nedeni ayırdı. `divideToIntegralValue` ve `multiply` ile üretilen tick/step hizalı değerler matematiksel olarak doğruydu fakat ara işlem scale'ini taşıyarak `0.000170000000000000000000` gibi gereksiz uzun wire metni oluşturuyordu. Harness floor/ceil sonucunu yuvarlamadan `decimal/normalize` ile sadeleştirir; regresyon testi güncel Testnet ölçeğine benzer girdinin `price=66029.04&quantity=0.00017` üretmesini sabitler. Ortak serializer'ın kullanıcı scale'ini koruyan ve resmi imza vektörlerine bağlı sözleşmesi değiştirilmemiştir.

## Komutlar

```powershell
# Ağsız release kapısı
.\scripts\verify-phase-9.ps1

# API key/secret aynı PowerShell process'inde iken tam release kabulü
.\scripts\verify-phase-9.ps1 -RunFullTestnet
```

## Final Kabul Kanıtı

2026-08-18 tarihli `verify-phase-9.ps1 -RunFullTestnet` koşusu:

- Unit/contract: 103 test, 624 assertion, 0 failure/error
- Offline integration: 10 test, 20 assertion, 0 failure/error
- Full Testnet integration: 10 test, 47 assertion, 0 failure/error
- Exact credential scan: 2 değer kontrol edildi, 0 ihlal
- clj-kondo: 0 error/warning; cljfmt temiz
- 10 saniye soak: 1.346.274 event, 0 drop, 0 thread artışı
- Final mesaj: `Phase 9 release verification passed.`

Runtime artık `:phase 9` ve `:status :ready` bildirir. Connector V1 bot projesine devredilmeye hazırdır.
