# Phase 3 Exit Gate

> English counterpart of [PHASE_3_EXIT_GATE.md](PHASE_3_EXIT_GATE.md). | [Türkçe](PHASE_3_EXIT_GATE.md)

**Date:** 2026-08-18

**Result:** PASS

Implemented strict lossless `BigDecimal`, rejection of scientific/floating/silent rounding, RFC-3986 UTF-8 encoding, deterministic REST canonicalization, separate raw-UTF-8 WS canonicalization, millisecond/microsecond timestamps, midpoint server offset, centralized receive-window validation, algorithm-neutral `Signer`, HMAC-SHA256 lowercase hex, Ed25519 Base64 and unencrypted PKCS#8 loading, and secret-safe representations.

Official Binance ASCII REST HMAC, Unicode REST HMAC, alphabetical WebSocket HMAC, and RFC 8032 §7.1 Ed25519 test 1 all passed. Boundary tests covered decimal forms, reserved/Unicode encoding, ordering and nil omission, time units, offsets, receive-window bounds, unsupported key containers, deterministic signatures, and secret leakage.

No HTTP, JSON, endpoints, or limit state was added; RSA/encrypted/OpenSSH key support remains outside V1. Verification: 32 tests, 139 assertions; offline integration 1/1; lint/format clean; no new runtime dependency. Next: Phase 4 transport, JSON, errors, and limits.
