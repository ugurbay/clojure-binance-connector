# binance-clj Mimari Kararları

## Durum

Faz 0–9, 2026-08-18 tarihinde tamamlanmıştır. Connector V1 full Spot Testnet release kapısını geçmiş ve `READY` durumuna alınmıştır. Faz 8, paylaşılan dayanıklı WebSocket bağlantıları ile güncel imzalı User Data Stream sözleşmesini; Faz 9 ise canlı LIMIT yaşam döngüsü, secret scan, benchmark ve soak kabulünü kapatmıştır. Güncel Binance sözleşmesinin araştırma ayrıntıları `BINANCE_RESEARCH.md`, sürüm temeli `BINANCE_VERSION.md`, SDK karşılaştırması `OFFICIAL_SDK_CROSSCHECK.md` içindedir.

## ADR-0001 — Proje ve Runtime Temeli

**Karar:** SDK, Clojure CLI / `deps.edn`, Clojure 1.12.5 ve JDK 25 üzerinde geliştirilecektir.

**Gerekçe:** Master plan JVM 25 hedefini belirler. Clojure 1.12.5, Faz 0 tarihinde doğrulanan güncel 1.12.x kararlı sürümdür. Projede Node.js veya frontend build zinciri bulunmayacaktır.

## ADR-0002 — Faz 0 Bağımlılıkları

Runtime bağımlılığı yalnız `org.clojure/clojure` olacaktır. Faz 0 araçları runtime artifact'ına dahil edilmez:

| Bağımlılık | Sürüm | Scope | Gerekçe |
|---|---:|---|---|
| `org.clojure/clojure` | 1.12.5 | runtime | Projenin sabit Clojure sürümü |
| `io.github.cognitect-labs/test-runner` | v0.5.1 / `dfb30dd` | test | `clojure.test` namespace keşfi ve standart exit code |
| `clj-kondo/clj-kondo` | 2026.05.25 | tooling | Statik analiz; proje alias'ı ile sürüm sabitleme |
| `dev.weavejester/cljfmt` | 0.16.4 | tooling | Format kontrolü ve mekanik düzeltme |

Yeni bir dependency ancak standart kütüphane/JDK ile çözümün bakım veya doğruluk maliyeti daha yüksekse eklenir ve bu belgeye kaydedilir.

## ADR-0003 — Test Katmanları

- `test/`: hızlı ve ağsız unit/contract testleri.
- `integration-test/`: ayrı alias ile çalışan entegrasyon testleri.
- Credential ve ağ gerektiren Testnet testleri ileriki fazlarda açıkça opt-in olacaktır.
- CI'ın varsayılan işi secret gerektirmeyen testleri çalıştıracaktır.

## ADR-0004 — Güvenli Environment Varsayılanı

`BINANCE_ENV=testnet` ve `ENABLE_LIVE_TRADING=false` güvenli varsayımlardır. Gerçek secret hiçbir örnek dosyada tutulmaz. Production order gönderimi ileriki fazlarda iki ayrı koşulla korunacaktır.

## ADR-0005 — Faz Sınırı

Faz 0 yalnız iskelet ve tooling içerir. HTTP client, JSON parser, signer veya Binance endpoint implementasyonu Faz 1 araştırma exit gate'i geçmeden seçilmez ya da yazılmaz.

## ADR-0006 — JDK 25 Geliştirme Ortamı

CI, Temurin JDK 25 kullanır. Windows geliştirme makinesinde sistem JDK'sını değiştirmeden çalışabilmek için `scripts/install-portable-jdk.ps1`, resmi Adoptium API'sinden JDK 25'i git tarafından ignore edilen `.toolchains/` dizinine kurabilir. `scripts/verify-phase-0.ps1`, bu JDK'yı `JAVA_CMD`, `JAVA_HOME` ve process-local `PATH` üzerinden seçer; sistem genelindeki environment değişkenlerini değiştirmez.

## ADR-0007 — Binance Sözleşme Kaynakları

Güncel Binance Spot API Docs davranış otoritesidir. Resmî OpenAPI yalnız ikincil şema kontrolü, resmî Python SDK ise lifecycle ve ergonomi karşılaştırmasıdır. OpenAPI-generated code veya model sınıfları üretilmeyecektir. Kaynak commit'leri `BINANCE_VERSION.md` içinde sabitlenir.

## ADR-0008 — REST Request ve Signing Sınırı

V1 signed REST istekleri bütün parametreleri deterministic, percent-encoded query string içinde gönderir. İmzalanan byte dizisi ile wire payload birebir aynıdır. REST ve WebSocket API canonicalization farklı kurallara sahip olduğundan iki ayrı bileşen olacaktır. Clock ve signer testlerde enjekte edilebilir olacaktır.

## ADR-0009 — Finansal Veri Modeli

Price, quantity, balance, commission ve notional alanları public API'de ve iç modelde `BigDecimal` olacaktır. Binance'e plain decimal string gönderilir. Unknown response alanları kaybedilmez; generated kapalı sınıf ağları kullanılmaz.

## ADR-0010 — Emir İdempotency ve Belirsizlik

Her order benzersiz `newClientOrderId` alır. Trading POST timeout, ilgili `5xx`, `-1006` veya `-1007` sonucu `unknown-execution` olur. Aynı order otomatik yeniden POST edilmez; order query ve User Data Stream ile reconcile edilir.

## ADR-0011 — User Data Stream Yolu

Legacy listen-key REST uçları kaldırıldığı için V1 User Data Stream, WebSocket API `userDataStream.subscribe.signature` ile açılır. Bu yol HMAC varsayılanını ve Ed25519 signer'ı destekler. Ed25519-only `session.logon` yolu birincil değildir.

## ADR-0012 — Güncel Market Stream Kapsamı

Kaldırılan `!ticker@arr` uygulanmayacaktır. Bütün piyasa özeti `!miniTicker@arr`, full 24 saat ticker sembol bazlı `<symbol>@ticker`, taker-emri bazlı aggregate trade `<symbol>@aggTrade`, best bid/ask `<symbol>@bookTicker` ve top depth partial depth streamleriyle sağlanır. Partial depth her event'te atomik top-5/10/20 görünümüdür; diff-depth continuity veya tam local order book doğruluğu atfedilmez. Tam local order book reconstruction V1 dışındadır.

## ADR-0013 — Faz 2 Public Client Sözleşmesi

Public facade `create-client`, `execute!`, `close!`, `closed?` ve secret-safe `client-config` fonksiyonlarından oluşur. Girdi/çıktılar immutable Clojure map'leridir. Lifecycle için client map içinde sınırlandırılmış tek bir atom bulunur; client oluşturmak ağ bağlantısı açmaz ve `close!` transport'u en fazla bir kez kapatır.

Config varsayılanı Testnet, request timeout `15000` ms ve `recv-window` `5000M`'dir. 15 saniye, Binance matching-engine'in 10 saniyelik belirsizlik penceresinin client tarafından daha erken kesilmemesi için seçilmiştir. Base URL override'ları yalnız güvenli `https`/`wss` URI kabul eder. HMAC secret ile private key aynı client config'inde birlikte verilemez.

## ADR-0014 — Endpoint Execution Semantiği

Endpointler qualified keyword id taşıyan declarative EDN map'leridir. Her spec `:execution :read|:command` ve türetilmiş `:retry-policy` taşır. `:command` her zaman `:never` olur; method `DELETE` olsa dahi cancel order otomatik retry alamaz. Bu karar, resmî Go ve JavaScript SDK common katmanlarında gözlenen method/global retry davranışına karşı emir güvenliği sınırıdır.

## ADR-0015 — Generic Pipeline ve Yan Etki Sınırı

Sabit sıra `endpoint → validation → serialization → timestamp → signing → transport → parsing → normalization` şeklindedir. Faz 2 request değeri transporttan bağımsız abstract map'tir. Clock, signer, parser ve transport fonksiyon/protocol enjeksiyonuyla değiştirilebilir. HTTP, JSON ve crypto implementasyonları sırasıyla Faz 3–4'e kadar çekirdeğe eklenmez.

## ADR-0016 — Hata Taksonomisi ve Secret Redaction

Public hatalar `ExceptionInfo` ve `:binance-clj/error` ile `validation`, `auth`, `transport`, `timeout`, `api`, `rate-limit`, `unknown-execution`, `configuration` veya `client-closed` kategorisi taşır. Beklenmeyen boundary exception'larının mesajı/data/cause zinciri dışarı aktarılmaz; yalnız güvenli cause class bilgisi korunur. Credential ve signature anahtarları recursive olarak redacted edilir.

## ADR-0017 — Faz 2 Bağımlılık Kararı

Faz 2 için yeni runtime dependency eklenmemiştir. URI doğrulama JDK, lifecycle ve data contracts Clojure core ile uygulanmıştır. HTTP, JSON ve crypto dependency seçimi ilgili fazların doğruluk gereksinimleriyle ayrıca değerlendirilecektir.

## ADR-0018 — Exact Decimal ve Wire Serialization

Finansal değerler `BigDecimal`, integer veya strict plain-decimal metinden parse edilir. `float`/`double`, exponent notation, whitespace ve `.1`/`1.` gibi shorthand biçimler reddedilir. Parse scale'i korur; wire serialization yalnız `BigDecimal.toPlainString` kullanır ve rounding yapmaz. Ayrı `normalize` fonksiyonu karşılaştırma gerektiğinde trailing zero'ları kaldırır.

## ADR-0019 — REST ve WebSocket Canonicalization Ayrımı

REST canonicalizer UTF-8 değerleri RFC 3986 kurallarıyla percent-encode eder. Map girdisi wire key'e göre sıralanır; endpoint tarafından verilen pair vector sırasını korur. Böylece aynı string hem imzalanır hem wire'a gönderilebilir.

WebSocket API canonicalizer parametre adlarını alfabetik sıralar, `signature` alanını dışarıda bırakır ve değerleri raw UTF-8 olarak tutar. Resmî non-ASCII WebSocket örneği SDK ortak URL-encoding yardımcılarından üstün davranış kaynağıdır.

## ADR-0020 — Zaman ve Receive Window

Config clock'u milisaniye döndürür. `:time-unit` varsayılanı `:millisecond`, isteğe bağlı değeri `:microsecond`dur. Mikro timestamp exact integer çarpımıyla üretilir. Server offset, `/time` request başlangıcı ile response sonunun midpoint'i üzerinden hesaplanır ve `synchronized-clock` sınırında atomik güncellenir.

`recvWindow` tek bir namespace'te doğrulanır: varsayılan `5000M`, maksimum `60000M`, en fazla üç ondalık milisaniye basamağı ve floating-point yasağı.

## ADR-0021 — Signer ve Anahtar Formatı

`Signer` protocol'ü algoritma ile UTF-8 payload signing davranışını ayırır. HMAC-SHA256 lowercase hex; Ed25519 Base64 üretir. Signer type'larının string temsili anahtar materyalini göstermez ve her sign çağrısında thread-safe JDK primitive'i oluşturulur.

Ed25519 V1 yalnız JDK `PrivateKey` veya unencrypted PKCS#8 `PRIVATE KEY` PEM kabul eder. Encrypted PKCS#8, OpenSSH ve raw seed formatları desteklenmez. RSA public yüzeye eklenmemiştir.

## ADR-0022 — Faz 3 Bağımlılık Kararı

Faz 3 için yeni runtime dependency eklenmemiştir. UTF-8, HMAC-SHA256, Ed25519, PKCS#8 ve Base64 JDK 25 API'leriyle; decimal ve canonicalization Clojure/JDK çekirdeğiyle uygulanmıştır.

## ADR-0023 — HTTP Client Yaşam Döngüsü

REST transport JDK 25 `java.net.http.HttpClient` kullanır. Bir client bütün isteklerde connection pool'u yeniden kullanır, redirect takip etmez, connect timeout'u client seviyesinde ve request timeout'u her request üzerinde uygular. `create-client` varsayılan transport'u oluşturur fakat ilk `execute!` çağrısına kadar ağ bağlantısı açmaz; `close!` JDK client kaynaklarını en fazla bir kez kapatır.

V1 signed ve unsigned REST parametrelerinin tamamı query string'dedir. `POST`, `PUT` ve `DELETE` dahil request body boş kalır. Signed request ancak signer'ın ürettiği exact `:signed-query` varsa wire'a çıkabilir.

## ADR-0024 — Precision-Safe JSON

JSON için resmî `org.clojure/data.json` 2.5.2 seçilmiştir. Parser `:bigdec true` ile decimal JSON tokenlarını `BigDecimal`, büyük integerları kayıpsız integer olarak okur; object key'lerini keyword yapar ve unknown alanları korur. Empty body `nil`, malformed veya trailing JSON normalized `:api` hatasıdır; raw body hata verisine eklenmez.

JSON yazımında Binance financial wire sözleşmesine uygun olarak `BigDecimal` plain decimal string'e dönüşür. `float`, `double` ve `Ratio` reddedilir.

## ADR-0025 — HTTP Error ve Retry Güvenliği

HTTP response sınıflandırması status, Binance numeric code ve endpoint `:execution`/`:retry-policy` değerlerini birlikte kullanır. Auth code'ları `:auth`; `418`/`429` ve rate-limit code'ları `:rate-limit`; diğer kesin API retleri `:api` olur.

Safe read istekleri transient network error, timeout ve `5xx` için config ile sınırlı exponential retry alır. `429`, yalnız parse edilebilir `Retry-After` header'ı varsa bu süreye uyarak retry edilir. `418` asla otomatik retry edilmez. Command timeout/network/`5xx`/`-1006`/`-1007` tek denemeden sonra `:unknown-execution` olur.

## ADR-0026 — Rate-Limit Observation State

Her gerçek wire attempt local `RAW_REQUESTS` yaklaşımı olarak `raw-request-count` ve endpoint static weight toplamına eklenir. `X-MBX-USED-WEIGHT-*`, `X-MBX-ORDER-COUNT-*` ve `Retry-After` response header'ları interval bazında parse edilerek authoritative gözlem state'ine yazılır. Binance REST ayrı bir RAW_REQUESTS response header'ı sağlamadığı için raw count yerel tahmindir; server limitleri ileride `exchangeInfo` ile eşleştirilir.

## ADR-0027 — Faz 4 Bağımlılık Kararı

Tek yeni runtime dependency `org.clojure/data.json` 2.5.2'dir. Resmî Clojure kütüphanesidir, dış transitif runtime dependency taşımaz ve BigDecimal parse seçeneği sağlar. HTTP, timeout, pooling ve TLS JDK 25 ile uygulanmıştır.

## ADR-0028 — Public Spot Endpoint Registry ve Facade

Faz 5'in yedi public REST ucu default registry'ye declarative spec olarak eklenir. Tüketici yüzeyi `binance-clj.spot` altında küçük facade fonksiyonları sunar; generic `execute!` genişletilebilirlik için korunur. Endpoint seçimi ve parametre doğrulaması wire'a çıkmadan önce yapılır.

Tek sembol, sembol dizisi ve tüm-market seçimleri Clojure değerleriyle ifade edilir. Binance'in `symbols` ve `permissions` parametreleri compact JSON array olarak yazılır, ardından mevcut RFC 3986 query encoder tarafından percent-encode edilir. Clojure ergonomisi için kebab-case `:symbol-status` ve `:show-permission-sets?`, wire'da resmî camelCase adlarına çevrilir.

## ADR-0029 — Dinamik Request Weight

Endpoint spec'teki `:weight` her zaman non-negative integer ve veri-odaklı kalır. Parametreye bağlı güncel ağırlıklar validation aşamasında `:request-weight` olarak çözülür; serialization bunu static ağırlığın önüne koyar. Böylece `ticker/24hr`, ticker price/book ve depth çağrılarında gerçek symbol count/limit, transport'un yerel rate-limit tahminine girer.

## ADR-0030 — Public Response Normalizasyonu

Parserlar Binance object/array şeklini endpoint bazında doğrular. Bilinen price, quantity, volume, notional ve filter alanları strict plain-decimal parser ile `BigDecimal` yapılır; timestamp/id/count alanları integer kalır. Depth level'larının ilk iki elemanı dönüştürülür ve ileriye dönük ek elemanlar korunur. Bilinmeyen object alanları hiçbir seviyede düşürülmez.

Malformed response finansal değerleri raw response'u sızdırmadan normalized `:api` hatası olur. Faz 5 yeni runtime dependency eklemez; Faz 4'ün JDK HTTP ve `data.json` altyapısını kullanır.

## ADR-0031 — Public Testnet Kabul Kapısı

Public Spot Testnet testi credential gerektirmez fakat ağ bağımlılığı nedeniyle `BINANCE_RUN_PUBLIC_TESTNET=true` ile opt-in'dir. Varsayılan integration işi ağsız ve deterministik kalır. Opt-in kapı ping, time, exchangeInfo, ticker price, 24h ticker, book ticker ve depth uçlarını `BTCUSDT` üzerinde gerçek Binance yanıtıyla doğrular.

## ADR-0032 — Config'ten Otomatik Signed REST Pipeline

Client config'inde `:api-secret` varsa HMAC-SHA256, `:private-key` varsa Ed25519 signer bir kez oluşturulur. Signed endpointler API key header'ı yanında validated parametreler, merkezi `recvWindow` ve request timestamp'ini aynı deterministic query içinde imzalar; wire'a yalnız bu exact signed query çıkar. Signer yoksa istek transporttan önce `:auth` hatasıyla durur.

Endpoint spec'leri signed olmanın yanında `:permission :trade|:user-data|:user-stream` bilgisini taşır. Bu bilgi gözlemlenebilir metadata içindir; Binance hesabındaki gerçek izinleri client varsaymaz ve server retlerini normalize eder.

## ADR-0033 — Signed Spot Endpoint ve Weight Sözleşmeleri

Faz 6 account, my trades, test order, new order, query order, cancel order ve open orders uçlarını default registry'ye ekler. Güncel resmî ağırlıklar endpoint/parametre bazında izlenir: account `20`, my trades order id ile `5` aksi halde `20`, test order commission hesabıyla `20` aksi halde `1`, new/cancel `1`, query `4`, open orders sembolle `6` aksi halde `80`.

`POST /api/v3/order/test` matching engine'de emir gerçekleştirmez ve otomatik retry almaz. `POST /api/v3/order` ile `DELETE /api/v3/order` state-changing command'dır; retry politikaları her zaman `:never` kalır.

## ADR-0034 — Exact Yerel Filter Preflight

V1 yalnız `PRICE_FILTER`, `LOT_SIZE`, `MARKET_LOT_SIZE`, `MIN_NOTIONAL` ve `NOTIONAL` kurallarını exact `BigDecimal` karşılaştırma, çarpma ve remainder ile uygular. Raw exchangeInfo decimal metinleri de strict parser'dan geçer; eksik, bozuk veya duplicate bilinen filtre sessizce atlanmaz. Minimum/maksimum sınırlar inclusive, tick/step hizası exact'tir ve connector hiçbir değeri yuvarlamaz.

MARKET base quantity için uygulanabilir notional filtresi varsa çağıran güncel bir `:reference-price` sağlamalıdır. `quoteOrderQty` yerel notional tahmini olarak kullanılabilir. Average price ve emir wire'a çıkana kadar oluşan fiyat yarışı nedeniyle Binance'in server doğrulaması nihai otoritedir.

## ADR-0035 — Symbol Snapshot Sorumluluğu

Order facade'ları çağırandan connector'ın `exchangeInfo` sonucundaki ilgili `symbol-info` map'ini ister. Böylece hidden cache, cache expiry veya trading sırasında otomatik network çağrısı oluşmaz. Symbol/status, Spot trading capability, order type, quote order quantity capability ve filtreler wire'dan önce kontrol edilir. Snapshot'ın güncelliği tüketici sorumluluğundadır; server tekrar doğrular.

## ADR-0036 — Client Order ID ve Production Çift Kilidi

Caller geçerli `new-client-order-id` vermediğinde her test/new order için `clj-` önekli 36 karakterlik benzersiz UUID tabanlı id üretilir. Kabul edilen tam karakter kümesi `[A-Za-z0-9._:/-]`, uzunluk `1..36`dır. Otomatik tekrar POST yapılmaz; ileriki reconciliation aynı id'yi kullanır.

Testnet command'ları sanal ortamda çalışabilir. Production new/cancel işlemleri ise hem `:environment :production` hem açık `:enable-live-trading? true` istemeden yerelde durdurulur. Bu guard read ve non-executing test-order çağrılarını kilitlemez.

## ADR-0037 — Auth ve Trading Hata Nedenleri

Numeric Binance code ve güvenli mesaj sınıflandırması public hata verisine kararlı `:binance-reason` ekler. API key/IP/permission reddi, geçersiz key biçimi, invalid signature/timestamp, order bulunamaması, cancel reddi ve insufficient balance ayrıştırılır. Değişebilir raw Binance mesajı dışarı taşınmaz; yetersiz bakiye ayrımı yalnız `-2010` mesajına dayanması nedeniyle yardımcı bir reason'dır, hata kategorisinin otoritesi değildir.

## ADR-0038 — Signed Testnet Kabul Güvenliği

Credential'lı entegrasyon `BINANCE_RUN_SIGNED_TESTNET=true` ile opt-in'dir. Test yalnız signed account read ve `POST /api/v3/order/test` MARKET quote quantity çağrısı yapar; gerçek new/cancel order göndermez. Quote miktarı canlı `exchangeInfo` notional filtrelerinden türetilir. Varsayılan integration işi credential ve ağ olmadan deterministik kalır; key/secret yalnız process environment'tan okunur ve loglanmaz.

Faz 6 yeni runtime dependency eklemez. Signed account ve non-executing HMAC `order/test` kabulü 2026-08-18 tarihinde gerçek Spot Testnet üzerinde geçmiştir.

## ADR-0039 — Explicit Server-Time Synchronization

Default client clock, ağ bağlantısı açmadan offset-aware bir wrapper ile oluşturulur. `client/synchronize-time!`, public `/api/v3/time` çağrısının yerel request başlangıcı ve response bitişi midpoint'ini kullanarak atomik offset günceller. Böylece Windows/system clock birkaç saniye ileride veya geride olsa bile sonraki signed timestamp server saatine yaklaşır.

Senkronizasyon açık ve gözlemlenebilirdir; client creation hidden network çağrısı yapmaz. Signed Testnet acceptance account çağrısından önce bunu otomatik çalıştırır. Uzun yaşayan uygulamalar signed işlem gruplarından önce/periyodik senkronize eder. Custom `:clock` enjekte eden tüketici clock ownership'i aldığı için kendi senkronizasyonunu sağlar.

## ADR-0040 — Order Lifecycle State Machine

Yüksek seviye `spot/submit-order!` sonucu kapalı bir lifecycle state sözleşmesi taşır: `pending`, `confirmed`, `rejected`, `unknown`, `reconciled`, `unresolved`. Geçerli geçişler `pending → confirmed|rejected|unknown` ve `unknown → reconciled|unresolved`dur. `unknown` senkron orchestration içinde ara durumdur; query ile kanıt bulunursa `reconciled`, bütçe veya güvenli sorgulama imkânı biterse `unresolved` olur.

Kesin API/validation reddi `rejected`, başarılı command response `confirmed`dır. Unknown sonrası order query'de kayıt görülmesi `reconciled` + `resolution :confirmed` olur. Tekrarlanan `-2013 order not found`, Memory → Database görünürlük gecikmesi için resmî bir üst sınır bulunmadığından hiçbir zaman kesin ret sayılmaz; `unresolved` + `resolution :unknown` olur.

## ADR-0041 — Exactly-One Command Attempt

`submit-order!`, `submit!` command sınırını tam bir kez çağırır. Timeout, network, ilgili `5xx`, `-1006`, `-1007` veya beklenmeyen submit boundary hatası aynı payload'ın ikinci POST'una dönüşmez. Generated/caller `newClientOrderId`, command'den önce ayrılır ve unknown exception data'sında güvenli biçimde korunur. Reconciliation yalnız signed `GET /api/v3/order` çağrısını `origClientOrderId` ile kullanır.

Client ayrıca başarılı yerel validation sonrasında her new-order client id'sini atomik process-local sette claim eder. Aynı client yaşam süresinde deterministic server rejection olsa dahi bu id yeniden command'e çıkamaz. Bu politika Binance'in yalnız açık emirler için benzersizlik sınırından daha muhafazakârdır ve istemsiz duplicate gönderimi önler.

## ADR-0042 — Bounded Query Fallback ve Rate-Limit Uyumu

Default fallback en fazla 5 logical query attempt kullanır. İlk başarısız sorgudan sonra delay 250 ms exponential artar ve local olarak 2000 ms'de sınırlanır. `429` ancak geçerli `Retry-After` varsa tekrar sorgulanabilir ve server süresi local cap'in üstünde olsa da korunur. `418`, auth/config/validation/client-closed gibi non-retryable sonuçlar query bütçesini hemen `unresolved` kapatır.

Query endpoint'i safe-read transport retry'sine sahip olduğundan bir logical reconciliation attempt birden fazla transient wire attempt içerebilir; command attempt sayısı yine daima 1'dir. Lifecycle `:events` yalnız event/state/attempt ile redacted category, numeric code, reason, status ve retry metadata'sını taşır; raw Binance message, request payload, signature veya credential taşımaz.

Faz 8 User Data Stream, aynı lifecycle modeline daha güçlü bir observation kaynağı olarak eklenmiştir. REST query fallback kaldırılmaz; stream gap/reconnect sırasında kontrollü yedek olarak kalır.

## ADR-0043 — Ayrı ve Paylaşılan WebSocket Kanalları

Market Streams ile WebSocket API farklı Binance uç noktaları ve wire sözleşmeleri olduğundan iki fiziksel bağlantı yöneticisi kullanılır. Her yönetici connector/client seviyesinde paylaşılır; UI veya consumer başına socket açılmaz. Client close, kayıtlı WebSocket kaynaklarını da idempotent kapatır.

## ADR-0044 — JDK Transport, Heartbeat ve Renewal

Yeni runtime dependency eklenmez; JDK 25 `java.net.http.WebSocket` kullanılır. Fragmented text frame'leri tek JSON mesajına birleştirilir. Server ping payload'ı exact pong ile yanıtlanır. 65 saniye server-ping sessizliği, `serverShutdown`, `eventStreamTerminated` veya beklenmeyen close reconnect tetikler. 24 saatlik server sınırından önce 23 saat 50 dakikada planlı renewal yapılır.

## ADR-0045 — Reconnect, Restore ve İmzalı UDS

Reconnect exponential backoff ve en fazla yüzde 25 jitter kullanır; explicit close reconnect üretmez. Market registry set olduğu için duplicate subscription wire'a çıkmaz ve restore tek `SUBSCRIBE` mesajıyla yapılır. UDS restore eski request'i tekrar kullanmaz; her bağlantıda synchronized client clock'tan yeni timestamp ve signer'dan yeni signature üretilir. `timestamp` integer, `recvWindow` exact JSON decimal number tokenıdır; float/double wire'a çıkmaz. Legacy listen-key endpointleri kullanılmaz.

## ADR-0046 — Bounded Event Sınırı

JDK network callback'i consumer kodunu çağırmaz ve bloklanmaz. Normalize edilen mesajlar varsayılan 1024 kapasiteli `ArrayBlockingQueue` içine non-blocking yazılır. Varsayılan `drop-oldest`, alternatif `drop-newest`tir; accepted/dropped/depth sayaçları snapshot'ta görünür. Block policy desteklenmez çünkü yavaş consumer'ın ping/pong ve bağlantı sağlığını durdurmasına izin verilmez.

## ADR-0047 — Event Normalizasyonu ve Geç Reconciliation

Market price/quantity/volume, order price/quantity/commission ve account balance alanları strict `BigDecimal` olur; timestamp/id integer kalır ve bilinmeyen alanlar korunur. UDS eventlerine stable kebab-case alias'lar eklenirken raw Binance anahtarları düşürülmez. Matching `executionReport`, Faz 7'de query bütçesi bitmiş `unresolved` lifecycle'ı `reconciled` + `resolution :observed` yapabilir; aynı order hiçbir zaman yeniden POST edilmez.

## ADR-0048 — Faz 9 Full Testnet Kabul Emri

Release acceptance sabit price/quantity kullanmaz. Güncel `exchangeInfo`, book ticker ve hesap bakiyesinden tick/step hizalı, minimum notional üstünde ve non-marketable bir BTCUSDT LIMIT GTC türetilir. Testnet SELL bakiyesi yeterliyse ask'in yüzde 2 üstü; değilse yeterli quote bakiyesiyle bid'in yüzde 2 altı BUY seçilir. Harness environment'ı kod içinde `:testnet` sabitler ve tek create/tek cancel güvenliğini korur.

## ADR-0049 — Release Gözlemlenebilirliği

Yeni runtime dependency eklenmez. Microbenchmark sabit warmup/batch ölçümüyle JSON parse+normalize, UDS normalize, bounded queue ve HMAC signing p50/p95/throughput baseline'ı verir. Kısa soak queue drop/depth, heap ve thread farkını ölçer. Secret scanner gerçek environment credential değerlerini yazdırmadan proje metinlerinde arar; exact değer bulunursa yalnız dosya adını bildirip release'i durdurur.

## ADR-0050 — Testnet Kabulünde Eventual Consistency

Başarılı new-order yanıtının hemen ardından `GET /api/v3/order` kısa süreli `-2013 ORDER_NOT_FOUND` döndürebilir; order query ve open-orders veri kaynağı `Memory → Database` zinciridir. Release harness bu durumu emir reddi saymaz. UDS `executionReport` gözleminden sonra yalnız safe read çağrılarını 250 ms aralıkla en fazla 20 kez gözlemler; beklenmeyen API hatasını anında yükseltir. New-order ve cancel command'ları hiçbir koşulda tekrar gönderilmez. Başarılı submit'ten alınan `orderId` cleanup sınırında saklanır; daha sonraki kabul adımı hata verirse aynı emir tek cancel denemesiyle kapatılır.

## ADR-0051 — Clojars Artifact ve Build Bağımlılık Sınırı

Clojars koordinatı doğrulanmış GitHub reverse-domain grubu üzerinden `io.github.ugurbay/binance-clj` olarak sabitlenir. `VERSION`, Git tag'i, POM ve JAR sürümü aynı tek kaynaktan türetilir. Paket source JAR'dır; yalnız `src`, runtime `resources`, MIT lisansı, AI geliştirme bildirimi ve Maven metadata'sını içerir. Test, integration, dev, local toolchain veya credential dosyaları artifact'e girmez.

## ADR-0052 — STOP_LOSS Felaket Koruması Public Sınırı

Spot `STOP_LOSS`, tetiklenince MARKET emir çalıştıran koşullu emir olarak public order map sözleşmesine eklenir. Connector yalnız statik ve kanıtlanabilir kuralları uygular: symbol `orderTypes`, `quantity`, `stopPrice`, `LOT_SIZE`, `PRICE_FILTER` ve çağıranın verdiği güncel reference price ile market-notional. Tetikleyicinin canlı piyasa fiyatına göre doğru tarafta olup olmadığı matching engine sorumluluğudur. Emir aynı new-order endpoint'ini, production çift guard'ını, benzersiz client order id'yi, tek POST ve query/UDS reconciliation politikasını kullanır; ayrı veya daha gevşek bir transport yolu yoktur.

`io.github.clojure/tools.build` ve `slipset/deps-deploy` yalnız `:build` alias'ında build-time dependency'dir; connector runtime classpath'ine veya üretilen POM dependency listesine girmez. `tools.build` tekrarlanabilir JAR/POM üretimi ve yerel kurulum için, `deps-deploy` ise Clojars'ın kullanıcı adı + deploy-token akışıyla tek artifact/POM yüklemesi için seçilmiştir. Yayın script'i immutable release riskine karşı temiz worktree, exact tag, tam Faz 9 kapısı, secret scan ve dış tüketici çözümlemesini zorunlu tutar.
