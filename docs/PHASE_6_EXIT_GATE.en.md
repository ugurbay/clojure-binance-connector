# Phase 6 Exit Gate

> English counterpart of [PHASE_6_EXIT_GATE.md](PHASE_6_EXIT_GATE.md). | [Türkçe](PHASE_6_EXIT_GATE.md)

**Date:** 2026-08-18

**Result:** PASS

All offline, public, and credentialed Spot Testnet gates passed. Acceptance created no real order; it used signed account read and non-executing `order/test`.

Implemented account, trades, test/new/query/cancel order, and open orders; automatic HMAC/Ed25519 signer construction; explicit midpoint time synchronization; exact signed query; permission metadata; BUY/SELL MARKET/LIMIT contracts; exact core filters; malformed/duplicate filter rejection; no rounding; safe unique client IDs; dual production new/cancel lock; no command retry; stable auth/trading reasons; and lossless financial response normalization.

Verification: 77 tests, 480 assertions; offline integration 3/3; public Testnet regression 3/9; signed Testnet HMAC account and non-executing MARKET `order/test`; lint/format clean; no new runtime dependency. The first signed run revealed a Windows clock about 2.5 seconds ahead and `-1021`; adding explicit `client/synchronize-time!` fixed acceptance.

```powershell
$env:BINANCE_API_KEY='testnet-api-key'
$env:BINANCE_API_SECRET='testnet-api-secret'
.\scripts\verify-phase-6.ps1 -RunPublicTestnet -RunSignedTestnet
```

The script never enables production. Next: Phase 7 unknown execution and reconciliation.
