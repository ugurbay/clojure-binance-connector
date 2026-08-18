# Faz 9 Performans Baseline

## Ortam

- Tarih: 2026-08-18
- Java: Temurin/OpenJDK 25.0.4
- Clojure: 1.12.5
- İşletim sistemi: Windows
- Komutlar: `clojure -M:benchmark`, `clojure -M:soak`
- Ağ: Microbenchmark ve soak tamamen process içidir; Binance latency ölçümü değildir.

Her microbenchmark senaryosu 5.000 warmup işleminden sonra 30 × 1.000 işlem ölçer. p50/p95, batch başına nanosecond/operation örneklerinden hesaplanır. Sonuçlar release regresyon referansıdır; farklı CPU, JVM ısınması ve sistem yükünde değişebilir.

## Microbenchmark

| Senaryo | p50 ns/op | p95 ns/op | Yaklaşık throughput/sn |
|---|---:|---:|---:|
| Bounded buffer offer/poll | 995 | 1.751 | 1.004.823 |
| HMAC WebSocket signing | 125.442 | 176.845 | 7.971 |
| Market JSON parse + normalize | 11.205 | 16.012 | 89.243 |
| User event normalize | 8.864 | 10.312 | 112.812 |

## Kısa Soak

10 saniyelik tek producer/consumer döngüsü, `executionReport` normalizasyonu ile bounded queue offer/poll yolunu birlikte çalıştırdı:

| Ölçüm | Sonuç |
|---|---:|
| İşlenen event | 1.383.948 |
| Mesaj/sn | 138.394 |
| Queue depth (son) | 0 |
| Dropped event | 0 |
| Thread farkı | 0 |
| GC sonrası heap farkı | 2.024 byte |

## Yorum ve Sınırlar

- Sonuçlar connector event yolunun botun beklenen manuel trading yükünün çok üstünde kapasiteye sahip olduğunu gösteren yerel baseline'dır; production throughput garantisi değildir.
- HMAC ölçümü signer oluşturmayı içermez; reusable client signer ile tek payload imzasını ölçer.
- REST p50/p95 internet, rota ve Binance Testnet yüküne çok bağımlıdır. Faz 9 full Testnet kabulü fonksiyonel latency'yi gözleyecek, fakat tek koşudan SLA türetilmeyecektir.
- RSS/CPU/GC profiler ölçümü bu kısa dependency-free gate kapsamında değildir. Uzun production soak ve profiler çalışması bot uygulamasının deployment ortamında ayrıca yapılmalıdır.
- Release regresyonunda p95'in aynı makinede iki katı aşması veya soak sırasında drop/thread growth oluşması inceleme sebebidir; otomatik sabit performans eşiği uygulanmaz.
