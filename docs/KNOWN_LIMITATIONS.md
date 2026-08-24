# Bilinen Sınırlamalar

## V1 Kapsam Sınırları

- Yalnız Binance Spot manuel trading connector.
- Yalnız `BUY`/`SELL` ve `MARKET`/`LIMIT` order tipleri.
- Futures, Margin, Options, OCO/OTO, SOR, wallet transfer/deposit/withdraw, bot stratejisi ve UI yok.
- JSON desteklenir; SBE ve FIX desteklenmez.
- HMAC-SHA256 tam destek ve Ed25519 signer altyapısı hedeflenir. RSA V1 public özelliği değildir.
- Ed25519 key loader yalnız unencrypted PKCS#8 `PRIVATE KEY` PEM veya JDK `PrivateKey` kabul eder. Encrypted PKCS#8, OpenSSH ve raw 32-byte seed desteği V1 kapsamı dışındadır.

## Resmî Kaynak Sınırlamaları

- Resmî OpenAPI v1.22.0, 2024-10-08 tarihlidir ve 2026 Spot davranışlarının tamamını içermez. Kod üretim veya güncel davranış otoritesi olarak kullanılamaz.
- Resmî Python SDK generated modelleri finansal alanlarda `float` kullanabildiği için Clojure domain tipi kaynağı değildir.
- Resmî Go ve JavaScript SDK'ları da OpenAPI-generated kapalı model ağlarıdır; güncel financial wire alanlarını string tutmaları yararlı bir çapraz kontroldür ancak Clojure domain modeli olarak kopyalanmaz.
- Resmî Go common `2.6.0` ve JavaScript common `2.4.5` retry davranışları state-changing command güvenliği için yeterli değildir. Connector bunları davranış otoritesi olarak kullanmaz.
- Binance hata mesajı metinleri değişebilir; yalnız numeric code ve HTTP status kararlı sınıflandırma girdisi sayılır.

## Testnet

- Spot Testnet live exchange ile her zaman aynı anda güncellenmez.
- Yaklaşık ayda bir ve ön bildirim olmadan veri resetlenebilir; order geçmişi ve açık order'lar silinir.
- Bakiyeler sanaldır, transfer edilemez; `/sapi/*` desteklenmez.
- Rate limit ve filtreler production'a genellikle benzer olsa da hard-code edilmez; `exchangeInfo` otoritedir.
- Ağ gerektiren testler opt-in kalır. Faz 5'te yedi public REST ucu anahtarsız Spot Testnet üzerinde doğrulanmıştır. Faz 6'da HMAC signed account ve non-executing `order/test` gerçek Spot Testnet üzerinde geçmiştir.

## REST ve Trading

- Production alternatif `api1`–`api4` host'ları daha az kararlı olabilir; V1 otomatik host failover yapmaz.
- Paramsız `ticker/24hr` ve `openOrders` çağrıları 80 weight tüketebilir; varsayılan public API mümkün olduğunca symbol seçimini teşvik eder.
- `exchangeInfo` ve account/order query bazı durumlarda Memory → Database fallback kullanır; kısa süreli eventual consistency görülebilir.
- Trading timeout, `-1006`, `-1007` ve ilgili `5xx` kesin başarı/başarısızlık bildirmez. Connector aynı request'i otomatik tekrar göndermez; bounded order query sonunda kayıt görülmezse sonuç açıkça `unresolved` kalır.
- Emir create kadar cancel (`DELETE /api/v3/order`) da state-changing command'dır. HTTP metodu `DELETE` olsa bile network/`5xx` sonrasında genel retry uygulanmaz.
- Yeni order'ın güncel request weight'i `1` ve unfilled order count etkisi `1`dir. Header bulunmayan rejected response'da yerel order sayacı yalnız ihtiyatlı tahmin olabilir.
- `STOP_LOSS` için statik `PRICE_FILTER`, `LOT_SIZE` ve market-notional preflight yapılır. Tetikleyicinin anlık piyasa fiyatına göre doğru tarafta olması canlı bir matching-engine kuralıdır; connector bunu tahmin etmez ve Binance rejection'ını korur.
- REST transport bütün V1 parametrelerini query string'de taşır; form body karıştırma desteği public yüzeyde yoktur.
- `RAW_REQUESTS` için ayrı response header bulunmadığından transport yalnız gerçek local wire attempt sayısını tutar. Faz 5 `exchangeInfo` rate-limit verisini kayıpsız döndürür; tracker bu server limitlerinden otomatik bir concurrency/throttle bütçesi türetmez.
- Paramsız public ticker çağrıları resmî sözleşmeye göre geçerli fakat yüksek ağırlıklıdır. Connector doğru dinamik ağırlığı izler; tüketicinin pahalı all-market çağrısını otomatik engellemez.
- Safe read retry backoff'u Faz 4'te deterministic exponential'dır; jitter ve merkezi concurrency budget ileriki performans/rate-limit ölçümleriyle sıkılaştırılabilir.
- Default client saat senkronizasyonu explicit `client/synchronize-time!` çağrısıdır; creation sırasında hidden network çağrısı yapılmaz. Uzun yaşayan client'larda offset zamanla değişebileceği için uygulama signed iş akışlarından önce/periyodik senkronizasyon planlamalıdır. Custom `:clock` kullanan tüketici kendi offset yönetiminden sorumludur.
- Client order id tekrar kullanım kilidi process-local ve client yaşam süresiyle sınırlıdır. Connector restart sonrasında önceki id'leri hatırlamaz; bot/application katmanı kalıcı ve global benzersiz id üretiminden sorumlu olacaktır.
- Reconciliation bütçesinin bitmesi emrin oluşmadığını kanıtlamaz. `unresolved` sonuçta aynı order yeniden POST edilmez; daha uzun manuel/query takibi veya User Data Stream gözlemi gerekir.
- Bounded REST reconciliation senkrondur ve çağıran thread'i delay süresince tutar. User Data Stream daha sonraki matching `executionReport` ile sonucu çözebilir; ilk senkron çağrının bekleme süresini geriye dönük azaltmaz.

## Filter ve Precision

- MARKET order `MIN_NOTIONAL`/`NOTIONAL` doğrulaması hareketli average/reference price'a bağlı olabilir. Yerel preflight ile server kararı arasında fiyat yarışı vardır; nihai otorite Binance'tir.
- V1 yalnız `PRICE_FILTER`, `LOT_SIZE`, `MARKET_LOT_SIZE`, `MIN_NOTIONAL` ve `NOTIONAL` filtrelerini uygular. Diğer filtreler raw olarak korunur ve tam yerel doğrulama iddiası yapılmaz.
- `PERCENT_PRICE`, `PERCENT_PRICE_BY_SIDE`, `ICEBERG_PARTS`, `MAX_NUM_ORDERS`, `MAX_NUM_ALGO_ORDERS`, `MAX_NUM_ICEBERG_ORDERS`, `MAX_POSITION` ve exchange/asset filtreleri Faz 6 yerel preflight kapsamında değildir; Binance bunlara dayanarak yerelde geçen bir emri reddedebilir.
- MARKET `quoteOrderQty`, yerel notional kontrolünde doğrudan tahmin olarak kullanılır. Gerçek base quantity, average price ve likidite ancak matching engine tarafında belirlenir.
- Order command'e verilen `symbol-info` bir `exchangeInfo` snapshot'ıdır ve connector hidden cache/refresh yapmaz. Eski snapshot ile yerel sonuç güncelliğini kaybedebilir; wire üzerindeki son karar Binance'e aittir.
- Binance yeni filtre veya enum ekleyebilir. Bilinmeyen değerler parse sırasında kaybedilmez; güvenli trading doğrulaması bunları açık limitation/validation sonucu olarak ele alır.

## WebSocket

- `!ticker@arr` 2026-03-26'da kaldırılmıştır. V1 bütün piyasa özeti için `!miniTicker@arr`, full ticker için sembol bazlı `<symbol>@ticker` kullanır.
- `!miniTicker@arr` yalnız değişen sembolleri içerir; başlangıç full snapshot REST'ten alınmalıdır.
- Partial depth yalnız top 5/10/20 seviyeyi gösterir. V1 diff stream + REST snapshot ile tam yerel order book reconstruction yapmaz.
- WebSocket bağlantıları 24 saatte sona erer; reconnect/restore sırasında kısa event boşluğu olabilir. UDS gap'i REST order/account sorgularıyla reconcile edilir.
- Market-data-only `data-stream.binance.vision` User Data Stream sağlamaz.
- Connection/subscription rate limits nedeniyle her UI/consumer için ayrı socket açılmaz; connector seviyesinde paylaşılır.
- Bounded event buffer varsayılan olarak 1024 eleman ve `drop-oldest` kullanır; `drop-newest` seçilebilir. Her iki politika da network callback'ini bloklamaz ve taşmada event kaybı oluşur; dropped sayacı izlenmeli, kritik gap REST reconciliation ile tamamlanmalıdır.
- JSON WebSocket akışları desteklenir; SBE binary market/account session'ları V1 kapsam dışıdır.
- Control requestleri tek manager üzerinden gönderilir; connector restore'u batch eder. Binance'in bağlantı başına 5 incoming-message/s sınırını aşacak yoğun manuel subscribe/unsubscribe kullanımı önerilmez.

## Henüz Doğrulanmamış Entegrasyon Noktaları

Aşağıdakiler ilgili faz exit gate'inde gerçek Spot Testnet credential ile doğrulanacaktır:

- Ed25519 signed `order/test` canlı kabulü (HMAC kabulü geçmiştir; Ed25519 canlı harness'i henüz yoktur).
- Gerçek command timeout/5xx altında exchange görünürlük süresi; Faz 7 deterministik simülasyonla doğrulanmıştır ancak canlı ortamda kasıtlı belirsiz command üretmek güvenli kabul kapsamına alınmamıştır.
- Reconnect sırasında gerçek ağ kesintisi altında restore ve event gap gözlemi. Deterministik forced-close/restore testi tamamlandı.

Bu maddeler Faz 1 protokol belirsizliği değildir; resmî sözleşmesi belirlenmiş, credential gerektiren entegrasyon kabul testleridir.

HMAC `userDataStream.subscribe.signature`, gerçek Testnet `FILLED executionReport` ve `outboundAccountPosition` Faz 8'de canlı olarak doğrulanmıştır.

Non-marketable LIMIT create/query/open-orders/cancel, UDS `NEW`/`CANCELED`, final query ve açık emir yokluğu Faz 9 full Spot Testnet release kabulünde canlı olarak doğrulanmıştır.
