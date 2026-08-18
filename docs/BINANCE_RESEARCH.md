# Binance Spot Faz 1 Araştırması

## Durum

Faz 1 araştırması **2026-08-18** tarihinde tamamlandı. İnceleme yalnız resmî Binance kaynaklarına dayanır. Sabitlenen commit'ler [BINANCE_VERSION.md](BINANCE_VERSION.md), V1 uçları [BINANCE_ENDPOINT_MATRIX.md](BINANCE_ENDPOINT_MATRIX.md) içindedir.

Kaynak önceliği:

1. [Resmî Spot API belgeleri](https://github.com/binance/binance-spot-api-docs)
2. [Resmî Spot OpenAPI](https://github.com/binance/binance-api-swagger)
3. [Resmî Python connector](https://github.com/binance/binance-connector-python)

## REST Ortamları ve Transport

Production REST ana adresi `https://api.binance.com`'dur. `api-gcp.binance.com` ve `api1`–`api4.binance.com` alternatifleri vardır; Binance alternatif `api1`–`api4` adreslerinin daha iyi performans gösterebildiğini fakat daha az kararlı olabildiğini belirtir. `https://data-api.binance.vision` yalnız public market data içindir.

Spot Testnet REST adresleri `https://testnet.binance.vision/api` ve `https://api1.testnet.binance.vision/api`'dir. Connector config'i host'u saklayacak; endpoint path'i `/api/v3/...` olarak kalacaktır.

- Yanıt biçimi V1'de JSON'dur; SBE kapsam dışıdır.
- Zaman alanları varsayılan olarak milisaniyedir. `X-MBX-TIME-UNIT: MICROSECOND` seçeneği vardır, ancak V1 iç varsayılanı milisaniye olacaktır.
- `GET` parametreleri query string'dedir.
- `POST`, `PUT` ve `DELETE` parametreleri query string veya `application/x-www-form-urlencoded` body'de olabilir.
- Query ve body birlikte kullanılırsa aynı isimde query parametresi üstündür.

### V1 serialization kararı

V1 signed REST istekleri bütün parametreleri tek bir deterministic query string içinde gönderecektir. Böylece Binance'in query ile body'yi ayraçsız birleştiren imza kuralındaki iki parçalı payload belirsizliği ortadan kalkar. İmzalanan UTF-8 byte dizisi ile gerçekten gönderilen encoded query birebir aynı olacaktır.

## Security ve İmzalama

Security tipleri `NONE`, `TRADE`, `USER_DATA` ve `USER_STREAM`'dir. `NONE` dışındaki REST çağrıları imzalıdır ve API key `X-MBX-APIKEY` header'ında gönderilir. `TRADE` izni yeni API key'de varsayılan olarak açık değildir.

Binance HMAC-SHA256, RSA ve Ed25519 key tiplerini destekler. V1:

- HMAC-SHA256 tam destek,
- Ed25519 doğrulanmış signer altyapısı,
- RSA yalnız gelecekteki genişleme noktası

sunacaktır.

### REST canonicalization

1. `nil` parametreler çıkarılır; anahtar ve değerler Binance'in beklediği biçime çevrilir.
2. Finansal değerler plain decimal string olur; scientific notation veya sessiz rounding yoktur.
3. Parametreler stabil bir sırada `name=value` çiftleri olarak percent-encode edilir.
4. Non-ASCII dahil encoded payload'ın UTF-8 byte'ları imzalanır.
5. HMAC sonucu lowercase hex olur. Ed25519 sonucu Base64 olur ve query'ye eklenirken percent-encode edilir.
6. `signature` son parametre olarak eklenir.

REST sunucusu belirli bir parametre sırası şart koşmaz; proje deterministik test ve gözlemlenebilirlik için sabit sıra kullanır. Kritik koşul, imzalanan encoded payload ile gönderilen payload'ın aynı olmasıdır. HMAC signature değeri case-insensitive; API key, secret ve payload case-sensitive'dir.

### WebSocket API canonicalization

WebSocket API, REST'ten farklı bir sözleşmedir:

1. `params` içinden `signature` çıkarılır.
2. Parametreler isimlerine göre alfabetik sıralanır.
3. Değerler UTF-8 ile `name=value&...` payload'ına dönüştürülür.
4. HMAC sonucu hex; RSA/Ed25519 sonucu Base64 olarak `params.signature` içine konur.

Bu payload URL query'si değildir; REST percent-encoding fonksiyonu WebSocket API için yeniden kullanılmayacaktır. İki canonicalizer ayrı namespace ve ayrı resmî test vektörleriyle doğrulanacaktır.

## Timestamp ve `recvWindow`

- Signed isteklerde `timestamp` zorunludur; milisaniye veya mikrosaniye olabilir.
- `recvWindow` her zaman milisaniye cinsindedir, üç ondalık haneye kadar mikrosaniye hassasiyetini ifade edebilir.
- Varsayılan `recvWindow` 5000 ms, üst sınır 60000 ms'dir. Binance 5000 ms veya daha küçük değer önerir.
- Sunucu zamanı isteği alırken ve matching engine'e iletmeden önce tekrar kontrol eder.
- Timestamp sunucu zamanından en fazla 1 saniye ileride olmalı ve `serverTime - timestamp <= recvWindow` koşulunu iki kontrolde de sağlamalıdır.

V1 varsayılanı 5000 ms olacaktır. `GET /api/v3/time` ile ölçülen offset injectable clock üzerinde tutulacak; `-1021 INVALID_TIMESTAMP` kör retry sebebi olmayacak, önce offset yeniden ölçülecektir.

## Rate Limit ve Response Metadata

`exchangeInfo` güncel `RAW_REQUESTS`, `REQUEST_WEIGHT` ve `ORDERS` limitlerini bildirir. Statik endpoint weight'i yalnız planlama girdisidir; gerçek kullanım response metadata'dan izlenir.

- REST weight IP bazlıdır ve `X-MBX-USED-WEIGHT-<interval>` header'larıyla izlenir.
- Başarılı order yanıtları `X-MBX-ORDER-COUNT-<interval>` taşıyabilir; reddedilen order yanıtlarında header garanti değildir.
- Unfilled order count account bazlıdır ve aynı hesaptaki API key'ler arasında paylaşılır.
- `429` sonrasında backoff zorunludur. REST `Retry-After` saniye cinsindedir.
- Tekrarlanan ihlaller `418` IP ban üretir; süre 2 dakikadan 3 güne kadar büyüyebilir.
- WebSocket API'de `retryAfter`, retry edilebilecek epoch timestamp'tir; REST header semantiğiyle karıştırılmaz.
- WebSocket API'ye bağlantı açmak 2 request weight tüketir ve varsayılan yanıtlar `rateLimits` alanını içerir.

2026-04-02 değişikliğiyle seçili trading çağrılarında başarılı request weight 0, başarısız çağrıda endpointte belgelenen weight geçerlidir. V1 için `POST /api/v3/order` ve `DELETE /api/v3/order` bu kurala dahildir. Order count etkisi devam eder; connector hem statik worst-case hem response header'ını tutacaktır.

## Hata ve Emir Güvenliği

REST hata gövdesi `{code, msg}` biçimindedir. Mesaj değişebilir; numeric code programatik sınıflandırmanın temelidir.

| Durum | Sınıflandırma | V1 davranışı |
|---|---|---|
| Validation / `-1013` | kesin red | Yerel/API validation hatası |
| `-1021` | timestamp | Offset yenile, yeni kullanıcı eylemi olmadan trading POST'u tekrar etme |
| `-1022` | signature | Auth/encoding hatası; retry yok |
| `429` | rate limit | `Retry-After` ve merkezi limiter ile bekle |
| `418` | IP ban | Ban bitimine kadar durdur |
| `-1006`, `-1007` | unknown execution | Order query/UDS ile reconcile et |
| Trading timeout veya ilgili `5xx` | unknown execution | Aynı order'ı körlemesine tekrar POST etme |
| Diğer `4xx` | client/API error | Kod ve mesajı normalize et |

Binance matching engine yanıtı 10 saniye içinde dönmezse `-1007 TIMEOUT` verebilir; emir gerçekleşmiş olabilir. Benzer biçimde `5xx`, execution sonucunu garanti etmez. Her yeni emirde benzersiz `newClientOrderId` kullanılacak ve belirsizlik `GET /api/v3/order` veya User Data Stream ile çözülecektir.

## Spot Filtreleri ve Finansal Doğruluk

V1, `exchangeInfo.symbols[].filters` içinden aşağıdakileri uygular:

| Filtre | Yerel kontrol |
|---|---|
| `PRICE_FILTER` | `minPrice`, `maxPrice`, `price % tickSize == 0`; sıfır değer ilgili kuralı kapatır |
| `LOT_SIZE` | `minQty`, `maxQty`, `quantity % stepSize == 0` |
| `MARKET_LOT_SIZE` | MARKET quantity için market'e özel min/max/step |
| `MIN_NOTIONAL` | `price * quantity >= minNotional`; `applyToMarket` ve `avgPriceMins` dikkate alınır |
| `NOTIONAL` | min/max notional ve MARKET uygulama bayrakları |

Price, quantity, balance, commission ve notional alanları `BigDecimal` olur. Geçersiz değer otomatik yuvarlanmaz. `tickSize`/`stepSize` divisibility, decimal scale tahminiyle değil tam `BigDecimal` remainder kontrolüyle yapılır.

MARKET notional kontrolü moving average veya reference price kullanabilir. 2026 davranışında `MIN_NOTIONAL` ve `NOTIONAL`, mevcut ve non-null olduğunda reference price kullanır. Connector güncel server fiyatını tam olarak öngöremez; yerel kontrol best-effort preflight'tır ve Binance nihai otoritedir. Bilinmeyen filtreler kayıpsız korunacak, trading doğrulamasında sessizce “tam doğrulandı” sayılmayacaktır.

## WebSocket Streams

Production market streams adresleri `wss://stream.binance.com:9443` ve `wss://stream.binance.com:443`'tür. Raw bağlantı `/ws/<streamName>`, combined bağlantı `/stream?streams=a/b` kullanır. Stream isimleri lowercase olmalıdır. Testnet base `wss://stream.testnet.binance.vision`; V1 combined `/stream` bağlantısını ve canlı `SUBSCRIBE`/`UNSUBSCRIBE` mesajlarını kullanacaktır.

- Bağlantı ömrü 24 saattir; planlı renewal gerekir.
- Server 20 saniyede bir ping frame yollar; pong aynı payload ile mümkün olduğunca hızlı gönderilmelidir.
- Pong 1 dakika içinde gelmezse bağlantı kesilir.
- Incoming limit 5 message/s'dir; ping, pong ve control JSON mesajları sayılır.
- Bağlantı başına en fazla 1024 stream ve IP başına 5 dakikada 300 bağlantı denemesi vardır.
- `serverShutdown` gelince yeni bağlantı hızla kurulmalı ve subscriptions restore edilmelidir.
- Reconnect exponential backoff + jitter kullanacak; kullanıcı kapatması reconnect tetiklemeyecektir.

### Stream kapsamı değişikliği

Master plandaki `!ticker@arr`, Binance changelog'una göre **2026-03-26 tarihinde kaldırıldı** ve güncel stream dokümanında yoktur. V1 güncel karşılık olarak:

- bütün piyasa özeti: `!miniTicker@arr`,
- tam 24 saat istatistiği gereken sembol: `<symbol>@ticker`,
- seçili sembol best bid/ask: `<symbol>@bookTicker`,
- seçili sembol top depth: `<symbol>@depth5`, `@depth10` veya `@depth20` ve opsiyonel `@100ms`

kullanacaktır. `!miniTicker@arr` yalnız o intervalde değişen sembolleri içerir; tam snapshot değildir.

## WebSocket API ve User Data Stream

Production WebSocket API `wss://ws-api.binance.com:443/ws-api/v3`, Testnet `wss://ws-api.testnet.binance.vision/ws-api/v3` adresindedir. Bağlantı ömrü, ping/pong ve `serverShutdown` kuralları market streams ile aynıdır.

Eski Spot listen-key REST uçları (`POST/PUT/DELETE /api/v3/userDataStream`) **2026-02-20 tarihinde kaldırıldı**. Güncel UDS subscription WebSocket API üzerinden yapılır:

| Yöntem | Key desteği | V1 kararı |
|---|---|---|
| `userDataStream.subscribe.signature` | Signed request; WebSocket API'nin HMAC/RSA/Ed25519 key tipleri | **Birincil**; HMAC varsayılanı ve Ed25519 ile çalışır |
| `session.logon` + `userDataStream.subscribe` | Yalnız Ed25519 authenticated session | İkincil/ileriki optimizasyon |

Signature subscription parametreleri `apiKey`, `timestamp`, `signature` ve opsiyonel `recvWindow`; weight 2'dir. Aynı account için connection başına yalnız bir aktif subscription olabilir. Session başına 1000 eşzamanlı ve toplam yaşam döngüsünde 65535 subscription limiti vardır. `userDataStream.unsubscribe` belirli `subscriptionId` veya tüm subscriptions için kullanılır.

UDS event envelope `{subscriptionId, event}` biçimindedir. V1 normalize eder:

- `executionReport`
- `outboundAccountPosition`
- `balanceUpdate`
- `eventStreamTerminated`

`externalLockUpdate` ve bilinmeyen gelecek eventleri raw metadata kaybedilmeden generic event olarak iletilir. `eventStreamTerminated`, disconnect veya `serverShutdown` reconnect/restore akışını tetikler; subscription restore tamamlanana kadar order reconciliation REST query ile desteklenir.

## Spot Testnet

Spot Testnet API key `https://testnet.binance.vision/` üzerinden oluşturulur. Yalnız `/api/*` desteklenir; `/sapi/*` yoktur. Fonlar sanaldır ve transfer edilemez.

- IP/order limitleri ve filtreler production'a genellikle benzerdir ama `exchangeInfo` düzenli sorgulanmalıdır.
- Testnet yaklaşık ayda bir, ön bildirim olmadan sıfırlanır; pending ve executed order'lar silinir, sanal bakiyeler yenilenir.
- API key'ler reset sırasında korunur.
- Testnet live exchange ile her zaman senkron değildir; ayrı changelog tutulur.
- Production base URL veya production credential Testnet varsayılanına otomatik fallback yapamaz.

## Resmî Python SDK Analizi

### Benimsenecek yaklaşımlar

- REST, WebSocket API ve WebSocket Streams için ayrı configuration/lifecycle.
- Tek Spot facade altında lazy client yüzeyleri.
- Persistent HTTP session ve açık WebSocket close semantics.
- Response data yanında status/header/rate-limit metadata envelope.
- Connection, subscription ve callback registry ayrımı.
- Reconnect sonrası market subscription restore fikri.
- Her endpoint için unit fixture; config, auth, response ve error için ortak testler.
- Küçük, çalıştırılabilir örneklerin endpoint türlerine göre ayrılması.

### Değiştirilerek uygulanacak yaklaşımlar

- Python SDK'nın generated Pydantic model ağı yerine immutable Clojure map'leri ve küçük normalizer'lar kullanılacak.
- `float` finansal alanlar yerine yalnız `BigDecimal` kullanılacak.
- Genel retry sayısı trading güvenliğiyle ayrıştırılacak; POST order otomatik retry almayacak.
- Hata sınıfları HTTP status yanında Binance code, headers, retry metadata ve `unknown-execution` durumunu koruyacak.
- WebSocket callback yürütmesi bounded queue/backpressure policy üzerinden izole edilecek.

### Kopyalanmayacak yaklaşımlar

- OpenAPI-generated bir sınıf/model dosyası per endpoint/response.
- Sunucu şema değişince raw map'e sessiz fallback.
- Global mutable connection registry.
- Secret veya signature içeren request payload'ını loglamak.
- Sabit retry davranışını bütün HTTP methodlarına uygulamak.

## Faz 2–8 İçin Sabit Kararlar

1. Endpointler declarative EDN registry ile tanımlanır.
2. REST ve WebSocket API signing canonicalizer'ları ayrıdır.
3. Financial JSON string'leri `BigDecimal`, timestamp/ID değerleri integer olarak parse edilir.
4. Testnet varsayılandır; production order çift guard ister.
5. Trading timeout/`5xx`/`-1006`/`-1007` sonucu `unknown-execution` olur.
6. `newClientOrderId` zorunlu ve benzersiz üretilir.
7. Rate-limit state statik weight + response metadata ile merkezi tutulur.
8. User Data Stream'in birincil yolu `userDataStream.subscribe.signature`'dır.
9. `!ticker@arr` implementasyonu yapılmaz; güncel stream seti kullanılır.
10. OpenAPI generated code veya Python SDK modeli Clojure'a çevrilmez.

## Faz 3 Signing Doğrulama Kaydı

2026-08-18 tarihinde sabitlenen Spot API Docs commit'i üzerinden aşağıdaki davranışlar tekrar doğrulandı ve contract testine bağlandı:

- REST signature payload, query string ile form body'nin ayraç eklenmeden birleşimidir. V1 bütün parametreleri query string'e koyduğu için signed payload ile wire query birebir aynıdır.
- REST non-ASCII değerleri imzalanmadan önce percent-encode edilir. Resmî ASCII ve full-width Unicode `POST /api/v3/order` HMAC sonuçları testte birebir üretildi.
- WebSocket API parametre adları alfabetik sıralanır, `signature` dışarıda bırakılır ve değerler UTF-8 byte olarak percent-encode edilmeden imzalanır. Resmî HMAC sonucu birebir üretildi.
- Signed timestamp milisaniye veya mikrosaniye olabilir. `recvWindow` milisaniye cinsindedir; varsayılan 5000, maksimum 60000 ve micro precision için en fazla üç decimal basamak kabul eder.
- Resmî Ed25519 örnekleri private-key dosyasını yayınlamadığı için Binance imzasını yeniden üretmek mümkün değildir. Crypto primitive doğrulaması IETF RFC 8032 bölüm 7.1 test 1 ile yapıldı; Binance payload canonicalization bağımsız test edildi.

Kaynaklar: [Spot REST signed request](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/rest-api.md#signed-endpoint-security), [Spot WebSocket API request security](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/web-socket-api.md#request-security), [RFC 8032 test vectors](https://www.rfc-editor.org/rfc/rfc8032.html#section-7.1).

## Faz 4 HTTP, Error ve Rate-Limit Doğrulama Kaydı

2026-08-18 tarihinde sabitlenen Spot API Docs commit'i ve güncel JDK/Clojure resmî kaynakları üzerinden aşağıdaki kararlar contract testine bağlandı:

- Binance REST `4xx` yanıtları caller/API kaynaklıdır; `429` rate-limit aşımı, `418` devam eden ihlal sonrası IP ban anlamına gelir.
- `5xx` kesin başarısızlık değildir. State-changing command için execution sonucu `UNKNOWN` olabilir ve aynı command otomatik gönderilemez.
- `429` ve `418` yanıtlarında `Retry-After` saniye cinsindedir. Safe read `429` ancak bu header parse edilebiliyorsa bekleyip yeniden denenir; `418` otomatik retry dışıdır.
- `X-MBX-USED-WEIGHT-(intervalNum)(intervalLetter)` request-weight gözlemini, `X-MBX-ORDER-COUNT-*` başarılı order-count gözlemini taşır. Rejected response order-count header'ı içermeyebilir.
- JDK `HttpClient` instance'ı kendi connection pool'unu yönetip istekler arasında yeniden kullanır; request başına client yaratılmaz.
- `data.json` `:bigdec true` decimal JSON number tokenlarını `Double` yerine `BigDecimal` olarak okur. Faz 4 parser'ı bu seçeneği zorunlu kullanır.

Kaynaklar: [Spot REST HTTP ve rate-limit sözleşmesi](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/rest-api.md#http-return-codes), [JDK 25 HttpClient](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html), [Clojure data.json](https://github.com/clojure/data.json/tree/data.json-2.5.2).

## Resmî Go ve JavaScript SDK Çapraz Kontrolü

2026-08-18 tarihinde resmî Go Spot `1.10.0`/common `2.6.0` ve JavaScript Spot `32.0.1`/common `2.4.5` kaynakları kod seviyesinde incelendi. Ayrıntılı kayıt `OFFICIAL_SDK_CROSSCHECK.md` içindedir.

Çapraz kontrol; ayrı REST/WS config ve lifecycle, response metadata, finansal wire stringleri, signer çeşitleri, connection/subscription registry, `serverShutdown` ve reconnect/restore yönlerini doğruladı. İki generated model ağı da Clojure public API tasarımı olarak uygun bulunmadı.

Bir güvenlik kararı sıkılaştırıldı: retry güvenliği HTTP metodundan çıkarılamaz. Go common katmanı `500`–`504` response'larını metoda bakmadan, JavaScript common katmanı `GET` ve `DELETE` için retry edebilir. `DELETE /api/v3/order` state-changing bir command olduğundan binance-clj endpointleri `:execution :read|:command` ve `:retry-policy` taşır; hiçbir `:command` otomatik retry alamaz.

WebSocket API signing'de iki SDK alfabetik sırayı doğrulasa da ortak URL-encoding yardımcıları, resmî non-ASCII örnekteki raw UTF-8 payload ile ayrışır. Kaynak önceliği gereği resmî dokümantasyon esas alınır; Phase 3'te ASCII ve Unicode resmi vektörleri ayrı test edilir.

## Faz 8 WebSocket Yeniden Doğrulama Kaydı

2026-08-18 tarihinde resmî Spot API Docs `976cc580553890e92031b77306147c0ed1de5a46` HEAD'i tekrar doğrulandı. Market Streams ve WebSocket API bağlantıları 24 saat geçerlidir; server 20 saniyede ping gönderir, aynı payload ile pong ister ve 1 dakika içinde pong yoksa bağlantıyı keser. Market bağlantısı en fazla 1024 stream ve 5 client message/s kabul eder. WebSocket API `serverShutdown` eventini planlı kesintiden 10 dakika önce gönderir.

User Data Stream'in güncel primary yolu `userDataStream.subscribe.signature` olmaya devam etmektedir. Request `apiKey`, `timestamp`, `signature` ve opsiyonel en fazla 60000 ms `recvWindow` taşır; weight 2'dir. Connection başına aynı account için tek subscription, session başına 1000 aktif ve yaşam boyunca 65535 toplam subscription sınırı vardır. Eventler `{subscriptionId,event}` envelope'undadır.

Implementasyon bu nedenle pong'u transport callback'inde gecikmeden yollar, market restore'u batch eder, UDS restore'da yeni imza üretir ve 23 saat 50 dakikada planlı renewal başlatır. Anahtarsız Spot Testnet `BTCUSDT@bookTicker` canlı kabulü aynı tarihte geçmiştir.
