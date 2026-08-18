# Phase 1 Exit Gate

> English counterpart of [PHASE_1_EXIT_GATE.md](PHASE_1_EXIT_GATE.md). | [Türkçe](PHASE_1_EXIT_GATE.md)

**Date:** 2026-08-18

**Result:** PASS

Required research, endpoint matrix, version baseline, and known limitations were completed. Acceptance confirmed seven public and seven signed REST endpoints; exact REST and separate alphabetical WebSocket signing; timestamp/offset/receive-window rules; rate headers, `429`/`418`, REST-vs-WS retry semantics and order-count impact; unknown execution and no-blind-retry; current signed WebSocket UDS; removal of listen-key REST; production/Testnet behavior; pinned official sources; Python/Go/JavaScript SDK cross-checks; current streams replacing `!ticker@arr`; and no critical unanswered protocol question.

Critical findings were that OpenAPI lags current behavior, `!ticker@arr` and legacy listen-key REST are removed, REST and WS canonicalization differ, successful new/cancel request weight may be zero while order count still matters, and generic SDK retry policy is unsafe for commands. No endpoint/transport/signer/WebSocket production code was added in this research phase. Next: Phase 2 domain contracts and SDK core.
