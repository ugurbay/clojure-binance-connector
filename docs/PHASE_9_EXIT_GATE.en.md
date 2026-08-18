# Phase 9 SDK Release Gate

> English counterpart of [PHASE_9_EXIT_GATE.md](PHASE_9_EXIT_GATE.md). | [Türkçe](PHASE_9_EXIT_GATE.md)

**Date:** 2026-08-18

**Result:** PASS — CONNECTOR V1 READY

Offline and full Spot Testnet release gates completed. The default gate remains offline, credential-free, and order-free; `-RunFullTestnet` passed with real Testnet credentials and virtual balances.

Offline acceptance covered all tests, JDK 25/Clojure 1.12.5, zero lint warnings/errors, formatting, secret scan, precision regression, no-resubmit regression, forced reconnect/restore, performance baseline, a ten-second no-drop/no-thread-growth soak, and public API/lifecycle documentation.

Full acceptance covered public REST, signed account and `order/test`, public stream and renewal/restore, signed UDS, one non-marketable LIMIT create, REST and UDS `NEW` observation, matching open order, exactly one cancel, UDS/final-query `CANCELED`, no remaining open order, and exact-credential scanning.

The harness is hard-coded to Testnet and derives tick/step-aligned price and quantity from current book, filters, and virtual balances. It prefers a funded SELL 2% above ask or otherwise BUY 2% below bid. New order is sent once. Cleanup queries first and sends at most one cancel only if an open order is observed.

### Live Findings

An immediate post-create query briefly returned `-2013`, consistent with Memory → Database eventual consistency. The harness now waits for UDS then polls only safe reads at 250 ms up to 20 times, never repeating commands. Definitive submit rejection is kept distinct from later query failure. A subsequent non-executing preflight exposed `-1100`: mathematically aligned values retained excessive intermediate scale. Harness values are now simplified without rounding via `decimal/normalize`; the shared scale-preserving serializer remains unchanged and regression coverage fixes `price=66029.04&quantity=0.00017`.

```powershell
.\scripts\verify-phase-9.ps1
.\scripts\verify-phase-9.ps1 -RunFullTestnet
```

Final evidence: 103 tests/624 assertions; offline integration 10/20; full Testnet 10/47; two exact credential values checked with zero violations; clean lint/format; 1,346,274 soak events, zero drops and zero thread growth. Final output: `Phase 9 release verification passed.` Runtime reports Phase 9 and `:ready`.
