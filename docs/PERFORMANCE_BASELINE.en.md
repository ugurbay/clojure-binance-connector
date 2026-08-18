# Phase 9 Performance Baseline

> English counterpart of [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md). | [Türkçe](PERFORMANCE_BASELINE.md)

## Environment

- Date: 2026-08-18
- Java: Temurin/OpenJDK 25.0.4
- Clojure: 1.12.5
- Operating system: Windows
- Commands: `clojure -M:benchmark`, `clojure -M:soak`
- Network: both tests run entirely in-process; they do not measure Binance latency.

Each microbenchmark performs 30 × 1,000 measured operations after 5,000 warmup operations. p50/p95 are calculated from nanoseconds-per-operation samples for each batch. Results are release regression references and vary with CPU, JVM warmup, and system load.

## Microbenchmark

| Scenario | p50 ns/op | p95 ns/op | Approx. throughput/sec |
|---|---:|---:|---:|
| Bounded buffer offer/poll | 995 | 1,751 | 1,004,823 |
| HMAC WebSocket signing | 125,442 | 176,845 | 7,971 |
| Market JSON parse + normalize | 11,205 | 16,012 | 89,243 |
| User event normalize | 8,864 | 10,312 | 112,812 |

## Short Soak

A ten-second single-producer/single-consumer loop combined `executionReport` normalization with bounded queue offer/poll:

| Measurement | Result |
|---|---:|
| Events processed | 1,383,948 |
| Messages/sec | 138,394 |
| Final queue depth | 0 |
| Dropped events | 0 |
| Thread delta | 0 |
| Post-GC heap delta | 2,024 bytes |

## Interpretation and Limits

- This local baseline shows capacity well above expected manual-trading bot load; it is not a production-throughput guarantee.
- HMAC measurement excludes signer construction and measures one payload signature with a reusable client signer.
- REST latency depends heavily on the Internet path and Testnet load. One acceptance run cannot define an SLA.
- RSS/CPU/GC profiling is outside this short dependency-free gate. Run long production soak and profiler work in the bot deployment environment.
- A p95 increase above 2× on the same machine, or any soak drop/thread growth, warrants investigation; no rigid automatic performance threshold is enforced.
