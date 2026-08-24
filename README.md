# binance-clj

[English](README.en.md) | **Türkçe** | [Tüm iki dilli dokümantasyon](docs/README.md)

Binance Spot için native, data-oriented ve güvenli Clojure connector/SDK projesi.

**Faz 0–9 tamamlandı; connector V1 `READY` durumundadır.** Full Spot Testnet release kabulünde public/signed REST, WebSocket renewal/restore, signed User Data Stream ve non-marketable LIMIT create/query/open-orders/cancel yaşam döngüsü başarıyla doğrulandı.

[![CI](https://github.com/ugurbay/clojure-binance-connector/actions/workflows/ci.yml/badge.svg)](https://github.com/ugurbay/clojure-binance-connector/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Clojure](https://img.shields.io/badge/Clojure-1.12.5-blue.svg)](deps.edn)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](README.md#gereksinimler)
[![Clojars](https://img.shields.io/clojars/v/io.github.ugurbay/binance-clj.svg)](https://clojars.org/io.github.ugurbay/binance-clj)

> [!WARNING]
> Bu yazılım gerçek emir gönderebilir ve maddi kayba yol açabilir. Varsayılan ortam Spot Testnet olsa da production kullanımı teknik olarak mümkündür. Yazılım finansal, yatırım, hukuk veya vergi danışmanlığı değildir. Kullanıcı bütün emirlerden, API anahtarı güvenliğinden, mevzuata ve Binance koşullarına uyumdan tek başına sorumludur. Ayrıntılar için [DISCLAIMER.md](DISCLAIMER.md) dosyasını okuyun.

> [!IMPORTANT]
> Bu proje, proje sahibinin yönlendirmesi ve kabul testleri altında **OpenAI ChatGPT/Codex** yardımıyla geliştirilmiştir. OpenAI veya Binance tarafından desteklenmiş, denetlenmiş ya da onaylanmış değildir. AI yardımı hatasızlık veya güvenlik garantisi oluşturmaz. Ayrıntılar [NOTICE.md](NOTICE.md) içindedir.

## Dokümantasyon Haritası

| İhtiyaç | Belge |
|---|---|
| İlk kurulum ve adım adım kullanım | [Başlangıç Rehberi](docs/GETTING_STARTED.md) |
| Clojars paketleme ve release süreci | [Clojars Yayın Rehberi](docs/CLOJARS_RELEASE.md) |
| Public namespace ve fonksiyon sözleşmeleri | [API Referansı](docs/API_REFERENCE.md) |
| Hata çözme ve operasyon notları | [Sorun Giderme](docs/TROUBLESHOOTING.md) |
| Mimari kararlar | [ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Binance endpoint kapsamı | [BINANCE_ENDPOINT_MATRIX.md](docs/BINANCE_ENDPOINT_MATRIX.md) |
| Güvenlik açığı bildirme | [SECURITY.md](SECURITY.md) |
| Katkı yapma | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Risk ve hukuki uyarılar | [DISCLAIMER.md](DISCLAIMER.md) |
| AI geliştirme bildirimi | [NOTICE.md](NOTICE.md) |
| Sürüm geçmişi | [CHANGELOG.md](CHANGELOG.md) |
| İngilizce proje ve dokümantasyon | [README.en.md](README.en.md) · [English documentation index](docs/README.en.md) |

## Gereksinimler

- JDK 25 (Temurin 25 önerilir)
- Clojure CLI
- Git

Proje Clojure `1.12.5` sürümünü `deps.edn` içinde sabitler. Faz 0 tarihinde referans alınan Clojure CLI sürümü `1.12.5.1664`'tür.

## Clojars ile Kurulum

Clojure CLI projenizin `deps.edn` dosyasına ekleyin:

```clojure
{:deps
 {io.github.ugurbay/binance-clj {:mvn/version "1.0.3"}}}
```

Leiningen kullanan projelerde:

```clojure
:dependencies [[io.github.ugurbay/binance-clj "1.0.3"]]
```

Artifact yayınlama ve doğrulama süreci için [Clojars Yayın Rehberi](docs/CLOJARS_RELEASE.md) belgesine bakın.

Sisteminizde JDK 25 yoksa, geliştirme ortamı için git tarafından ignore edilen `.toolchains/` altında taşınabilir bir JDK da kullanılabilir. Repository'ye JDK binary'si commit edilmez.

Windows'ta resmî Adoptium API'sinden taşınabilir Temurin 25 kurmak için:

```powershell
.\scripts\install-portable-jdk.ps1
```

`verify-phase-0.ps1`, mevcutsa bu taşınabilir JDK'yı otomatik seçer ve sistem genelindeki Java ayarlarını değiştirmez.

Ortamı doğrulamak için:

```powershell
clojure -M:verify-environment
```

## Komutlar

Komutları bu dizinde çalıştırın:

```powershell
# Smoke çalıştırma
clojure -M:run

# Unit tests
clojure -X:test

# Varsayılan ağsız integration harness (canlı public test atlanır)
clojure -X:integration-test

# Anahtarsız Binance Spot Testnet public kabul testi
$env:BINANCE_RUN_PUBLIC_TESTNET='true'
clojure -X:integration-test

# Credential'lı, emir gerçekleştirmeyen signed Testnet kabul testi
$env:BINANCE_API_KEY='yerel-testnet-api-key'
$env:BINANCE_API_SECRET='yerel-testnet-api-secret'
$env:BINANCE_RUN_SIGNED_TESTNET='true'
clojure -X:integration-test

# Anahtarsız market WebSocket Testnet kabulü
$env:BINANCE_RUN_PUBLIC_WEBSOCKET_TESTNET='true'
clojure -X:integration-test

# Credential'lı signed User Data Stream subscription kabulü
$env:BINANCE_RUN_SIGNED_WEBSOCKET_TESTNET='true'
clojure -X:integration-test

# UYARI: küçük bir sanal Spot Testnet MARKET emir gerçekleştirir
$env:BINANCE_RUN_USER_STREAM_EVENTS_TESTNET='true'
clojure -X:integration-test

# Statik analiz
clojure -M:lint

# Format kontrolü
clojure -M:format-check

# Format düzeltme
clojure -M:format

# Benchmark harness
clojure -M:benchmark

# Kısa bounded event soak
clojure -M:soak

# Credential değerlerini yazdırmadan repository secret taraması
clojure -M:secret-scan

# REPL / geliştirme classpath'i
clojure -M:dev
```

Windows'ta Faz 0 kapısını tek komutla çalıştırmak için:

```powershell
.\scripts\verify-phase-0.ps1
```

Faz 2 çekirdek kapısının tamamını çalıştırmak için:

```powershell
.\scripts\verify-phase-2.ps1
```

Faz 3 signing kapısının tamamını çalıştırmak için:

```powershell
.\scripts\verify-phase-3.ps1
```

Faz 4 HTTP/JSON kapısının tamamını çalıştırmak için:

```powershell
.\scripts\verify-phase-4.ps1
```

Faz 5 Public Spot REST kapısını anahtarsız canlı Testnet testi dahil çalıştırmak için:

```powershell
.\scripts\verify-phase-5.ps1 -RunPublicTestnet
```

Faz 6 kapısını public ve signed Spot Testnet kontrolleriyle çalıştırmak için API key/secret'i yalnız process environment'a koyun; script bunları yazdırmaz veya `.env` yüklemez:

```powershell
$env:BINANCE_API_KEY='yerel-testnet-api-key'
$env:BINANCE_API_SECRET='yerel-testnet-api-secret'
.\scripts\verify-phase-6.ps1 -RunPublicTestnet -RunSignedTestnet
```

Faz 7 no-resubmit reconciliation kapısını çalıştırmak için:

```powershell
.\scripts\verify-phase-7.ps1
```

Faz 8 WebSocket implementasyon kapısını ve isteğe bağlı canlı kabulleri çalıştırmak için:

```powershell
.\scripts\verify-phase-8.ps1 -RunPublicWebSocketTestnet

# API key/secret process environment'ta ise:
.\scripts\verify-phase-8.ps1 -RunPublicWebSocketTestnet -RunSignedUserStreamTestnet

# Son event kapısı; küçük bir sanal Testnet MARKET emir gerçekleştirir:
.\scripts\verify-phase-8.ps1 -RunUserStreamEventsTestnet
```

Faz 9 offline veya tam Spot Testnet release kapısını çalıştırmak için:

```powershell
# Emir göndermez
.\scripts\verify-phase-9.ps1

# UYARI: Tek bir sanal non-marketable LIMIT emir oluşturur ve iptal eder
.\scripts\verify-phase-9.ps1 -RunFullTestnet
```

## Environment

`.env.example` dosyasını `.env` olarak kopyalayabilirsiniz. `.env` git tarafından ignore edilir. Config sözleşmesi doğrudan Clojure map kabul eder; `.env` dosyasını otomatik yüklemez. Bot/application katmanı ileride environment değerlerini map'e dönüştürecektir.

Güvenli varsayımlar:

```text
BINANCE_ENV=testnet
ENABLE_LIVE_TRADING=false
```

Gerçek API key, secret veya private key repository'ye eklenmemelidir.

## Faz 2 Çekirdek API

Client oluşturmak bağlantı açmaz. Faz 4 itibarıyla reusable JDK HTTP transport varsayılan olarak oluşturulur; contract testleri için protocol veya `{:send! fn}` transport hâlâ enjekte edilebilir:

```clojure
(require '[binance-clj.core :as binance])

(def client
  (binance/create-client
   {:environment :testnet
    :transport {:send! (fn [request] {:request request})}
    :registry [{:id :spot/ping
                :method :get
                :path "/api/v3/ping"
                :security :none
                :execution :read}]}))

(binance/execute! client :spot/ping)
(binance/close! client)
```

Endpoint retry güvenliği HTTP metoduna göre değil `:execution` alanına göre belirlenir. `:command` endpointleri hiçbir zaman otomatik retry alamaz.

## Faz 3 Decimal ve Signing API

REST ve WebSocket API payload'ları farklı resmî kurallarla canonicalize edilir. Finansal değer yalnız `BigDecimal` veya strict plain-decimal metin olarak kabul edilir:

```clojure
(require '[binance-clj.auth :as auth]
         '[binance-clj.auth.hmac :as hmac]
         '[binance-clj.decimal :as decimal]
         '[binance-clj.time :as time])

(decimal/plain-string 0.01000000M)
;; => "0.01000000"

(def clock (time/synchronized-clock #(System/currentTimeMillis)))
(def signer (hmac/create-signer (System/getenv "BINANCE_API_SECRET")))

(auth/sign-rest signer [[:symbol "BTCUSDT"]
                        [:timestamp (time/current-timestamp (:now clock)
                                                           :millisecond)]])
```

Signer veya signed payload loglanmamalıdır. Ed25519 tarafında yalnız unencrypted PKCS#8 `PRIVATE KEY` PEM ve JDK `PrivateKey` kabul edilir; encrypted/OpenSSH/raw seed formatları açıkça reddedilir.

## Faz 4 HTTP Sonuç Zarfı

Varsayılan HTTP transport; query encoding, request/connect timeout, JSON parsing ve güvenli read retry davranışını uygular. Başarılı çağrılar transport ayrıntısını `:data` içine karıştırmadan metadata döndürür:

```clojure
{:ok? true
 :data {:serverTime 1499827319559}
 :metadata {:endpoint-id :spot/time
            :environment :testnet
            :http-status 200
            :attempts 1
            :rate-limits {:request-weight {[:minute 1] 12}
                          :orders {}
                          :retry-after-seconds nil}}}
```

Safe read istekleri transient network/`5xx` için sınırlı exponential retry alır. `429` yalnız geçerli `Retry-After` süresiyle yeniden denenebilir; `418` otomatik denenmez. `:command` istekleri timeout, `5xx`, `-1006` veya `-1007` sonucunda tekrar gönderilmeden `unknown-execution` olur.

## Faz 5 Public Spot REST API

Public endpoint registry varsayılan client'a gömülüdür. API key gerekmeden tek sembol, sembol listesi veya bütün market seçilebilir:

```clojure
(require '[binance-clj.client :as client]
         '[binance-clj.spot :as spot])

(def connector (client/create-client {:environment :testnet}))

(spot/ping connector)
(spot/server-time connector)
(spot/exchange-info connector {:symbol "BTCUSDT"})
(spot/ticker-price connector "BTCUSDT")
(spot/ticker-price connector ["BTCUSDT" "ETHUSDT"])
(spot/ticker-24h connector {:symbol "BTCUSDT" :type :mini})
(spot/book-ticker connector "BTCUSDT")
(spot/depth connector "BTCUSDT" {:limit 100})

(client/close! connector)
```

Ticker seçimi `nil`, sembol metni, sembol dizisi veya `{:symbol ...}` / `{:symbols [...]}` map'i kabul eder. `exchange-info` ayrıca `:permissions`, `:symbol-status` ve `:show-permission-sets?`; `depth` ise `:limit` ve `:symbol-status` seçeneklerini doğrular. Sembol dizileri Binance sözleşmesine uygun JSON-array query parametresine çevrilir. Bilinen fiyat/miktar/notional alanları `BigDecimal` olur; bilinmeyen response alanları korunur.

## Faz 6 Signed Spot ve Emir Preflight API

API key ve HMAC secret config'ten signer'a otomatik bağlanır. Signed parametreler, `recvWindow`, timestamp ve signature aynı exact query üzerinden taşınır:

```clojure
(require '[binance-clj.client :as client]
         '[binance-clj.spot :as spot])

(def connector
  (client/create-client
   {:environment :testnet
    :credentials {:api-key (System/getenv "BINANCE_API_KEY")
                  :api-secret (System/getenv "BINANCE_API_SECRET")}}))

(def symbol-info
  (get-in (spot/exchange-info connector {:symbol "BTCUSDT"})
          [:data :symbols 0]))

;; Signed akıştan önce midpoint tabanlı Binance saat offset'ini güncelle.
(client/synchronize-time! connector)

(spot/account connector {:omit-zero-balances? true})
(spot/my-trades connector {:symbol "BTCUSDT" :limit 100})
(spot/open-orders connector "BTCUSDT")
(spot/query-order connector {:symbol "BTCUSDT" :order-id 12345})

;; Yerel filtrelerden geçer, Binance doğrular, matching engine'de çalışmaz.
(spot/test-order connector
                 symbol-info
                 {:symbol "BTCUSDT"
                  :side :buy
                  :type :limit
                  :time-in-force :gtc
                  :quantity 0.001M
                  :price 10000M})

(client/close! connector)
```

`client/synchronize-time!`, public `/time` çağrısının başlangıç/bitiş midpoint'ini kullanarak default client clock offset'ini günceller. Signed bir iş akışından önce ve uzun yaşayan client'larda periyodik olarak çağrılmalıdır. Custom `:clock` enjekte eden tüketici kendi senkronizasyonundan sorumludur.

`spot/new-order` ve `spot/cancel-order` gerçek state-changing komutlardır ve otomatik retry edilmez. Production'da ayrıca client config'inde `:environment :production` ile `:enable-live-trading? true` birlikte bulunmadıkça yerelde reddedilir. Filtre preflight hiçbir değeri yuvarlamaz; MARKET veya market-executing `STOP_LOSS` base quantity kullanılıyorsa uygulanabilir notional kontrolü için çağıran güncel `:reference-price` seçeneğini sağlamalıdır.

## Faz 7 Unknown Execution ve Reconciliation API

Yeni order için önerilen yüksek seviye API `spot/submit-order!`dır. Bu fonksiyon emri tam bir kez gönderir. Sonuç unknown ise aynı payload'ı yeniden POST etmek yerine önceden ayrılmış client order id ile `query-order` çalıştırır:

```clojure
(def lifecycle
  (spot/submit-order!
   connector
   symbol-info
   {:symbol "BTCUSDT"
    :side :buy
    :type :limit
    :time-in-force :gtc
    :quantity 0.001M
    :price 10000M}
   {:reconciliation-policy {:max-query-attempts 5
                            :query-delay-ms 250
                            :max-query-delay-ms 2000}}))

(case (:state lifecycle)
  :confirmed  :command-response-confirmed
  :reconciled :order-observed-by-query
  :rejected   :command-definitively-rejected
  :unresolved :manual-or-user-data-stream-follow-up-required)
```

Bu çağrı **gerçek bir new-order command'ıdır**; Testnet'te sanal emir, production guard açıkken production'da gerçek emir oluşturabilir. `:unresolved` hiçbir zaman “emir oluşmadı” anlamına gelmez ve aynı order tekrar gönderilmemelidir. Varsayılan query bütçesi 5'tir; gecikmeler 250 ms exponential başlayıp 2000 ms'de sınırlanır. `429 Retry-After` bu sınırın üzerinde olsa bile korunur, `418` tekrar sorgulanmaz.

Lifecycle state'leri `pending → confirmed|rejected|unknown → reconciled|unresolved` şeklindedir. Event dizisi yalnız güvenli kategori/code/reason metadata'sı taşır. Client, aynı `newClientOrderId` değerinin kendi yaşam süresinde ikinci kez order command'e çıkmasını yerelde engeller.

## Faz 8 WebSocket API

Market ve hesap akışları farklı Binance uç noktalarında çalışır; bağlantılar client tarafından sahiplenilir ve client kapanınca kapanır:

```clojure
(require '[binance-clj.client :as client]
         '[binance-clj.spot.streams :as streams]
         '[binance-clj.spot.user-stream :as user-stream])

(def connector
  (client/create-client
   {:environment :testnet
    :credentials {:api-key (System/getenv "BINANCE_API_KEY")
                  :api-secret (System/getenv "BINANCE_API_SECRET")}}))

;; Abonelikleri bağlantıdan önce kaydetmek ilk restore'u tek mesaj yapar.
(def market-stream (streams/create-stream connector))
(streams/subscribe! market-stream (streams/all-mini-tickers))
(streams/subscribe! market-stream (streams/aggregate-trades "BTCUSDT"))
(streams/subscribe! market-stream (streams/book-ticker "BTCUSDT"))
(streams/subscribe! market-stream (streams/partial-depth "BTCUSDT" 20 100))
(streams/connect! market-stream)
(streams/poll-event! market-stream 1000)

;; Signed akıştan önce clock offset'ini güncelle.
(client/synchronize-time! connector)
(def account-stream (user-stream/create-stream connector))
(user-stream/connect! account-stream)
(def event (user-stream/poll-event! account-stream 1000))

;; Daha önce unresolved kalmış aynı order, matching executionReport ile çözülür.
(def updated-lifecycle
  (user-stream/reconcile-order lifecycle event))

(client/close! connector)
```

Varsayılan event buffer kapasitesi 1024, overflow politikası `:drop-oldest`tir; `create-stream` seçeneğiyle `:buffer-capacity` ve `:overflow-policy :drop-newest` değiştirilebilir. Snapshot dropped sayısını gösterir. Reconnect subscriptionları geri yükler; signed UDS isteği her seferinde yeni timestamp ve signature ile kurulur.

`-RunUserStreamEventsTestnet` kabulü yalnız açık opt-in ile çalışır. `BTCUSDT` için güncel `exchangeInfo` minimum notional'ını ve Testnet USDT bakiyesini kontrol eder, minimuma yakın tek MARKET BUY gönderir ve açık emir bırakmadan `FILLED` ile account-position eventlerini bekler. Varsayılan test komutları bu emri çalıştırmaz.

## Faz 9 Release Gate

`verify-phase-9.ps1` varsayılan olarak JDK/runtime, unit/mock, ağsız integration, secret scan, lint, format, microbenchmark ve bounded event soak çalıştırır. `-RunPublicAggTrade` kimlik gerektirmeden production `aggTrade` akışını ve reconnect/restore davranışını; `-RunFullTestnet` ise public/signed REST, market WebSocket renewal/restore ve tek bir non-marketable LIMIT create/query/open-orders/cancel yaşam döngüsünü doğrular.

LIMIT kabulü güncel Binance filtrelerinden türetilir; sabit price/quantity kullanmaz. Emir `submit-order!` ile tam bir kez oluşturulur, User Data Stream'de `NEW` görülür, tek cancel sonrası `CANCELED` doğrulanır ve final open-orders listesinden kaybolduğu kanıtlanır. Test yalnız sanal Spot Testnet fonları kullanır.

2026-08-18 tarihli final release koşusu 103 unit/contract testte 624 assertion, offline integration'da 20 assertion ve full Testnet integration'da 47 assertion ile geçti. İki process credential değeri exact secret scan'de kontrol edildi; ihlal, lint uyarısı, event drop veya thread artışı bulunmadı. Runtime metadata `:phase 9` ve `:status :ready` döndürür.

Yerel performans sonuçları ve ölçüm sınırları [PERFORMANCE_BASELINE.md](docs/PERFORMANCE_BASELINE.md), güncel release checklist'i [PHASE_9_EXIT_GATE.md](docs/PHASE_9_EXIT_GATE.md) içindedir.

## Dizinler

```text
src/              production namespace'leri
test/             hızlı unit/contract testleri
integration-test/ ayrı entegrasyon testleri
dev/              geliştirme ve benchmark giriş noktaları
docs/             araştırma, mimari ve kabul kayıtları
```

## Sınırlar

V1 yalnız Binance Spot manuel trading connector'ıdır. Futures, Margin, otomatik trading, UI ve bot servisleri bu projenin connector fazları dışındadır.

Ayrıntılı sıra için [BINANCE_CONNECTOR_PHASE_PLAN.md](BINANCE_CONNECTOR_PHASE_PLAN.md) ve kalıcı kurallar için [AGENTS.md](AGENTS.md) dosyalarına bakın.

## Faz 1 Araştırma Belgeleri

- [Binance araştırması](docs/BINANCE_RESEARCH.md)
- [V1 endpoint matrisi](docs/BINANCE_ENDPOINT_MATRIX.md)
- [Sabitlenen resmî sürümler](docs/BINANCE_VERSION.md)
- [Bilinen sınırlamalar](docs/KNOWN_LIMITATIONS.md)
- [Faz 1 çıkış kapısı](docs/PHASE_1_EXIT_GATE.md)
- [Resmî Go/JavaScript SDK çapraz kontrolü](docs/OFFICIAL_SDK_CROSSCHECK.md)
- [Faz 2 çıkış kapısı](docs/PHASE_2_EXIT_GATE.md)
- [Faz 3 çıkış kapısı](docs/PHASE_3_EXIT_GATE.md)
- [Faz 4 çıkış kapısı](docs/PHASE_4_EXIT_GATE.md)
- [Faz 5 çıkış kapısı](docs/PHASE_5_EXIT_GATE.md)
- [Faz 6 çıkış kapısı](docs/PHASE_6_EXIT_GATE.md)
- [Faz 7 çıkış kapısı](docs/PHASE_7_EXIT_GATE.md)
- [Faz 8 çıkış kapısı](docs/PHASE_8_EXIT_GATE.md)
- [Faz 9 release gate](docs/PHASE_9_EXIT_GATE.md)
