# Katkı Rehberi

Katkılar memnuniyetle karşılanır. Finansal sistemlerle konuşan kodda küçük bir hata gerçek kayba yol açabileceği için değişiklikler kanıt ve test odaklı ilerler.

## Başlamadan Önce

1. [Code of Conduct](CODE_OF_CONDUCT.md), [Security Policy](SECURITY.md) ve [Disclaimer](DISCLAIMER.md) belgelerini okuyun.
2. Güvenlik açığını public issue olarak bildirmeyin.
3. Büyük özellik veya public API değişikliği için önce bir issue açın.
4. V1 kapsamını kontrol edin: Spot manuel trading connector; bot stratejisi, Futures, Margin ve UI kapsam dışıdır.

## Geliştirme Ortamı

Gereksinimler: JDK 25, Clojure CLI ve Git.

```powershell
git clone https://github.com/ugurbay/clojure-binance-connector.git
cd clojure-binance-connector
clojure -M:verify-environment
```

Windows'ta sistem JDK'sını değiştirmeden taşınabilir Temurin kurulabilir:

```powershell
.\scripts\install-portable-jdk.ps1
```

## Branch ve Commit

- Fork üzerinde kısa ve açıklayıcı bir branch açın.
- Bir PR'da tek mantıksal değişiklik tercih edin.
- Commit mesajını emir kipinde ve kısa yazın.
- Generated binary, local cache, credential veya kişisel `.env` commit etmeyin.

## Zorunlu Kalite Kapısı

```powershell
clojure -M:verify-environment
clojure -X:test
clojure -X:integration-test
clojure -M:secret-scan
clojure -M:lint
clojure -M:format-check
```

Biçim gerekiyorsa:

```powershell
clojure -M:format
```

Canlı Testnet testleri varsayılan PR doğrulamasında çalışmaz. Maintainer açıkça istemedikçe gerçek credential veya emir kullanan test çalıştırmayın.

## Kod Kuralları

- Finansal değerlerde yalnız `BigDecimal`; `float`/`double` yasaktır.
- Scientific notation wire'a çıkmamalıdır.
- State-changing command otomatik retry edilmez.
- Timeout/ilgili `5xx` sonrası emir sonucu `UNKNOWN` kabul edilir.
- Secret veya signed payload loglanmaz.
- Yan etkiler sınırda, test edilebilir function boundary arkasında tutulur.
- Resmî Binance dokümanı davranış otoritesidir; üçüncü taraf kütüphane davranışı kopyalanmaz.
- Yeni dependency için `docs/ARCHITECTURE.md` içine gerekçe eklenir.

## Test Beklentisi

Bug fix PR'ı önce hatayı yeniden üreten regresyon testi içermelidir. Yeni endpoint/parametre değişikliği resmî kaynak bağlantısı ve endpoint contract testi taşımalıdır. Trading güvenliği değişikliği en az şu senaryoları değerlendirmelidir:

- Kesin success/rejection
- Timeout, transport ve `5xx` belirsizliği
- No-resubmit garantisi
- Rate limit ve `Retry-After`
- Precision/tick/step/notional
- Credential redaction
- Testnet/production guard ayrımı

## Pull Request Açıklaması

PR açıklamasında şunları belirtin:

- Ne değişti ve neden?
- Kullanıcı/public API etkisi nedir?
- Hangi resmî kaynağa dayanıyor?
- Hangi testler çalıştırıldı?
- Trading, secret, compatibility veya migration riski var mı?
- AI aracı kullanıldıysa hangi kısımlar insan tarafından doğrulandı?

Katkı göndererek değişikliğinizi repository'nin [MIT Lisansı](LICENSE) altında yayınlamaya yetkili olduğunuzu kabul edersiniz.
