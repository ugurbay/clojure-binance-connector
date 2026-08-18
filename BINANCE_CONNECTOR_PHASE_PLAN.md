# Clojure Binance Connector — Fazlandırılmış Uygulama Planı

**Proje:** `binance-clj`
**Hedef:** Binance Spot için idiomatik, data-oriented, güvenli ve test edilmiş native Clojure connector/SDK
**Kapsam:** Yalnızca Binance Connector; trading botu, Hyper/Datastar UI ve application service katmanları bu planın dışındadır.
**Varsayılan ortam:** Binance Spot Testnet
**Plan durumu:** Faz 0–9 tamamlandı (2026-08-18); Connector V1 `READY`, bot aşamasına devir kapısı açık

---

## 1. Başarı Tanımı

Connector tamamlandığında aşağıdaki yetenekler birlikte sağlanmış olmalıdır:

- Binance Spot REST public, market, account ve manuel trading uçları çalışır.
- HMAC-SHA256 imzalama çalışır; Ed25519 için doğrulanmış signer altyapısı bulunur.
- Bütün finansal değerler `BigDecimal` ile, scientific notation üretmeden işlenir.
- Emirler Binance filtrelerine göre yerel olarak doğrulanır.
- Rate-limit başlıkları ve `429`/`418` davranışları güvenli biçimde yönetilir.
- Timeout veya `5xx` sonrası emir sonucu `UNKNOWN` kabul edilir; körlemesine tekrar emir gönderilmez.
- Market ve User Data WebSocket akışları reconnect ve subscription restore ile çalışır.
- Testnet üzerinde gerçek LIMIT emir yaşam döngüsü tamamlanır.
- Unit, mock ve integration testleri geçer; secret sızıntısı bulunmaz.
- Baseline performans sonuçları belgelenir.

Bu koşullar sağlanmadan bot uygulamasının kodlanmasına başlanmayacaktır.

---

## 2. Değişmez Mimari ve Güvenlik Kuralları

1. Kaynak önceliği: Binance resmi Spot dokümantasyonu → resmi OpenAPI → resmi Python SDK → diğer resmi SDK'lar → proje kararları.
2. Üçüncü taraf Binance kütüphaneleri davranış kaynağı olarak kullanılmaz.
3. Dokümantasyon ile SDK çelişirse resmi API dokümantasyonu esas alınır ve çelişki kaydedilir.
4. Connector, bot ve UI katmanlarını bilmez; public Clojure API üzerinden bağımsız çalışır.
5. `float` ve `double` finansal alanlarda kullanılmaz.
6. API key, secret, signature ve özel anahtar hiçbir log, hata mesajı veya test çıktısına girmez.
7. Production trading varsayılan olarak kapalıdır; testlerin varsayılan hedefi Testnet'tir.
8. Futures, Margin, Options, otomatik trading ve tüm Binance ürün ailesini kapsayan kod üretimi V1 dışıdır.
9. Trading POST isteklerinde genel/agresif otomatik retry uygulanmaz.
10. Her yeni dependency küçük tutulur ve gerekçesi belgelenir.

---

## 3. Hedef Dizin Yapısı

Kodlama fazlarında aşağıdaki yapı kademeli olarak kurulacaktır:

```text
Clojure Binance Connector/
├── AGENTS.md
├── README.md
├── deps.edn
├── .env.example
├── .gitignore
├── docs/
│   ├── BINANCE_RESEARCH.md
│   ├── BINANCE_ENDPOINT_MATRIX.md
│   ├── BINANCE_VERSION.md
│   ├── ARCHITECTURE.md
│   ├── PERFORMANCE_BASELINE.md
│   └── KNOWN_LIMITATIONS.md
├── src/binance_clj/
│   ├── client.clj
│   ├── config.clj
│   ├── encoding.clj
│   ├── decimal.clj
│   ├── time.clj
│   ├── json.clj
│   ├── errors.clj
│   ├── rate_limit.clj
│   ├── filters.clj
│   ├── auth/
│   │   ├── signer.clj
│   │   ├── hmac.clj
│   │   └── ed25519.clj
│   ├── transport/
│   │   ├── http.clj
│   │   └── websocket.clj
│   └── spot/
│       ├── general.clj
│       ├── market.clj
│       ├── account.clj
│       ├── trade.clj
│       ├── streams.clj
│       └── user_stream.clj
├── test/binance_clj/
├── integration-test/binance_clj/
└── dev/
```

Namespace sınırları uygulama sırasında doğrulanacak; büyük ve çok sorumluluklu namespace oluşturulmayacaktır.

---

## 4. Faz Özeti ve Bağımlılık Sırası

| Faz | Başlık | Ana çıktı | Sonraki fazın ön koşulu |
|---:|---|---|---|
| 0 | Proje iskeleti ve tooling — **TAMAMLANDI** | Çalışan boş SDK projesi | Build, test, lint, format geçer |
| 1 | Resmi Binance araştırması — **TAMAMLANDI** | Araştırma, sürüm ve endpoint belgeleri | Belirsiz kritik davranış kalmaz |
| 2 | Domain sözleşmeleri ve çekirdek model — **TAMAMLANDI** | Public API ve endpoint engine sözleşmesi | Saf çekirdek testleri geçer |
| 3 | Encoding, zaman ve authentication — **TAMAMLANDI** | Doğrulanmış signing pipeline | Resmi test vektörleri geçer |
| 4 | HTTP transport, hata ve rate limit — **TAMAMLANDI** | Güvenli generic REST yürütücüsü | Mock transport testleri geçer |
| 5 | Public Spot REST — **TAMAMLANDI** | General ve market data API | Public Testnet testleri geçer |
| 6 | Signed REST ve emir güvenliği — **TAMAMLANDI** | Account/trading API + filters | Test order ve yerel validation geçer |
| 7 | Emir belirsizliği ve reconciliation — **TAMAMLANDI** | Güvenli order lifecycle | Timeout/5xx senaryoları geçer |
| 8 | WebSocket ve User Data Stream — **TAMAMLANDI** | Dayanıklı realtime bağlantılar | Reconnect/restore testleri geçer |
| 9 | Testnet kabul, performans ve release gate — **TAMAMLANDI** | Botun kullanabileceği SDK | Bütün SDK exit gate'leri geçer |

Fazlar sırayla uygulanır. Bir fazın exit gate'i başarısızsa sonraki faza geçilmez.

---

## 5. Ayrıntılı Fazlar

### Faz 0 — Proje İskeleti ve Geliştirme Altyapısı

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_0_EXIT_GATE.md`.

**Amaç:** Uygulama özelliği yazmadan tekrarlanabilir bir Clojure geliştirme zemini kurmak.

**İşler:**

- JDK 25 ve güncel kararlı Clojure 1.12.x sürümünü ortamda doğrula.
- `deps.edn`, kaynak/test yolları ve ayrı integration-test alias'ını oluştur.
- Test runner, `clj-kondo` ve `cljfmt` komutlarını tanımla.
- `test`, `integration-test`, `lint`, `format-check`, `benchmark` ve REPL/dev komutlarını belgeleyip çalıştırılabilir hale getir.
- Root `AGENTS.md`, `.gitignore`, `.env.example` ve başlangıç `README.md` dosyalarını oluştur.
- CI için secret gerektirmeyen unit/lint/format temelini hazırla; Testnet testlerini ayrı ve opt-in tut.
- Dependency seçimlerini ve gerekçelerini `ARCHITECTURE.md` içinde kaydet.

**Exit gate:**

- Proje derlenir.
- Boş/smoke test suite geçer.
- Lint ve format kontrolü geçer.
- Hiçbir gerçek credential repository'de bulunmaz.
- Bütün standart komutlar README'de yer alır.

---

### Faz 1 — Resmi Binance Araştırması ve API Sözleşmesi

**Amaç:** Kod yazmadan önce güncel Binance Spot davranışını resmi kaynaklardan sabitlemek.

**İşler:**

- Resmi Spot REST bölümlerini incele: general info, security, signing, market, account, trading, filters, errors ve rate limits.
- Resmi WebSocket Streams, WebSocket API, User Data Stream, ping/pong, bağlantı limitleri, reconnect ve connection lifetime davranışlarını incele.
- Spot Testnet adreslerini, destek farklarını ve credential gereksinimlerini doğrula.
- Resmi OpenAPI sürüm/commit bilgisini ve resmi Python Spot SDK sürüm/commit bilgisini kaydet.
- Python SDK'dan client lifecycle, config, transport, auth, serialization, errors, WebSocket, tests ve examples yaklaşımlarını analiz et; kodu Clojure'a çevirmeye çalışma.
- İhtiyaç duyulan her endpoint için method, path, security, parametreler, weight/order limit etkisi, response ve hata davranışını tabloya yaz.

**Çıktılar:**

- `docs/BINANCE_RESEARCH.md`
- `docs/BINANCE_ENDPOINT_MATRIX.md`
- `docs/BINANCE_VERSION.md`
- İlk `docs/KNOWN_LIMITATIONS.md`

**Exit gate:**

- V1 endpointlerinin tamamı resmi kaynakla eşleştirilmiştir.
- Signing payload sırası/encoding, timestamp ve `recvWindow` davranışı açıktır.
- User Data Stream'in güncel subscription yöntemi doğrulanmıştır.
- Testnet/production farkları belgelenmiştir.
- Kritik bir belirsizlik varsa test hipotezi yazılmış ve faz kapatılmamıştır.

**Tamamlanma kaydı (2026-08-18):** Zorunlu belgeler ve `docs/PHASE_1_EXIT_GATE.md` oluşturuldu. Kaldırılmış `!ticker@arr` ve legacy listen-key akışları güncel resmî karşılıklarıyla değiştirildi.

---

### Faz 2 — Domain Sözleşmeleri ve SDK Çekirdeği

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_2_EXIT_GATE.md`.

**Amaç:** Transport detaylarını public API'den ayıran küçük, data-oriented çekirdeği kurmak.

**İşler:**

- Client config şemasını tanımla: environment, base URLs, timeout, credentials, `recvWindow`, clock ve transport enjeksiyonu.
- `create-client` yaşam döngüsünü ve kapatma davranışını tanımla.
- Endpointleri EDN map'leri olarak tanımlayan registry yapısını kur.
- Generic request pipeline sözleşmesini oluştur:

```text
endpoint → validation → serialization → timestamp → signing
         → transport → parsing → normalized result/error
```

- Public API'nin immutable Clojure map'leri alıp vermesini sağla.
- Yan etki sınırlarını protocol/function injection ile test edilebilir yap.
- Hata taxonomy'sini tasarla: validation, auth, transport, timeout, API, rate-limit ve unknown-execution.

**Testler:**

- Config validation.
- Environment/base URL seçimi.
- Endpoint registry doğrulaması.
- Request pipeline sırası.
- Transport ve clock enjeksiyonu.
- Public API'nin secret içermeyen hata üretimi.

**Exit gate:** Saf çekirdek testleri geçer ve public API; HTTP, JSON, signer veya Binance'in ham response biçimine gereksiz biçimde bağlı değildir.

**Tamamlanma kaydı (2026-08-18):** Resmî Go/JavaScript SDK çapraz kontrolündeki retry bulgusu endpoint execution sözleşmesine işlendi. Config, client lifecycle, registry, generic pipeline, injection sınırları ve secret-safe hata taksonomisi `docs/PHASE_2_EXIT_GATE.md` kanıtlarıyla tamamlandı.

---

### Faz 3 — Decimal, Encoding, Zaman ve Authentication

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_3_EXIT_GATE.md`.

**Amaç:** Signed isteklerin en hassas ve tekrar kullanılabilir parçalarını bağımsız doğrulamak.

**İşler:**

- `BigDecimal` parse/normalize/serialize yardımcılarını yaz.
- Plain decimal çıktı üret; scientific notation ve sessiz rounding'i engelle.
- Deterministik query/form encoding ve parametre sıralama davranışını uygula.
- Milisaniye/mikrosaniye timestamp gereksinimlerini resmi sözleşmeye göre uygula.
- Server time offset hesaplama ve kontrollü clock abstraction oluştur.
- `recvWindow` varsayılanını ve sınırlarını merkezi yönet.
- Signer abstraction ile HMAC-SHA256 implementasyonunu tamamla.
- Ed25519 anahtar yükleme/signing altyapısını tamamla; key formatlarını araştırma çıktısına göre sınırla.
- RSA'yı V1 public desteğine ekleme; yalnız mimari genişleme noktasını koru.

**Testler:**

- Resmi HMAC signing örnekleri/test vektörleri.
- Doğrulanmış Ed25519 test vektörleri.
- Unicode ve reserved-character encoding.
- Parametre sırası ve imza determinismi.
- Timestamp/offset ve `recvWindow` sınırları.
- Çok küçük/büyük BigDecimal değerleri, trailing zero ve scientific notation reddi.
- Secret redaction.

**Exit gate:** Aynı girdi her zaman aynı payload ve signature'ı üretir; bütün crypto/encoding test vektörleri geçer.

**Tamamlanma kaydı (2026-08-18):** Lossless `BigDecimal` yardımcıları, ayrık REST/WS canonicalizer'ları, offset-aware clock, merkezi `recvWindow`, HMAC-SHA256 ve unencrypted PKCS#8 Ed25519 signer tamamlandı. Resmî Binance REST/WS HMAC vektörleri ve RFC 8032 Ed25519 vektörü geçti.

---

### Faz 4 — HTTP Transport, JSON, Hatalar ve Rate Limit

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_4_EXIT_GATE.md`.

**Amaç:** Endpointlerden bağımsız, gözlemlenebilir ve güvenli bir REST transport katmanı kurmak.

**İşler:**

- Java/JVM `HttpClient` tabanlı transport abstraction oluştur.
- Connect/request timeout, headers, query/body ve response parsing'i uygula.
- JSON sayılarında precision kaybını engelle; finansal alan dönüşümlerini açık yap.
- Binance error body/status/header bilgilerini normalize edilmiş hata verisine çevir.
- `REQUEST_WEIGHT`, `ORDERS` ve `RAW_REQUESTS` kullanımını response header'larından izle.
- `Retry-After`, `429` ve `418` davranışlarını uygula.
- Retry sınıflandırmasını idempotent public GET ile trading POST için ayır.
- Credential ve signature redaction içeren güvenli logging/diagnostics ekle.

**Mock testler:**

- 2xx ve boş response.
- Binance 4xx hata body'leri.
- Timeout, bağlantı hatası ve iptal.
- `429`, `418`, `5xx` ve `Retry-After`.
- Malformed/eksik JSON.
- Rate-limit header parsing.
- Log ve exception içinde secret bulunmaması.

**Exit gate:** Generic transport testleri deterministik geçer; trading isteklerine yanlış retry uygulanmaz.

**Tamamlanma kaydı (2026-08-18):** Reusable JDK 25 `HttpClient`, precision-safe JSON, normalized response metadata, rate-limit header/state takibi ve execution-aware retry tamamlandı. Command timeout/5xx/`-1006`/`-1007` ilk denemede `unknown-execution`; `418` retry dışı, `429` yalnız geçerli `Retry-After` ile safe-read retry davranışına bağlandı.

---

### Faz 5 — Public Spot REST Endpointleri

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_5_EXIT_GATE.md`.

**Amaç:** Authentication gerektirmeyen Spot verileriyle uçtan uca REST çekirdeğini doğrulamak.

**Kapsam:**

- `ping`
- `server time`
- `exchangeInfo`
- `ticker price`
- `24h ticker`
- `book ticker`
- `depth`

**İşler:**

- Her endpointi elle doğrulanmış declarative spec olarak ekle.
- Parametre validation ve response normalization uygula.
- `exchangeInfo` içindeki symbol ve filter verilerini kayıpsız Clojure verisine dönüştür.
- Public API adlarını sade ve tutarlı tut; transport ayrıntılarını dışarı sızdırma.
- API örneklerini README'ye ekle.

**Exit gate:** Unit/mock testleri ve Testnet public integration testleri geçer; finansal alanlarda `double` oluşmadığı doğrulanır.

**Tamamlanma kaydı (2026-08-18):** Yedi public Spot REST ucu declarative registry, doğrulanan query sözleşmeleri, parametreye bağlı request-weight hesabı ve kayıpsız `BigDecimal` normalizasyonuyla tamamlandı. 58 unit/mock testte 343 assertion ve anahtarsız canlı Spot Testnet kabul testinde yedi endpoint geçti.

---

### Faz 6 — Signed REST, Filtreler ve Emir Güvenliği

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_6_EXIT_GATE.md`.

**Amaç:** Account ve manuel Spot trading işlemlerini yerel doğrulama ile güvenli hale getirmek.

**Kapsam:**

- `account` ve balances
- `my trades`
- `test order`
- `new order`
- `query order`
- `cancel order`
- `open orders`
- Yalnız `BUY`/`SELL` ve `MARKET`/`LIMIT`

**İşler:**

- Signed endpoint pipeline'ını public endpoint engine üzerine ekle.
- `PRICE_FILTER`, `LOT_SIZE`, `MARKET_LOT_SIZE`, `MIN_NOTIONAL` ve `NOTIONAL` kurallarını uygula.
- Tick size, step size, min/max, notional ve gerekli alan kombinasyonlarını Binance'e gitmeden doğrula.
- Validation otomatik rounding yapmasın; hatayı alan ve kural bilgisiyle dönsün.
- Her yeni emre benzersiz `newClientOrderId` üret; çağıranın sağladığı geçerli değeri destekle.
- Production order submission için varsayılan kapalı güvenlik kilidi ekle.
- API key izin ve yetersiz balance gibi Binance hatalarını normalize et.

**Testler:**

- Her filtre için sınır altı, sınır, sınır üstü ve step/tick uyumsuzluğu.
- MARKET/LIMIT ve BUY/SELL parametre kombinasyonları.
- `clientOrderId` benzersizliği ve biçimi.
- Account/trade signed request payload'ları.
- Production guard.
- Testnet `test order`.

**Exit gate:** Geçersiz quantity/price/notional yerelde reddedilir; geçerli test order Testnet tarafından kabul edilir.

**Ara kayıt (2026-08-18):** Yedi signed endpoint, otomatik HMAC/Ed25519 signing bağlantısı, exact filtre preflight, benzersiz client order id, production guard ve normalized auth/trading reason'ları tamamlandı. Unit/mock, lint, format ve ağsız integration kapıları geçti. İlk signed Testnet denemesi sistem saatinin server'dan yaklaşık 2,5 saniye ileride olması nedeniyle `-1021` verdi; midpoint tabanlı explicit client clock synchronization eklendi. Yeni credential ile non-executing `order/test` tekrar kabulü bekleniyor; bu nedenle faz henüz kapatılmadı.

**Tamamlanma kaydı (2026-08-18):** Server-time synchronization sonrasında HMAC signed account ve matching engine'de emir gerçekleştirmeyen `POST /api/v3/order/test` gerçek Spot Testnet üzerinde geçti. Production command guard, no-retry sözleşmesi, filtre sınırları ve secret-safe kalite kapıları doğrulandı; Faz 6 kapatıldı.

---

### Faz 7 — Unknown Execution ve Emir Reconciliation

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_7_EXIT_GATE.md`.

**Amaç:** Network belirsizliğinde çift emir riskini engellemek ve order state'i doğrulanabilir kılmak.

**İşler:**

- Trading timeout ve ilgili `5xx` sonuçlarını `unknown-execution` olarak sınıflandır.
- Aynı order payload'ını otomatik yeniden POST etmeyi engelle.
- `newClientOrderId` ile order query/reconciliation akışını oluştur.
- User Data Stream henüz bağlı değilse kontrollü order query fallback tanımla.
- Reconciliation sonucu için açık durum modeli oluştur: pending, confirmed, rejected, unknown, reconciled ve unresolved.
- Belirsizlik süresi/deneme bütçesi ve gözlemlenebilir olayları belgeye bağla.

**Testler:**

- POST sırasında timeout.
- `5xx` ve geç/bozuk response.
- Emir Binance'te oluşmuşken client timeout yaşamış senaryo.
- Emir oluşmamış senaryo.
- Query geçici olarak başarısızken kör retry yapılmaması.
- Aynı `clientOrderId` ile çift emir engeli.

**Exit gate:** Simüle edilen bütün belirsizlik senaryolarında çift POST oluşmaz ve sonuç güvenli biçimde reconcile edilir veya açıkça unresolved kalır.

**Tamamlanma kaydı (2026-08-18):** `pending`, `confirmed`, `rejected`, `unknown`, `reconciled` ve `unresolved` durum modeli; tek command POST; `origClientOrderId` ile bounded query fallback; exponential delay ve `Retry-After`; process-local clientOrderId tekrar kullanım kilidi tamamlandı. Timeout/5xx, order bulundu/bulunmadı, transient/auth/rate-limit query ve duplicate id senaryoları deterministik mock testlerde geçti.

---

### Faz 8 — WebSocket Streams ve User Data Stream

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_8_EXIT_GATE.md`.

**Amaç:** Market/account/order eventlerini dayanıklı tekil bağlantı yönetimiyle sağlamak.

**İşler:**

- WebSocket connection lifecycle ve kapatma semantiğini kur.
- Subscribe/unsubscribe registry ve event dispatch mekanizması ekle.
- Market streamleri için en az güncel `!miniTicker@arr`, seçili sembol `ticker`, `bookTicker` ve partial `depth` desteği ekle. Kaldırılmış `!ticker@arr` kullanılmaz.
- Güncel resmi yönteme göre User Data Stream subscription ve yenileme akışını uygula.
- `executionReport`, `outboundAccountPosition` ve `balanceUpdate` eventlerini normalize et.
- Ping/pong, disconnect detection, exponential backoff, jitter, reconnect ve subscription restore ekle.
- Binance connection lifetime sınırına göre planlı renewal uygula.
- Backpressure/consumer yavaşlığı için sınırlı buffer ve açık overflow policy tanımla.
- Bağlantıyı browser/UI başına açma; connector seviyesinde paylaşılabilir connection/state tasarla.
- User Data Stream eventlerini Faz 7 reconciliation akışına bağla.

**Testler:**

- Connect/subscribe/unsubscribe.
- Ping/pong ve heartbeat timeout.
- Bağlantı kopması, backoff ve reconnect.
- Subscription restore ve duplicate subscription önleme.
- Malformed/unknown event toleransı.
- Event normalization.
- Connection renewal.
- Yavaş consumer/backpressure.
- Execution event ile unknown order reconciliation.

**Exit gate:** Zorla kesilen bağlantı yeniden kurulur, subscriptionlar geri gelir ve Testnet order/account eventleri doğru normalize edilir.

**Tamamlanma kaydı (2026-08-18):** JDK WebSocket transport, exact ping/pong, heartbeat, planlı renewal, exponential reconnect/jitter, market subscription restore, taze imzalı UDS restore, bounded buffer ve BigDecimal event normalizasyonu tamamlandı. Deterministik forced-close testleri, anahtarsız canlı market stream, signed UDS handshake ve explicit opt-in gerçek Testnet `FILLED executionReport`/`outboundAccountPosition` kabulü geçti.

---

### Faz 9 — Testnet Kabul, Performans ve SDK Release Gate

**Durum:** Tamamlandı — 2026-08-18. Kanıt: `docs/PHASE_9_EXIT_GATE.md`.

**Amaç:** Connector'ın bot projesi tarafından kullanılmaya hazır olduğunu kanıtlamak.

**Tam test sırası:**

1. Unit tests
2. Mock/contract tests
3. Public Testnet integration tests
4. Signed Testnet integration tests
5. Manuel Testnet order lifecycle
6. WebSocket disconnect/reconnect testi
7. Secret leakage taraması
8. Baseline benchmark ve kısa soak testi
9. Lint ve format-check
10. Dokümantasyon ve public API review

**Testnet senaryosu:**

```text
ping → time → exchangeInfo → ticker → account
→ test order → LIMIT order oluştur → query → open orders
→ execution/account eventlerini gözle → cancel → final query
```

Market order yalnız güvenli Testnet bakiyesi ve açık test prosedürü varsa ayrı kabul senaryosu olarak çalıştırılır.

**Performans ölçümleri:**

- REST latency p50/p95.
- JSON parse ve state/event dispatch süresi.
- WebSocket messages/sec.
- Reconnect sayısı ve toparlanma süresi.
- Heap/RSS, CPU, GC ve thread sayısı.
- Uzun bağlantıda memory/thread growth.

**Zorunlu çıktılar:**

- `docs/PERFORMANCE_BASELINE.md`
- Güncel `docs/BINANCE_VERSION.md`
- Güncel `docs/KNOWN_LIMITATIONS.md`
- Kullanım ve lifecycle örnekleri içeren `README.md`

**SDK release gate:**

- [x] Bütün unit testleri geçiyor.
- [x] Bütün mock/contract testleri geçiyor.
- [x] Bütün zorunlu Testnet integration testleri geçiyor.
- [x] Testnet LIMIT order create/query/cancel yaşam döngüsü tamamlandı.
- [x] User Data Stream execution ve account update eventleri alındı.
- [x] BigDecimal precision ve serialization doğrulandı.
- [x] Reconnect ve subscription restore doğrulandı.
- [x] Unknown execution senaryosunda çift emir oluşmadı.
- [x] Rate-limit tracking ve backoff doğrulandı.
- [x] Secret leakage testi temiz.
- [x] Lint ve format-check temiz.
- [x] Baseline performans raporu hazır.
- [x] Known limitations ve doğrulanan Binance sürümleri belgeli.

Bu checklist tamamen geçmeden connector `READY` kabul edilmez ve bot kodlamasına başlanmaz.

---

## 6. Her Faz İçin Standart Çalışma Protokolü

Her fazda aynı sıra uygulanacaktır:

1. İlgili resmi dokümantasyonu ve mevcut kararları oku.
2. Faz için küçük implementation plan ve test matrisi çıkar.
3. Minimum gerekli production kodunu yaz.
4. Unit ve gerekiyorsa mock testlerini yaz.
5. İzinli ise integration testlerini çalıştır.
6. Lint ve format-check çalıştır.
7. İlgili dokümantasyonu ve sürüm kayıtlarını güncelle.
8. Secret, precision, retry ve scope ihlali açısından değişikliği review et.
9. Faz exit gate'ini kanıtlayan kısa sonuç kaydı oluştur.
10. Yalnız faz gerçekten kapandıysa küçük atomic commit hazırla.

---

## 7. Stop Conditions

Aşağıdaki durumlardan biri oluşursa tahminle implementasyon yapılmaz:

- Resmi Binance dokümantasyonu belirsiz veya kendi içinde çelişkiliyse.
- Resmi Python SDK davranışı dokümantasyonla çelişiyorsa.
- Signing payload, encoding veya key formatı doğrulanamıyorsa.
- Precision, filter veya notional davranışı belirsizse.
- Testnet davranışı production sözleşmesinden farklıysa.
- Endpoint deprecated olmuş veya yeni sürümle değişmişse.
- User Data Stream subscription/lifecycle yöntemi güncellenmişse.

Bu durumda sırasıyla resmi kaynak yeniden incelenir, bulgu belgelenir, doğrulama testi yazılır ve ancak sonuç doğrulandıktan sonra devam edilir.

---

## 8. Connector V1 Kapsam Dışı

- Futures, Margin, Options, Earn, staking, deposit, withdrawal ve transfer.
- Otomatik trading, strateji, risk motoru, backtesting ve sinyal üretimi.
- Hyper, Datastar veya başka bir UI framework'üne özel kod.
- PostgreSQL, Redis, Kafka veya kalıcı uygulama state'i.
- Full local order-book reconstruction.
- Bütün Binance API'sini OpenAPI'den otomatik generate etmek.
- RSA'nın tam public desteği.
- Production'da gerçek para ile kabul testi.

---

## 9. Bot Aşamasına Devir Sözleşmesi

SDK release gate geçtikten sonra bot projesine yalnız public API ve belgelenmiş lifecycle devredilir. Botun `binance-clj` içindeki transport, signing veya ham Binance response ayrıntılarına erişmesi gerekmez.

Beklenen temel kullanım şekli:

```clojure
(binance/create-client config)
(spot/exchange-info client)
(spot/tickers-24h client)
(spot/account client)
(spot/place-order! client order)
(spot/cancel-order! client request)
```

Public API'nin kesin isimleri Faz 2'de sabitlenecek ve Faz 9'da release review'dan geçirilecektir.

---

## 10. İlk Kodlama Oturumu

İlk uygulama oturumunda yalnız **Faz 0** ele alınacaktır:

1. Yerel JDK/Clojure araçlarını doğrula.
2. Proje iskeletini ve `deps.edn` dosyasını kur.
3. Test/lint/format komutlarını çalışır hale getir.
4. Güvenlik kurallarını `AGENTS.md` içine yerleştir.
5. README ve environment örneğini oluştur.
6. Faz 0 exit gate'ini çalıştırıp sonuçları raporla.

Faz 0–9 tamamlanmıştır. Connector V1 release gate'i geçmiştir; sonraki proje aşaması, burada belgelenen public API ve lifecycle sözleşmesini tüketen bot uygulamasıdır.
