# Phase 7 Exit Gate

> English counterpart of [PHASE_7_EXIT_GATE.md](PHASE_7_EXIT_GATE.md). | [Türkçe](PHASE_7_EXIT_GATE.md)

**Date:** 2026-08-18

**Result:** PASS

Phase 7 completed the lifecycle that never repeats an uncertain new-order command and instead seeks evidence by client order ID. Acceptance used deterministic contract scenarios rather than a real order or deliberately induced live timeout.

Implemented states `pending`, `confirmed`, `rejected`, `unknown`, `reconciled`, `unresolved`; exactly one submit attempt; safe IDs in unknown errors; signed original-client-ID queries; five logical queries; 250 ms exponential delay capped at 2,000 ms; server `Retry-After`; early stop for `418`/non-retryable errors; no false rejection from repeated `-2013`; process-local duplicate-ID rejection; and secret-safe event summaries.

Tests covered immediate success/rejection, multiple uncertain-query paths, unresolved budgets, auth and unexpected errors, rate bans/delays, duplicate IDs, exact query parameters, and an invariant of one new-order POST in every uncertainty scenario.

Verification: 87 tests, 527 assertions; offline harness, lint, and format passed; no new runtime dependency.

```powershell
.\scripts\verify-phase-7.ps1
```

Next: Phase 8 WebSocket and User Data Stream observation.
