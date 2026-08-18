# Phase 5 Exit Gate

> English counterpart of [PHASE_5_EXIT_GATE.md](PHASE_5_EXIT_GATE.md). | [Türkçe](PHASE_5_EXIT_GATE.md)

**Date:** 2026-08-18

**Result:** PASS

Implemented `ping`, `time`, `exchangeInfo`, ticker price, 24-hour ticker, book ticker, and depth from the pinned official Spot REST contract. Declarative specs validate symbols/lists/enums/limits/combinations; encode JSON arrays through RFC 3986; resolve dynamic weights; preserve unknown fields; normalize finance to strict `BigDecimal`; retain integer IDs/timestamps/counts; and fail safely before transport or without leaking malformed responses.

Coverage included single/multi/all-market calls, FULL/MINI, object/array responses, all depth and symbol-count weight boundaries, query encoding, invalid combinations/keys/types, malformed server responses, and unknown-field preservation.

Verification: 58 tests, 343 assertions; offline integration 2/2; live no-key Testnet 2 tests/8 assertions across all seven endpoints; lint/format clean; no new runtime dependency.

```powershell
.\scripts\verify-phase-5.ps1 -RunPublicTestnet
```

Next: Phase 6 signed REST, filters, and order safety.
