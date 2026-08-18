# Risk ve Hukuki Sorumluluk Reddi

Son güncelleme: 18 Ağustos 2026

Bu belge önemli kullanım koşullarını ve riskleri açıklar. Hukuk danışmanlığı değildir. Kendi durumunuz için yetkili bir hukuk, finans, vergi veya uyum uzmanına başvurun.

## Finansal ve Teknik Risk

`binance-clj`, Binance Spot API ile bağlantı kurabilen ve emir oluşturma/iptal etme komutları gönderebilen bir yazılım bileşenidir. Kripto varlıklar yüksek volatilite, likidite, piyasa, karşı taraf, saklama, bağlantı ve düzenleyici risk taşır. Yazılım hatası, kullanıcı hatası, yanlış yapılandırma, gecikme, ağ kesintisi, stale veri, rate limit, exchange kesintisi, API değişikliği veya kötüye kullanılan credential aşağıdakiler dâhil zararlara yol açabilir:

- Beklenmeyen, yinelenen, eksik veya yanlış emirler
- Emir durumunun geçici ya da kalıcı olarak belirsiz kalması
- Kısmi veya tam sermaye kaybı
- Fırsat, gelir, veri veya itibar kaybı
- Hesap kısıtlaması, rate-limit veya IP yasağı
- Vergi, raporlama ya da mevzuat yükümlülükleri

Testnet kabulünün geçmiş olması production ortamında hatasız çalışma garantisi değildir. Geçmiş test veya benchmark sonuçları gelecekteki davranışın garantisi değildir.

## Tavsiye veya Aracılık Değildir

Bu proje:

- Finansal, yatırım, trading, hukuk, vergi veya muhasebe tavsiyesi vermez.
- Herhangi bir varlığı alma, satma veya elde tutma önerisi oluşturmaz.
- Broker, dealer, yatırım danışmanı, fiduciary, exchange veya saklama hizmeti değildir.
- Kâr, performans, uptime, uygunluk veya belirli bir amaca elverişlilik vaat etmez.

Kullanıcı kendi araştırmasını, risk analizini ve hukuki uygunluk değerlendirmesini yapmak zorundadır.

## Kullanıcının Sorumluluğu

Yazılımı kullanan kişi veya kuruluş:

1. Önce Spot Testnet üzerinde bağımsız doğrulama yapmaktan,
2. Production guard'larını yalnız bilinçli biçimde açmaktan,
3. Emir fiyatı, miktarı, sembolü ve hesabı doğrulamaktan,
4. API key/secret/private key değerlerini güvenli saklamaktan ve en az yetki ilkesini uygulamaktan,
5. Withdrawal yetkisini gereksiz yere vermemekten,
6. Rate limit, account limit ve Binance kurallarına uymaktan,
7. Kullanıldığı ülkedeki mevzuat, yaptırım, lisans, vergi ve raporlama kurallarına uymaktan,
8. İzleme, alarm, yedekleme, incident response ve manuel durdurma süreçlerini kurmaktan,
9. Bağımlılıkları ve resmi Binance değişikliklerini takip etmekten

tek başına sorumludur.

## Garanti ve Sorumluluk Sınırı

Yazılım, MIT Lisansı uyarınca **“OLDUĞU GİBİ”** sunulur. Uygulanabilir hukukun izin verdiği azami ölçüde; proje sahibi, yazarlar, katkıda bulunanlar ve AI araç sağlayıcıları yazılımın doğruluğu, güvenliği, kesintisizliği, güncelliği, satılabilirliği, ihlal oluşturmaması veya belirli bir amaca uygunluğu hakkında açık ya da zımni garanti vermez.

Uygulanabilir hukukun izin verdiği azami ölçüde bu kişiler; yazılımın kullanımı, kullanılamaması veya yazılıma güvenilmesiyle bağlantılı doğrudan, dolaylı, arızi, özel, cezai veya sonuçsal zararlar; kâr, sermaye, gelir, veri, itibar veya iş fırsatı kaybı için sorumlu tutulamaz. Bazı hukuk düzenleri belirli garanti veya sorumluluk sınırlamalarına izin vermeyebilir; bu nedenle sınırlamalar yalnız hukuken izin verilen ölçüde uygulanır.

Bu ek uyarı ile [MIT Lisansı](LICENSE) arasında çelişki olması hâlinde lisans metni yazılımın lisans koşulları bakımından belirleyicidir.

## Üçüncü Taraflar ve Markalar

Proje Binance veya OpenAI ile bağlantılı değildir; bu kuruluşlar tarafından desteklenmiş, denetlenmiş, sertifikalandırılmış ya da onaylanmış değildir. Binance, Binance logosu, OpenAI, ChatGPT ve Codex ilgili sahiplerinin ticari markaları olabilir. İsimler yalnız uyumluluk ve geliştirme aracını tanımlamak için kullanılır.

Binance hesabı ve API kullanımı ayrıca Binance'in güncel sözleşmelerine, risk uyarılarına ve API kurallarına tabidir. Resmî kaynaklar değişebileceğinden kullanıcı yayın öncesi ve production kullanım öncesi güncel belgeleri kontrol etmelidir.
