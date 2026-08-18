# Phase 8 Exit Gate

> English counterpart of [PHASE_8_EXIT_GATE.md](PHASE_8_EXIT_GATE.md). | [Türkçe](PHASE_8_EXIT_GATE.md)

**Date:** 2026-08-18

**Result:** PASS

Production code, deterministic reconnect/restore, public Testnet market streaming, signed `userDataStream.subscribe.signature`, and real order/account event acceptance completed. An explicit Testnet MARKET order produced `FILLED executionReport` and `outboundAccountPosition`, validating decimal normalization and Phase 7 reconciliation.

Implemented JDK WebSocket transport/frame assembly, exact pong, shared managers, subscriptions and 1,024-stream limit, current market names, backoff/jitter/heartbeat/shutdown reconnect, 23h50m renewal, market and fresh-signature UDS restore, no listen key, main UDS normalizers, `BigDecimal` finance, unknown-field preservation, bounded drop-oldest/drop-newest queues, unresolved-to-reconciled observation, and client-owned resource close.

Verification: 101 tests, 599 assertions; fragmented ping, reconnect/restore, renewal/heartbeat, duplicate/overflow, malformed/unknown events, and offline integration 6/6 passed. Live `BTCUSDT@bookTicker`, signed UDS, `FILLED executionReport`, account-position event, decimal fields, and lifecycle reconciliation passed on Testnet. No new runtime dependency.

```powershell
.\scripts\verify-phase-8.ps1
.\scripts\verify-phase-8.ps1 -RunPublicWebSocketTestnet
.\scripts\verify-phase-8.ps1 -RunPublicWebSocketTestnet -RunSignedUserStreamTestnet
# WARNING: executes one small virtual MARKET order on Spot Testnet
.\scripts\verify-phase-8.ps1 -RunUserStreamEventsTestnet
```

Only virtual Testnet funds were used. Next: Phase 9 release gate.
