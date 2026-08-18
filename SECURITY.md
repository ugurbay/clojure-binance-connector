# Güvenlik Politikası

## Desteklenen Sürüm

| Sürüm | Güvenlik güncellemeleri |
|---|---|
| En güncel `main` ve son GitHub release | Destekleniyor |
| Eski commit/tag değerleri | Best effort |

Production trading kullanan herkes kendi sürüm sabitleme, dependency tarama ve incident response sürecinden sorumludur.

## Güvenlik Açığı Bildirme

API key sızıntısı, signature açığı, production guard bypass, yinelenen emir riski, request forgery, credential logging veya benzeri bir güvenlik sorunu bulduysanız public issue açmayın.

1. Repository'nin **Security → Report a vulnerability** bağlantısını kullanın.
2. Private vulnerability reporting görünmüyorsa maintainer'ın GitHub profili üzerinden özel iletişim kanalı isteyin; açığın ayrıntısını public mesajda paylaşmayın.
3. Etkilenen commit/tag, yeniden üretim adımları, beklenen/gerçek davranış ve mümkünse güvenli bir proof-of-concept sağlayın.
4. Gerçek API key, secret, signature, private key, hesap kimliği veya production emir bilgisini hiçbir rapora eklemeyin.

Rapor alındığında makul çabayla alındı teyidi, etki değerlendirmesi ve düzeltme planı paylaşılacaktır. Kesin yanıt veya düzeltme süresi garanti edilmez.

## Credential İlkeleri

- Gerçek credential repository, issue, PR, log, test fixture veya ekran görüntüsüne eklenmez.
- `.env` otomatik yüklenmez ve git tarafından ignore edilir.
- Testnet ve production için farklı API key kullanın.
- En az yetki ilkesini uygulayın; connector withdrawal izni gerektirmez.
- Mümkünse IP allowlist, secret manager ve düzenli key rotation kullanın.
- Sızıntı şüphesinde anahtarı derhâl iptal edin; yalnız dosyadan silmek yeterli değildir.

## Kapsam

Bu politika yalnız bu repository'deki koda yöneliktir. Binance altyapısındaki açıklar Binance'in resmi güvenlik kanalına bildirilmelidir. Destek soruları ve genel bug raporları [SUPPORT.md](SUPPORT.md) ve issue şablonları üzerinden gönderilmelidir.
