# Sorun Giderme Rehberi

Önce şu bilgileri alın; credential değerlerini yazdırmayın:

```powershell
java -version
clojure -Sdescribe
clojure -M:run
clojure -M:verify-environment
```

## `java` veya `clojure` bulunamıyor

- JDK 25 ve Clojure CLI kurulumunu kontrol edin.
- Windows'ta `scripts/install-portable-jdk.ps1` kullanabilirsiniz.
- Yeni kurulumdan sonra PowerShell'i yeniden açın.
- Portable JDK kullanan verify scriptleri `.toolchains/jdk-25*` dizinini otomatik seçer.

## `invalid-timestamp` / Binance `-1021`

Nedenler: clock drift, uzun yaşayan client offset'i veya yanlış time unit.

```clojure
(client/synchronize-time! connector)
```

Signed workflow öncesi yeniden deneyin. Custom `:clock` enjekte ettiyseniz offset yönetimi size aittir. `recvWindow` değerini körlemesine büyütmek temel clock sorununu çözmez.

## `invalid-signature` / Binance `-1022`

- Key ile secret'ın aynı Testnet/production hesabına ait olduğunu doğrulayın.
- HMAC secret yerine API key'i yanlış alana koymadığınızdan emin olun.
- Payload'ı kendiniz oluşturmayın; connector pipeline'ını kullanın.
- URL ve environment eşleşmesini kontrol edin.
- Secret'ı debug loguna yazdırmayın.

Credential sızdıysa anahtarı Binance tarafında iptal/rotate edin.

## `api-key-ip-or-permission-rejected` / `-2015`

- API key permission ve IP allowlist'i kontrol edin.
- Signed account için okuma, order/test/new-order için TRADE izni gerekir.
- Testnet key production'da; production key Testnet'te kullanılamaz.
- Connector withdrawal yetkisi gerektirmez.

## `order-not-found` / `-2013`

Yeni emirden hemen sonraki query Memory → Database görünürlük penceresinde geçici `-2013` döndürebilir. Bu tek başına emrin reddedildiğini kanıtlamaz.

- `submit-order!` lifecycle sonucunu kullanın.
- Aynı business order'ı yeniden POST etmeyin.
- Client order ID ile bounded query ve UDS `executionReport` takip edin.
- `:unresolved` durumunu manuel/async reconciliation kuyruğuna alın.

## `illegal-characters` / `-1100`

Wire parametrelerinden biri Binance lexical sözleşmesine uymuyordur:

- Exponent notation (`1E-8`) kullanmayın.
- `float`/`double` göndermeyin.
- Finansal girdiyi `BigDecimal` veya plain decimal string verin.
- Hesap sonucu gereksiz trailing-zero scale taşıyorsa değeri değiştirmeden `decimal/normalize` kullanın.
- Client order ID yalnız izin verilen karakter ve uzunlukta olmalıdır.

## Filter failure / `-1013`

- `exchangeInfo` snapshot'ını yeniden alın.
- `PRICE_FILTER`, `LOT_SIZE`, `MIN_NOTIONAL` ve `NOTIONAL` değerlerini kontrol edin.
- Tick/step'e kendiniz sessiz rounding uygulamayın; doğru business kuralıyla exact değer üretin.
- `PERCENT_PRICE(_BY_SIDE)` gibi server-authoritative filtreler yerel V1 preflight'ın tamamı değildir.
- Symbol status ve account open-order limitlerini kontrol edin.

## `insufficient-balance`

Account response içindeki `free` balance'ı kullanın; `locked` bakiye harcanamaz. BUY için quote notional, SELL için base quantity ve komisyon etkisini hesaba katın. Testnet resetleri bakiyeleri ve order geçmişini değiştirebilir.

## `429` veya `418`

- `429`: request/order limit aşıldı. `Retry-After` varsa connector safe read için uygular; uygulama yine de çağrı hızını azaltmalıdır.
- `418`: IP ban. Connector otomatik retry yapmaz. Ban süresi dolmadan istek fırtınası üretmeyin.
- Metadata'daki request-weight ve order-count sayaçlarını izleyin.
- Symbolsüz yüksek-weight endpointleri sınırlayın.

## Timeout / transport / `5xx` sırasında new order

State-changing command sonucu unknown olabilir. En önemli kural: aynı emri yeniden göndermeyin.

```clojure
(def lifecycle (spot/submit-order! connector symbol-info order))
```

`:unresolved` ise client order ID ile REST query ve signed UDS kullanın. Manuel operator'a görünür alarm üretin.

## WebSocket bağlanmıyor

- Firewall/proxy'nin `wss` erişimini kontrol edin.
- Market streams ve WebSocket API URL'lerinin farklı olduğunu unutmayın.
- Signed UDS öncesi saat senkronizasyonu ve credential kontrolü yapın.
- `snapshot` içindeki `:status`, `:last-error-category`, `:reconnect-attempt` alanlarını inceleyin.
- Event poll timeout sonucu `nil` normal olabilir; bağlantı hatasıyla karıştırmayın.

## Event kaybı / buffer drop

```clojure
(streams/snapshot stream)
(user-stream/snapshot stream)
```

Buffer `:dropped` sayacı artıyorsa consumer yavaştır:

- Event işini poll thread dışında iş kuyruğuna taşıyın.
- Buffer kapasitesini kontrollü artırın.
- Kritik order/account gap'ini REST ile reconcile edin.
- Drop'u yalnız loglayıp görmezden gelmeyin.

## 24 saat civarında bağlantı yenilenmesi

Binance WebSocket bağlantıları süre sınırına sahiptir. Connector planned renewal/reconnect yapar ve subscriptionları geri yükler. Kısa gap mümkündür; REST snapshot/reconciliation tasarımın parçası olmalıdır.

## Testler neden skip oluyor?

Varsayılan `clojure -X:integration-test` ağsızdır. Canlı kapılar explicit environment flag ister. Release scripti gerekli flag'leri kendi scope'unda ayarlar:

```powershell
.\scripts\verify-phase-9.ps1 -RunFullTestnet
```

Bu komut gerçek Spot Testnet credential ister ve bir sanal LIMIT emir oluşturup iptal eder.

## Secret scan hata veriyor

Scanner gerçek process credential değerini repository metninde bulmuş olabilir. Değeri hiçbir çıktıda tekrar yazdırmayın:

1. Dosyayı git'e eklemeyin.
2. Credential'ı Binance tarafında rotate edin.
3. Git history'ye girdiyse history temizliği ve force-push etkisini ayrıca değerlendirin.
4. `clojure -M:secret-scan` tekrar çalıştırın.

## Issue açmadan önce

```powershell
clojure -X:test
clojure -X:integration-test
clojure -M:secret-scan
clojure -M:lint
clojure -M:format-check
```

Issue'ya işletim sistemi, JDK/Clojure sürümü, commit/tag, minimum örnek ve secret-safe hata `ex-data` ekleyin. API key, secret, private key, signature veya signed query eklemeyin.
