# Resmî Go ve JavaScript SDK Çapraz Kontrolü

**İnceleme tarihi:** 2026-08-18
**Sonuç:** Faz 1'in temel kararları doğrulandı; retry sınıflandırması endpoint execution semantiğine bağlanarak sıkılaştırıldı.

## İncelenen Kaynaklar

| SDK | Commit | Spot/common sürümü |
|---|---|---|
| [Binance Go Connector](https://github.com/binance/binance-connector-go/tree/553d82485895a6ccabd881e6bf838d384a65fc1a) | `553d82485895a6ccabd881e6bf838d384a65fc1a` | Spot `1.10.0`, common `2.6.0` |
| [Binance JavaScript Connector](https://github.com/binance/binance-connector-js/tree/092e4f289e9047114fb8ec66256510cc207e16bb) | `092e4f289e9047114fb8ec66256510cc207e16bb` | `@binance/spot` `32.0.1`, `@binance/common` `2.4.5` |

Her iki güncel depo da ürün bazlı, OpenAPI-generated modüler connector yapısıdır. Eski monolitik Node/TypeScript connector'ları güncel tasarım kaynağı değildir.

## Karar Matrisi

| Konu | Go / JavaScript bulgusu | binance-clj kararı |
|---|---|---|
| Client ayrımı | REST, WebSocket API ve WebSocket Streams ayrı config/lifecycle taşır | **Doğrulandı.** Tek facade altında ayrı sınırlar korunur |
| Public model | Çok sayıda generated struct/interface vardır | **Kopyalanmaz.** Immutable Clojure map + küçük normalizer |
| Finansal alan | Güncel Go ve JS Spot modellerinde price/qty/balance gibi alanlar çoğunlukla string'dir | **Güçlendirildi.** Wire string, public/internal değer `BigDecimal` |
| Büyük integer | JS `json-with-bigint`, Go `int64`/typed modeller kullanır | ID/timestamp integer kalır; finansal değerle karıştırılmaz |
| REST signing | Parametre serialization ile imzalanan payload birlikte üretilir | Exact-wire-payload ve percent-encoding kararı doğrulandı |
| WS API signing | İki SDK da parametreleri alfabetik sıralar | Alfabetik sıralama doğrulandı |
| WS Unicode | Ortak SDK yardımcıları URL encoding uygular | Resmî WS dokümanındaki raw UTF-8 vektörü üstün; Phase 3'te uyumsuzluğu yakalayan test yazılır |
| Auth | HMAC, RSA ve Ed25519 signer yolları vardır | HMAC + Ed25519 V1; RSA kapsam dışı kararı korunur |
| Timestamp | SDK'lar sistem saatini doğrudan okur | Clock injection korunur; deterministik test ve server offset için daha güvenlidir |
| Response metadata | Status, headers ve rate-limit metadata korunur | Normalize metadata envelope kararı doğrulandı |
| Error modeli | HTTP status odaklı ortak hata sınıfları vardır | Binance numeric code + status + retry metadata + unknown execution daha zengin tutulur |
| Reconnect | Connection/subscription registry, `serverShutdown`, reconnect ve restore bulunur | Registry/restore doğrulandı; bounded queue, jitter ve kullanıcı kapatması ayrımı korunur |
| Testnet varsayılanı | SDK varsayılanları production URL'leridir | Testnet varsayılanı güvenlik gereği bilinçli farklılık olarak korunur |

## Kritik Retry Bulgusu

Go common `2.6.0`, REST response `500`–`504` olduğunda HTTP metodunu kontrol etmeden varsayılan üç retry uygular. JavaScript common `2.4.5`, network/`500`–`504` retry'sini `GET` ve `DELETE` metodlarıyla sınırlar. Binance Spot'ta `DELETE /api/v3/order` bir state-changing cancel komutudur; yalnız metoda bakılarak güvenli kabul edilemez.

Bu nedenle binance-clj:

- retry kararını `GET/POST/DELETE` metodundan türetmez;
- her endpoint spec'inde `:execution :read|:command` taşır;
- `:command` için `:retry-policy :never` zorunlu kılar;
- create ve cancel dahil belirsiz trading sonuçlarını ileriki fazlarda query/UDS ile reconcile eder.

Bu, önceki “trading POST tekrar edilmez” kararını **“hiçbir state-changing trading command otomatik tekrar edilmez”** şeklinde genişletir.

## SDK'lardan Bilinçli Ayrılıklar

1. Generated model ağı ve SDK enum kapalılığı alınmaz.
2. Production URL varsayılanı alınmaz.
3. Sabit/global retry sayısı alınmaz.
4. HTTP metoduna göre retry güvenliği çıkarımı yapılmaz.
5. REST ve WebSocket API için aynı canonicalizer kullanılmaz.
6. Sabit reconnect delay tek başına yeterli sayılmaz; exponential backoff + jitter uygulanır.
7. Ham exception/request içeriği tanılama verisine taşınmaz; secret-safe normalize hata üretilir.

## Nihai Teyit

Faz 1'de seçilen kapsam, endpointler, Testnet güvenliği, `BigDecimal`, ayrı REST/WS signing, güncel User Data Stream yöntemi, metadata zarfı, unknown execution ve reconnect/restore kararlarında geri alınması gereken bir hata bulunmadı. Retry kapsamı yukarıdaki şekilde sıkılaştırıldı ve Faz 2 endpoint sözleşmesine işlendi.
