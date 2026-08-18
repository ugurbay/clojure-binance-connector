# binance-clj Çalışma Talimatları

Bu dosyanın kapsamı `Clojure Binance Connector/` dizininin tamamıdır.

## Kaynak Önceliği

1. Binance resmi Spot API dokümantasyonu
2. Binance resmi OpenAPI/Swagger tanımları
3. Binance resmi Python Spot SDK
4. Binance diğer resmi SDK'ları
5. Bu repository içindeki belgelenmiş mimari kararlar

Üçüncü taraf Binance kütüphaneleri davranış kaynağı olarak kullanılmaz. Resmi dokümantasyon ile resmi SDK çelişirse dokümantasyon esas alınır; bulgu `docs/BINANCE_RESEARCH.md` içinde kaydedilir.

## Kapsam

- V1 yalnız Binance Spot manuel trading connector'ıdır.
- Futures, Margin, Options, otomatik trading, strateji, UI ve bot application service kodu eklenmez.
- Bir fazın exit gate'i geçmeden sonraki faza başlanmaz.
- Faz 1 araştırması tamamlanmadan Binance endpoint kodu yazılmaz.

## Kod ve Mimari

- İdiomatik, immutable ve data-oriented Clojure API kullan.
- Büyük namespace'ler yerine küçük ve tek sorumluluklu modüller oluştur.
- Java/Python generated SDK sınıf modelini birebir kopyalama.
- Transport, authentication ve Binance endpoint ayrıntılarını public API'ye sızdırma.
- Yan etkileri sınırda tut; clock, transport ve signer bileşenlerini testlerde değiştirilebilir tasarla.
- Yeni dependency eklenirse gerekçesini `docs/ARCHITECTURE.md` içine yaz.
- V1 kapsamı dışındaki özellikleri gelecekte gerekebilir düşüncesiyle ekleme.

## Finansal Doğruluk

- Price, quantity, balance, commission ve notional alanlarında yalnız `BigDecimal` kullan.
- Finansal değerlerde `float` veya `double` kullanma.
- Binance'e sayı gönderirken scientific notation üretme.
- Precision, tick size veya step size davranışını tahmin etme; resmi kaynağa ve teste bağla.
- Sessiz rounding yapma; geçersiz girdiyi açık validation hatasıyla reddet.

## Emir Güvenliği

- Testlerin ve uygulamanın varsayılan ortamı Testnet'tir.
- Production trading varsayılan olarak kapalıdır ve çift koşulla açılır: `BINANCE_ENV=production` ile `ENABLE_LIVE_TRADING=true`.
- Trading POST timeout veya ilgili `5xx` sonucunda execution durumunu `UNKNOWN` kabul et.
- Belirsiz emri körlemesine yeniden POST etme; User Data Stream veya order query ile reconcile et.
- Her yeni emir için benzersiz `newClientOrderId` kullan.
- Rate-limit yanıtlarını görmezden gelme; `429`, `418` ve `Retry-After` davranışlarını test et.

## Secret Güvenliği

- API key, secret, private key ve signature değerlerini loglama.
- Secret'ları exception data, fixtures, snapshots, CI artifact'ları veya örnek komutlarda kullanma.
- Gerçek credential'ları yalnız git tarafından ignore edilen `.env` veya güvenli secret store üzerinden al.
- Credential içeren integration testleri opt-in tut.

## Zorunlu Kontroller

Değişiklik türüne göre aşağıdaki komutları çalıştır:

```text
clojure -M:verify-environment
clojure -X:test
clojure -X:integration-test
clojure -M:lint
clojure -M:format-check
```

Her faz sonunda test, lint, formatting ve dokümantasyon güncellemesi yapılır. Stop condition oluşursa tahminle kod yazılmaz; resmi kaynak yeniden incelenir ve doğrulama testi oluşturulur.
