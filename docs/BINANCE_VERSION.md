# Binance Uyumluluk Temeli

## İnceleme Kaydı

- İnceleme tarihi: **2026-08-18**
- Hedef: Binance Spot REST, WebSocket Streams, WebSocket API ve User Data Stream
- Varsayılan çalışma ortamı: Spot Testnet
- Veri biçimi: JSON

Bu dosyadaki commit kimlikleri Faz 1 araştırmasının yeniden üretilebilir referanslarıdır. Runtime, sabit bir Binance sunucu sürümü varsaymaz; `exchangeInfo`, response header'ları ve canlı hata yanıtları her zaman güncel sunucu otoritesidir.

## Sabitlenen Resmî Kaynaklar

| Kaynak | Referans | Tarih/sürüm | Bu projedeki rolü |
|---|---|---|---|
| [Binance Spot API Docs](https://github.com/binance/binance-spot-api-docs/tree/976cc580553890e92031b77306147c0ed1de5a46) | `976cc580553890e92031b77306147c0ed1de5a46` | 2026-08-14 | Birincil davranış kaynağı |
| [Binance API Swagger](https://github.com/binance/binance-api-swagger/tree/64b327c0addadd191ca0dca8e6b4cebd8a97c05c) | `64b327c0addadd191ca0dca8e6b4cebd8a97c05c` | v1.22.0, 2024-10-08 | İkincil REST şema kontrolü |
| [Binance Python Connectors](https://github.com/binance/binance-connector-python/tree/8fc3614aa480849fee019634e65341481b637c94) | `8fc3614aa480849fee019634e65341481b637c94` | 2026-08-14 | SDK tasarım karşılaştırması |
| [Binance Go Connectors](https://github.com/binance/binance-connector-go/tree/553d82485895a6ccabd881e6bf838d384a65fc1a) | `553d82485895a6ccabd881e6bf838d384a65fc1a` | Spot 1.10.0 / common 2.6.0 | SDK çapraz kontrolü |
| [Binance JavaScript Connectors](https://github.com/binance/binance-connector-js/tree/092e4f289e9047114fb8ec66256510cc207e16bb) | `092e4f289e9047114fb8ec66256510cc207e16bb` | Spot 32.0.1 / common 2.4.5 | SDK çapraz kontrolü |

## OpenAPI Durumu

Resmî Swagger deposundaki `spot_api.yaml`:

- OpenAPI sürümü `3.0.2`, belge `info.version` değeri `1.0`.
- Repository release sürümü v1.22.0'dır.
- V1 REST path'lerinin tamamını içerir.
- Dinamik `timestamp` ve `signature` üretimini modellemez.
- 2024-10-08 sonrasında Spot API'ye gelen User Data Stream, filtre, stream ve rate-limit değişikliklerini kapsamaz.

Bu nedenle OpenAPI, endpoint şekli için çapraz kontrol aracıdır; güncel parametre, weight, hata veya yaşam döngüsü davranışında Spot API Docs üstün gelir. OpenAPI'den otomatik Clojure client/model üretimi yapılmayacaktır.

## Resmî Python Spot SDK Durumu

İncelenen HEAD içinde:

- `binance-sdk-spot` sürümü `9.2.0`.
- Spot package changelog tarihi `2026-06-09`.
- Spot package, `binance-common` `4.0.0` ister; monorepo ortak modülünün HEAD sürümü `4.2.0`'dır.
- Repository GitHub Release kullanmıyor; her connector ayrı package ve sürümle yayınlanıyor.
- Spot client REST API, WebSocket API ve WebSocket Streams yüzeylerini ayrı config/lifecycle bileşenleri altında topluyor.

Python SDK generated modellerinde finansal alanlar sıkça `float` olarak tanımlanmıştır. Bu proje SDK'nın lifecycle, config ayrımı, response/rate-limit envelope, test ve example yaklaşımını inceler; generated sınıf modelini ve `float` tiplerini kopyalamaz.

## Resmî Go ve JavaScript Spot SDK Durumu

- Her ikisi de ürün bazlı modüler, OpenAPI-generated connector yapısıdır.
- REST, WebSocket API ve WebSocket Streams ayrı config/lifecycle bileşenleridir.
- Güncel Spot response modellerinde finansal alanlar çoğunlukla string olarak korunur; JS ayrıca büyük integer JSON parse desteği kullanır.
- İki ortak katman da retry davranışını genel client seviyesinde uygular. Go, `500`–`504` response'larını metoda bakmadan; JavaScript ise `GET`/`DELETE` için retry eder. Bu davranış trading command güvenliği için doğrudan kopyalanmaz.
- İki SDK'nın WebSocket API signer yardımcıları alfabetik sıralama yanında URL encoding uygular. Resmî WebSocket API dokümanındaki non-ASCII raw UTF-8 vektörü davranış otoritesidir.

Ayrıntılı karşılaştırma `OFFICIAL_SDK_CROSSCHECK.md` içindedir.

## Uyumluluk Yenileme Protokolü

Her release adayı ve Binance kaynak değişikliği öncesinde:

1. Spot API Docs `master` HEAD ve `CHANGELOG.md` kontrol edilir.
2. `testnet/CHANGELOG.md` ve `testnet/general-info.md` kontrol edilir.
3. `rest-api.md`, `filters.md`, `errors.md`, `web-socket-streams.md`, `web-socket-api.md` ve `user-data-stream.md` diff'i incelenir.
4. V1 endpoint matrisi, weight değerleri, imza test vektörleri ve stream isimleri yeniden doğrulanır.
5. Yeni referans commit'leri ve inceleme tarihi bu dosyaya yazılır.
6. Davranış değiştiyse önce contract test güncellenir; sonra implementasyon değiştirilir.

## Faz 9 Release Yeniden Kontrolü

2026-08-18 tarihinde Spot API Docs `master` tekrar sorgulandı ve sabitlenen HEAD'in `976cc580553890e92031b77306147c0ed1de5a46` olduğu doğrulandı. New/query/cancel order, execution types, User Data Stream ve Spot Testnet reset/sanal bakiye sözleşmelerinde V1 kararlarını değiştiren yeni bir fark görülmedi.

Full Spot Testnet release kabulü aynı tarihte geçti. Güncel `exchangeInfo` filtrelerinden türetilen non-marketable BTCUSDT LIMIT emir oluşturuldu; REST query/open-orders ve UDS `NEW` ile gözlendi, tek cancel sonrasında UDS/final query `CANCELED` ve open-orders yokluğu doğrulandı. Public/signed REST, canlı WebSocket planned renewal/restore ve exact environment credential leakage scan birlikte geçti. Connector runtime seviyesi Faz 9 / `READY` olarak kapatıldı.
