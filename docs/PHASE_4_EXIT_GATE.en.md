# Phase 4 Exit Gate

> English counterpart of [PHASE_4_EXIT_GATE.md](PHASE_4_EXIT_GATE.md). | [Türkçe](PHASE_4_EXIT_GATE.md)

**Date:** 2026-08-18

**Result:** PASS

Implemented pooled JDK 25 `HttpClient`, separate connect/request timeouts, HTTPS exact-query requests, Binance/time-unit/JSON headers, precision-safe `data.json`, empty/malformed response behavior, normalized result metadata, status/code error classification, limit-header parsing and local trackers, safe-read exponential retries, `429` compliance, `418` ban handling, unknown command execution, exact signed-query enforcement, and diagnostic redaction.

Mock coverage included success/empty/malformed JSON, reserved Unicode queries, timeout and headers, transient read retries, exhausted budgets, valid/invalid `Retry-After`, IP ban, uncertain command responses, API/auth codes, non-JSON errors, boundary redaction, missing signatures, limit state, and pipeline envelope integration.

No public Binance facade or domain normalizer was added. Verification: 50 tests, 232 assertions; offline harness, lint, and format passed on JDK 25/Clojure 1.12.5. New runtime dependency: `org.clojure/data.json` 2.5.2. Next: Phase 5 public Spot REST.
