# Faz 1 Çıkış Kapısı

**Tarih:** 2026-08-18
**Sonuç:** PASS

## Zorunlu Çıktılar

- [x] `BINANCE_RESEARCH.md`
- [x] `BINANCE_ENDPOINT_MATRIX.md`
- [x] `BINANCE_VERSION.md`
- [x] `KNOWN_LIMITATIONS.md`

## Kabul Kriterleri

- [x] Yedi public/market ve yedi account/trading REST ucu resmî kaynakla eşleştirildi.
- [x] REST signing payload sırası, percent-encoding ve exact-wire-payload kuralı açıklandı.
- [x] WebSocket API'nin alfabetik, REST'ten ayrı signing kuralı açıklandı.
- [x] Timestamp, server offset ve `recvWindow` varsayılan/üst sınırı belgelendi.
- [x] Rate-limit header'ları, `429`, `418`, REST/WS `retryAfter` farkı ve order count etkisi belgelendi.
- [x] Unknown execution ve no-blind-retry politikası sabitlendi.
- [x] User Data Stream'in güncel WebSocket API subscription yöntemi doğrulandı.
- [x] Kaldırılmış listen-key REST uçları kapsamdan çıkarıldı.
- [x] Production/Testnet adresleri ve Testnet reset/fark davranışı belgelendi.
- [x] Resmî docs, OpenAPI ve Python SDK commit/sürümleri sabitlendi.
- [x] Python SDK yaklaşımından alınacak, değiştirilecek ve kopyalanmayacak tasarımlar kaydedildi.
- [x] Resmî Go ve JavaScript SDK'ları güncel commitleriyle kod seviyesinde çapraz kontrol edildi.
- [x] Kaldırılmış `!ticker@arr` yerine güncel stream sözleşmesi plana işlendi.
- [x] Kritik, cevapsız protokol belirsizliği kalmadı.

## Araştırma Sırasında Çözülen Kritik Farklar

1. OpenAPI güncel Spot davranışından geridedir; docs birincil otorite olarak sabitlendi.
2. `!ticker@arr` kaldırılmıştır; `!miniTicker@arr` + sembol bazlı `@ticker` seçildi.
3. Legacy listen-key REST lifecycle kaldırılmıştır; UDS için `userDataStream.subscribe.signature` seçildi.
4. REST ve WebSocket API signing canonicalization aynı değildir; ayrı bileşen kararı alındı.
5. Başarılı new/cancel order request weight'i 0 olabilir; order count ve response metadata yine izlenecektir.
6. Genel SDK retry davranışı state-changing endpointler için güvenli değildir; command retry politikası metoda değil endpoint semantiğine bağlandı.
7. Go/JavaScript WS signer yardımcılarının URL encoding davranışı resmî Unicode WS vektörüyle ayrışır; dokümantasyon üstünlüğü ve ayrı canonicalizer kararı korundu.

## Faz Sınırı

Bu fazda Binance endpoint, transport, signer veya WebSocket production kodu eklenmedi. Credential gerektiren kontroller ileriki fazların opt-in Testnet kabul testleri olarak kaydedildi.

**Sıradaki faz:** Faz 2 — Domain Sözleşmeleri ve SDK Çekirdeği.
