# Faz 8 Çıkış Kapısı

**Tarih:** 2026-08-18
**Sonuç:** PASS

Faz 8'in production kodu, deterministik reconnect/restore sözleşmesi, anahtarsız canlı Spot Testnet market akışı, credential gerektiren `userDataStream.subscribe.signature` handshake'i ve gerçek order/account event kabulü tamamlandı. Explicit opt-in Testnet MARKET emri `FILLED executionReport` ile `outboundAccountPosition` üretti; decimal alanlar ve Faz 7 reconciliation bağlantısı doğrulandı.

## Resmî Sözleşme Temeli

- [Spot WebSocket Streams](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/web-socket-streams.md)
- [Spot WebSocket API](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/web-socket-api.md)
- [Spot User Data Stream](https://github.com/binance/binance-spot-api-docs/blob/976cc580553890e92031b77306147c0ed1de5a46/user-data-stream.md)

## Tamamlanan Sözleşmeler

- [x] JDK 25 `HttpClient` WebSocket transport ve fragmented text reassembly
- [x] Server ping payload'ını byte-for-byte pong olarak yansıtma
- [x] Paylaşılabilir tek market bağlantısı ve tek UDS bağlantısı modeli
- [x] `SUBSCRIBE`/`UNSUBSCRIBE`, duplicate önleme ve 1024 stream üst sınırı
- [x] `!miniTicker@arr`, symbol ticker, book ticker ve partial depth adları
- [x] Exponential backoff + jitter, heartbeat timeout ve `serverShutdown` reconnect
- [x] 23 saat 50 dakikada planlı connection renewal
- [x] Reconnect sonrasında market subscription restore
- [x] Reconnect sonrasında taze timestamp/signature ile UDS restore
- [x] Güncel `userDataStream.subscribe.signature`; legacy listen-key yok
- [x] `executionReport`, `outboundAccountPosition`, `balanceUpdate` normalizasyonu
- [x] Finansal alanlarda `BigDecimal`, bilinmeyen alanları koruma
- [x] Non-blocking bounded buffer; `drop-oldest` ve `drop-newest` politikaları
- [x] UDS `executionReport` ile `unresolved → reconciled` Faz 7 geçişi
- [x] Client kapanınca sahip olduğu WebSocket kaynaklarını kapatma

## Doğrulama

- Unit/mock: PASS — 101 test, 599 assertion, 0 failure/error
- Ping payload mirror ve fragmented text: PASS
- Disconnect/backoff/reconnect/restore: PASS
- Renewal ve heartbeat timeout: PASS
- Duplicate subscription ve bounded overflow: PASS
- Malformed/unknown event toleransı: PASS
- Varsayılan ağsız integration harness: PASS — 6 test, 6 assertion
- Anahtarsız gerçek Spot Testnet `BTCUSDT@bookTicker`: PASS
- Signed UDS Testnet subscription: PASS — kullanıcı tarafından 2026-08-18 tarihinde çalıştırıldı
- Gerçek Testnet `FILLED executionReport`: PASS — kullanıcı tarafından 2026-08-18 tarihinde çalıştırıldı
- Gerçek Testnet `outboundAccountPosition`: PASS — kullanıcı tarafından 2026-08-18 tarihinde çalıştırıldı
- Event decimal alanları ve `unresolved → reconciled`: PASS
- Runtime hedefi: JDK 25 / Clojure 1.12.5
- Yeni runtime dependency: yok

Kabul komutları:

```powershell
# Kod, mock ve ağsız kalite kapıları
.\scripts\verify-phase-8.ps1

# Anahtarsız market stream canlı kabulü
.\scripts\verify-phase-8.ps1 -RunPublicWebSocketTestnet

# API key/secret process environment'ta iken signed subscription handshake
.\scripts\verify-phase-8.ps1 -RunPublicWebSocketTestnet -RunSignedUserStreamTestnet

# UYARI: Spot Testnet'te küçük bir MARKET emir gerçekleştirir
.\scripts\verify-phase-8.ps1 -RunUserStreamEventsTestnet
```

**Kapanış kaydı:** Explicit opt-in kabul yalnız sanal Testnet bakiyesi kullandı ve production URL'sine bağlanmadı. Faz 8 kapandı; sıradaki aşama Faz 9 Testnet LIMIT yaşam döngüsü, performans ve SDK release gate'idir.
