# Başlangıç ve Kullanım Rehberi

Bu rehber, sıfırdan kurulumdan güvenli Spot Testnet kullanımına kadar önerilen yolu anlatır. Production'a geçmeden önce [DISCLAIMER.md](../DISCLAIMER.md), [SECURITY.md](../SECURITY.md) ve [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) mutlaka okunmalıdır.

## 1. Ne Kuruyorum?

`binance-clj` bir bot veya trading stratejisi değildir. Binance Spot ile konuşan connector/SDK katmanıdır:

```text
Uygulamanız / REPL
        ↓
binance-clj public API
        ↓
validation → timestamp → signing → transport → parsing
        ↓
Binance Spot REST / WebSocket
```

Connector public market verisi, signed account verisi, manuel MARKET/LIMIT emirleri ve realtime stream'ler sunar. Strateji, risk kararı, position sizing ve kalıcı business state çağıran uygulamanın sorumluluğudur.

## 2. Ön Koşullar

PowerShell'de kontrol edin:

```powershell
java -version
clojure -Sdescribe
git --version
```

Hedef ortam:

- Java/JDK 25
- Clojure CLI
- Clojure runtime 1.12.5 (`deps.edn` ile sabit)

Windows'ta JDK 25 yoksa:

```powershell
.\scripts\install-portable-jdk.ps1
```

Script `.toolchains/` altına repository-local Temurin kurar. Dizin git tarafından ignore edilir.

## 3. Clone ve İlk Doğrulama

```powershell
git clone https://github.com/ugurbay/clojure-binance-connector.git
cd clojure-binance-connector
clojure -M:verify-environment
clojure -M:run
```

Beklenen smoke bilgisi:

```clojure
{:name "binance-clj"
 :phase 9
 :status :ready
 :clojure-version "1.12.5"
 :java-version "25..."}
```

Sonra ağsız kapıyı çalıştırın:

```powershell
.\scripts\verify-phase-9.ps1
```

Bu komut gerçek emir göndermez; unit/contract, offline integration, secret scan, lint, format, benchmark ve kısa soak çalıştırır.

## 4. Public Testnet İstemcisi

Bir REPL açın:

```powershell
clojure -M:dev
```

```clojure
(require '[binance-clj.client :as client]
         '[binance-clj.spot :as spot])

(def connector
  (client/create-client {:environment :testnet}))
```

Client oluşturmak ağ çağrısı yapmaz. İlk endpoint çağrısı transport'u kullanır.

```clojure
(spot/ping connector)
(spot/server-time connector)
(spot/exchange-info connector {:symbol "BTCUSDT"})
(spot/ticker-price connector "BTCUSDT")
(spot/ticker-24h connector {:symbol "BTCUSDT" :type :mini})
(spot/book-ticker connector "BTCUSDT")
(spot/depth connector "BTCUSDT" {:limit 100})
```

İşiniz bitince:

```clojure
(client/close! connector)
```

`close!` idempotent'tir; birden fazla çağrı güvenlidir.

## 5. Sonuç Zarfı

Başarılı çağrıların ortak şekli:

```clojure
{:ok? true
 :data <normalize edilmiş Binance cevabı>
 :metadata {:endpoint-id :spot/time
            :environment :testnet
            :http-status 200
            :attempts 1
            :rate-limits {:request-weight {...}
                          :orders {...}
                          :retry-after-seconds nil}}}
```

Finansal response alanları bilinen şemalarda `BigDecimal`, timestamp/id alanları integer olur. Bilinmeyen response alanları forward compatibility amacıyla korunur.

## 6. Testnet Credential Hazırlığı

Spot Test Network üzerinde API key oluşturun. Anahtarı yalnız o PowerShell process'ine ekleyin:

```powershell
$env:BINANCE_API_KEY='testnet-key'
$env:BINANCE_API_SECRET='testnet-secret'
```

Kontrol ederken değeri yazdırmayın:

```powershell
if ($env:BINANCE_API_KEY) { 'API key hazır' }
if ($env:BINANCE_API_SECRET) { 'API secret hazır' }
```

Connector `.env` dosyasını otomatik yüklemez. Uygulama environment/secret manager değerlerini config map'e açıkça geçirir.

## 7. Signed Client ve Saat Senkronizasyonu

```clojure
(def connector
  (client/create-client
   {:environment :testnet
    :credentials {:api-key (System/getenv "BINANCE_API_KEY")
                  :api-secret (System/getenv "BINANCE_API_SECRET")}}))

(client/synchronize-time! connector)
```

Signed workflow öncesinde ve uzun yaşayan client'ta periyodik olarak saat senkronizasyonu yapın. Client creation hidden network çağrısı yapmaz.

```clojure
(spot/account connector {:omit-zero-balances? true})
(spot/my-trades connector {:symbol "BTCUSDT" :limit 100})
(spot/open-orders connector "BTCUSDT")
```

## 8. Güncel Symbol Filtrelerini Alma

Order validation için eski veya hard-coded filtre kullanmayın:

```clojure
(def symbol-info
  (get-in (spot/exchange-info connector {:symbol "BTCUSDT"})
          [:data :symbols 0]))
```

PRICE_FILTER, LOT_SIZE ve notional kuralları zaman içinde değişebilir. Wire üzerindeki nihai karar Binance'e aittir.

## 9. Gerçekleşmeyen Test Order

```clojure
(spot/test-order
 connector
 symbol-info
 {:symbol "BTCUSDT"
  :side :buy
  :type :limit
  :time-in-force :gtc
  :quantity 0.001M
  :price 10000M})
```

`test-order` signed bir `POST /api/v3/order/test` çağrısıdır ancak matching engine'e gerçek emir bırakmaz. Buna rağmen API key TRADE yetkisi ve doğru signature gerekir.

## 10. Yeni Emir ve Lifecycle

Gerçek Testnet/production emrinde önerilen API:

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
   {:reconciliation-policy
    {:max-query-attempts 5
     :query-delay-ms 250
     :max-query-delay-ms 2000}}))
```

Lifecycle durumları:

| State | Anlam | Kullanıcı davranışı |
|---|---|---|
| `:confirmed` | Command cevabı kesin başarı | Order ID/status işle |
| `:reconciled` | İlk sonuç unknown, query/UDS emri gözledi | Gözlenen order state'i işle |
| `:rejected` | Kesin validation/API reddi | Hata nedenini düzelt; aynı id'yi kullanma |
| `:unresolved` | Query bütçesi sonucu kanıtlamadı | Yeniden POST etme; REST/UDS ile takip et |

Kritik kural: timeout veya ilgili `5xx` sonrasında aynı iş emrini körlemesine göndermeyin.

## 11. Query ve Cancel

```clojure
(spot/query-order connector
                  {:symbol "BTCUSDT"
                   :original-client-order-id "your-client-order-id"})

(spot/cancel-order connector
                   {:symbol "BTCUSDT"
                    :order-id 123456789})
```

Cancel da state-changing command'dır. Network belirsizliğinde otomatik retry edilmez; final state ayrıca sorgulanmalıdır.

## 12. Market WebSocket

```clojure
(require '[binance-clj.spot.streams :as streams])

(def market-stream
  (streams/create-stream connector
                         {:buffer-capacity 2048
                          :overflow-policy :drop-oldest}))

(streams/subscribe! market-stream (streams/book-ticker "BTCUSDT"))
(streams/subscribe! market-stream (streams/partial-depth "BTCUSDT" 20 100))
(streams/connect! market-stream)

(streams/poll-event! market-stream 1000)
(streams/snapshot market-stream)
```

Abonelikleri bağlantıdan önce kaydetmek ilk restore'u batch etmeye yardımcı olur. Snapshot dropped/depth/reconnect durumunu gözlemlemek için kullanılmalıdır.

## 13. Signed User Data Stream

```clojure
(require '[binance-clj.spot.user-stream :as user-stream])

(client/synchronize-time! connector)
(def account-stream (user-stream/create-stream connector))
(user-stream/connect! account-stream)

(def event (user-stream/poll-event! account-stream 1000))
(def updated-lifecycle
  (user-stream/reconcile-order lifecycle event))
```

UDS restore her yeni bağlantıda taze timestamp ve signature üretir. Reconnect sırasında event gap olabileceği için kritik order/account state REST ile reconcile edilmelidir.

## 14. Production'a Geçiş

Production yalnız çift koşulla açılır:

```clojure
(client/create-client
 {:environment :production
  :enable-live-trading? true
  :credentials {...}})
```

Production öncesi minimum kontrol:

- Kodu ve dependency'leri bağımsız inceleyin.
- Tag/commit sabitleyin.
- Ayrı ve kısıtlı production key kullanın; withdrawal izni vermeyin.
- IP allowlist/secret manager kullanın.
- Position/order limitlerini bot katmanında kurun.
- `:unknown`/`:unresolved`, rate limit ve WebSocket gap süreçlerini test edin.
- Manuel kill switch ve Binance UI erişimini hazır tutun.
- Küçük miktarla başlayın; kaybedemeyeceğiniz fonu kullanmayın.

## 15. Credential Temizleme

Test bittikten sonra:

```powershell
Remove-Item Env:BINANCE_API_KEY
Remove-Item Env:BINANCE_API_SECRET
```

Bir credential yanlışlıkla paylaşıldıysa yalnız environment'tan silmek yetmez; Binance tarafında iptal/rotate edin.

## 16. Sonraki Okumalar

- [API Referansı](API_REFERENCE.md)
- [Sorun Giderme](TROUBLESHOOTING.md)
- [Mimari Kararlar](ARCHITECTURE.md)
- [Endpoint Matrisi](BINANCE_ENDPOINT_MATRIX.md)
- [Bilinen Sınırlamalar](KNOWN_LIMITATIONS.md)
- [Faz 9 Release Kanıtı](PHASE_9_EXIT_GATE.md)
