# Clojure Binance Connector — Phased Implementation Plan

> English counterpart of [BINANCE_CONNECTOR_PHASE_PLAN.md](BINANCE_CONNECTOR_PHASE_PLAN.md). | [Türkçe](BINANCE_CONNECTOR_PHASE_PLAN.md)

**Plan date:** 2026-08-18
**Current status:** Phases 0–9 complete; Connector V1 is `READY`.

## 1. Definition of Success

Deliver a native, data-oriented Clojure SDK for manual Binance Spot use that defaults to Testnet, preserves financial precision, treats command uncertainty safely, supports current REST/WebSocket contracts, exposes stable map-based APIs, and passes deterministic plus explicit live Testnet gates. It is a connector—not a strategy, autonomous bot, UI, risk engine, or custody system.

V1 success requires public and signed REST; BUY/SELL MARKET/LIMIT; HMAC and Ed25519 signing infrastructure; exact filters; no-resubmit reconciliation; market and signed user streams; lifecycle/resource safety; test/lint/format/secret gates; performance evidence; and documentation.

## 2. Invariant Architecture and Safety Rules

- Official current Binance Spot documentation is the behavioral authority.
- Prefer immutable Clojure maps and small single-purpose namespaces over generated class networks.
- Keep transport, signing, endpoint, and domain concerns separate.
- Inject clocks, signers, transports, parsers, and scheduling boundaries for deterministic tests.
- Use `BigDecimal` for all financial values; reject floating point, exponent wire forms, and silent rounding.
- Testnet is default. Production orders require both production selection and explicit live-trading enablement.
- Every order gets a unique client order ID.
- Never automatically retry any state-changing command, including cancellation.
- Treat timeout, relevant `5xx`, `-1006`, and `-1007` as unknown execution and reconcile through reads/UDS.
- Never log or commit keys, secrets, private keys, signatures, or signed payloads.
- Keep credential-dependent acceptance explicitly opt-in.
- Do not advance a phase until its exit gate passes.

## 3. Target Layout

```text
src/binance_clj/          public facade, contracts, auth, REST, WebSocket
test/                     fast offline unit and contract tests
integration-test/         offline harness plus opt-in Testnet acceptance
dev/                      benchmark, soak, secret-scan and developer entry points
scripts/                  environment/JDK and phase verification commands
docs/                     research, architecture, API, operations and exit evidence
.github/                  CI, templates and ownership
```

## 4. Phase Order

```text
0 foundation
  → 1 official research
  → 2 domain contracts/core
  → 3 decimal/encoding/time/auth
  → 4 HTTP/JSON/errors/limits
  → 5 public Spot REST
  → 6 signed REST/filters/order safety
  → 7 unknown execution/reconciliation
  → 8 market and user WebSockets
  → 9 full Testnet/performance/release gate
```

## 5. Detailed Phases

### Phase 0 — Project Skeleton and Development Infrastructure

Create `deps.edn`, standard source/test directories, smoke entry point, Clojure test aliases, lint/format tools, secret-safe examples/ignore rules, JDK 25 CI, portable Windows JDK support, architectural instructions, and a one-command gate. Do not implement Binance behavior. Exit requires environment, smoke, tests, lint, formatting, benchmark entry, and secret safety.

### Phase 1 — Official Binance Research and API Contract

Pin official documentation/OpenAPI/SDK commits; document environments, endpoint paths, weights, parameters, response sources, signing rules, time window, filters, errors, Testnet behavior, current market streams, UDS replacement for removed listen keys, and SDK comparison. Resolve conflicts in favor of current official documentation. Produce research, endpoint matrix, version baseline, known limitations, and exit evidence before production code.

### Phase 2 — Domain Contracts and SDK Core

Define validated Testnet-first configuration, safe URL overrides, dual production guard, client lifecycle, declarative endpoint registry, execution/retry semantics, generic eight-stage pipeline, injectable boundaries, secret-safe error taxonomy, and immutable public map contracts. Keep HTTP, JSON, crypto, and real endpoints out of scope. Exit through comprehensive pure/offline contract tests.

### Phase 3 — Decimal, Encoding, Time, and Authentication

Implement strict exact decimal parsing/serialization, RFC-3986 REST encoding, separate raw-UTF-8 WebSocket signing payload, milliseconds/microseconds, midpoint time offset, receive-window rules, HMAC-SHA256, Ed25519/PKCS#8, and safe signer representations. Verify official Binance signing examples and RFC Ed25519 vectors. Do not add transport/endpoints.

### Phase 4 — HTTP Transport, JSON, Errors, and Rate Limits

Use reusable JDK `HttpClient`, exact-query requests, precision-safe official `data.json`, normalized result envelopes, error classification, header-based rate observations, bounded safe-read retry, and strict command uncertainty/no-retry. Redact all sensitive diagnostics. Mock every relevant HTTP/error path. Defer public endpoint facades.

### Phase 5 — Public Spot REST Endpoints

Implement ping, server time, exchange info, ticker price, 24-hour ticker, book ticker, and depth. Validate selection/enum/limit combinations before transport; calculate dynamic weights; normalize known decimal/integer fields while preserving unknown fields. Keep default tests offline and add an explicit credential-free Spot Testnet gate.

### Phase 6 — Signed REST, Filters, and Order Safety

Implement account, trades, test/new/query/cancel order, and open orders; automatic signer selection; explicit time synchronization; exact query signatures; current endpoint weights; BUY/SELL MARKET/LIMIT contracts; exact core filters; unique safe client IDs; dual production lock; stable error reasons; and signed Testnet account plus non-executing `order/test`. The gate must never place a real order.

### Phase 7 — Unknown Execution and Order Reconciliation

Build a closed lifecycle state machine around exactly one submit attempt. Reconcile uncertainty by original client order ID using bounded safe reads with exponential delay and server rate metadata. Preserve `unresolved` when absence cannot be proven, prevent ID reuse during one client lifetime, and keep lifecycle evidence secret-safe. Use deterministic uncertainty simulations, not deliberately dangerous live failures.

### Phase 8 — WebSocket Streams and User Data Stream

Implement separate shared market/WS-API connections through JDK WebSocket; frame reassembly, heartbeat, current streams, subscription registry, exponential reconnect with jitter, 23h50m renewal, batched market restore, freshly signed UDS restore, event normalizers, bounded non-blocking buffers, and lifecycle reconciliation from `executionReport`. Validate public stream, signed handshake, and explicit small virtual Testnet order/account events.

### Phase 9 — Testnet Acceptance, Performance, and SDK Release Gate

Complete an offline release command plus explicit full Spot Testnet acceptance. Derive one non-marketable LIMIT from current filters/book/balances; prove create/query/open-orders/UDS NEW, send exactly one cancel, then prove UDS/final-query CANCELED and no open order. Include credential-value scanning, benchmarks, ten-second soak, current source recheck, documentation, and cleanup safety.

Final acceptance checklist:

- [x] Unit, mock/contract, and required Testnet tests pass.
- [x] Testnet LIMIT create/query/cancel lifecycle completes.
- [x] UDS execution and account events arrive.
- [x] `BigDecimal` precision and serialization verified.
- [x] Reconnect and subscription restore verified.
- [x] Unknown execution never creates a duplicate command.
- [x] Rate-limit tracking/backoff verified.
- [x] Secret scan, lint, and formatting clean.
- [x] Performance baseline complete.
- [x] Known limitations and pinned Binance references documented.

## 6. Standard Protocol for Every Phase

1. Recheck the authoritative source when behavior could have changed.
2. Update architecture/research decisions before or with implementation.
3. Write deterministic contract/regression tests at each boundary.
4. Implement the smallest in-scope public behavior.
5. Run environment, unit, integration, secret, lint, and formatting gates.
6. Run only explicitly authorized live Testnet acceptance.
7. Record results, limitations, and the next phase in an exit-gate document.

## 7. Stop Conditions

Stop implementation and return to official research if endpoint security, signing bytes, time units, error certainty, request weight, filter semantics, stream lifecycle, or credential handling is ambiguous; if a required test would risk production funds; if secrets appear in files/output; or if an observed live response contradicts the contract. Add a reproducing contract test before changing behavior.

## 8. Outside Connector V1

Futures, Margin, Options, OCO/OTO/SOR, wallet operations, withdrawals, autonomous strategy, position sizing, portfolio/risk engine, backtesting, persistence, UI, notifications, deployment orchestration, and operational bot services are separate future work.

## 9. Handoff Contract to the Bot Project

The bot may consume stable connector maps, result envelopes, lifecycle states, rate metadata, and stream events. It must own strategy, persistent globally unique business IDs, position/order limits, portfolio/risk decisions, durable state, monitoring/alerts, operator controls, kill switch, reconciliation queues, compliance, and production credential operations.

## 10. Initial Coding Session

The initial session created only Phase 0 foundation and verified JDK/Clojure/tooling before Phase 1 research. That sequencing was preserved through all later gates; the completed evidence is recorded in the paired Phase 0–9 exit documents.
