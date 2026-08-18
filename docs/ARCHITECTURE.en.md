# binance-clj Architecture Decisions

> English counterpart of [ARCHITECTURE.md](ARCHITECTURE.md). | [Türkçe](ARCHITECTURE.md)

## Status

Phases 0–9 completed on 2026-08-18. Connector V1 passed the full Spot Testnet release gate and is `READY`. Phase 8 closed resilient shared WebSocket connections and the current signed User Data Stream contract; Phase 9 closed live LIMIT lifecycle, secret scanning, benchmark, and soak acceptance. See [BINANCE_RESEARCH.en.md](BINANCE_RESEARCH.en.md), [BINANCE_VERSION.en.md](BINANCE_VERSION.en.md), and [OFFICIAL_SDK_CROSSCHECK.en.md](OFFICIAL_SDK_CROSSCHECK.en.md).

## ADR-0001 — Project and Runtime Baseline

**Decision:** Build the SDK with Clojure CLI/`deps.edn`, Clojure 1.12.5, and JDK 25. The project has no Node.js or frontend build chain.

## ADR-0002 — Phase 0 Dependencies

Only `org.clojure/clojure` is a runtime dependency at this stage. Test and tooling dependencies do not enter the runtime artifact.

| Dependency | Version | Scope | Rationale |
|---|---:|---|---|
| `org.clojure/clojure` | 1.12.5 | runtime | Pinned Clojure version |
| `io.github.cognitect-labs/test-runner` | v0.5.1 / `dfb30dd` | test | Namespace discovery and standard exit codes |
| `clj-kondo/clj-kondo` | 2026.05.25 | tooling | Pinned static analysis |
| `dev.weavejester/cljfmt` | 0.16.4 | tooling | Format checking and mechanical fixes |

Add a dependency only when a JDK/core solution has a higher correctness or maintenance cost, and record it here.

## ADR-0003 — Test Layers

`test/` holds fast offline unit/contract tests; `integration-test/` runs under a separate alias. Network- and credential-dependent Testnet tests are explicit opt-ins. Default CI requires no secrets.

## ADR-0004 — Safe Environment Default

Testnet and disabled live trading are the safe defaults. No example stores real secrets. Production order submission requires two independent conditions.

## ADR-0005 — Phase Boundary

Phase 0 contains only skeleton and tooling. No HTTP, JSON, signing, or endpoint implementation is selected before the Phase 1 research gate.

## ADR-0006 — JDK 25 Development Environment

CI uses Temurin 25. On Windows, `scripts/install-portable-jdk.ps1` installs under ignored `.toolchains/`. Verification selects it through process-local `JAVA_CMD`, `JAVA_HOME`, and `PATH` without modifying machine-wide variables.

## ADR-0007 — Binance Contract Sources

Current Spot API Docs are authoritative. OpenAPI is a secondary schema check and official SDKs are lifecycle/ergonomics comparisons. No generated client/model classes are used; source commits are pinned in [BINANCE_VERSION.en.md](BINANCE_VERSION.en.md).

## ADR-0008 — REST Request and Signing Boundary

Signed REST parameters use one deterministic percent-encoded query string. Signed bytes and wire bytes are identical. REST and WebSocket canonicalization remain separate, and clocks/signers are injectable.

## ADR-0009 — Financial Data Model

Prices, quantities, balances, commissions, and notionals are `BigDecimal` publicly and internally, and plain-decimal strings on the wire. Unknown response fields are preserved.

## ADR-0010 — Order Idempotency and Uncertainty

Every order gets a unique `newClientOrderId`. Trading timeout, relevant `5xx`, `-1006`, or `-1007` becomes `unknown-execution`. The same order is never automatically POSTed again; reconcile by query and User Data Stream.

## ADR-0011 — User Data Stream Path

Because legacy listen-key REST endpoints were removed, V1 uses WebSocket API `userDataStream.subscribe.signature`, supporting HMAC-default and Ed25519 signers. Ed25519-only `session.logon` is secondary.

## ADR-0012 — Current Market Stream Scope

Removed `!ticker@arr` is not implemented. Use `!miniTicker@arr`, per-symbol `<symbol>@ticker`, `<symbol>@bookTicker`, and partial-depth streams. Full local order-book reconstruction is outside V1.

## ADR-0013 — Phase 2 Public Client Contract

The facade provides `create-client`, `execute!`, `close!`, `closed?`, and secret-safe `client-config` over immutable maps. One bounded atom holds lifecycle state. Creation opens no connection; close is idempotent. Defaults are Testnet, 15,000 ms request timeout, and `5000M` receive window. Overrides accept only safe `https`/`wss`; HMAC secret and private key cannot coexist.

## ADR-0014 — Endpoint Execution Semantics

Endpoints are declarative EDN maps with qualified IDs, `:execution :read|:command`, and derived retry policy. Commands always use `:never`, including `DELETE` cancellation.

## ADR-0015 — Generic Pipeline and Effect Boundary

The fixed sequence is `endpoint → validation → serialization → timestamp → signing → transport → parsing → normalization`. Requests are transport-independent maps; clock, signer, parser, and transport are injectable.

## ADR-0016 — Error Taxonomy and Secret Redaction

Public `ExceptionInfo` errors use `:binance-clj/error` categories: validation, auth, transport, timeout, API, rate-limit, unknown-execution, configuration, or client-closed. Unexpected boundary messages/data/causes do not escape; safe cause class remains. Credentials and signatures are recursively redacted.

## ADR-0017 — Phase 2 Dependency Decision

No runtime dependency was added. URI validation uses the JDK; lifecycle and data contracts use Clojure core.

## ADR-0018 — Exact Decimal and Wire Serialization

Accept `BigDecimal`, integer, or strict plain-decimal text. Reject floating point, exponent notation, whitespace, `.1`, and `1.`. Preserve parse scale, serialize only with `toPlainString`, never round, and expose explicit normalization for comparison.

## ADR-0019 — REST vs WebSocket Canonicalization

REST percent-encodes UTF-8 with RFC 3986, sorting maps by wire key while preserving endpoint-provided pair-vector order. WebSocket API sorts parameter names, excludes `signature`, and retains raw UTF-8 values; the official non-ASCII vector overrides SDK URL-encoding helpers.

## ADR-0020 — Time and Receive Window

The configurable clock returns milliseconds; `:time-unit` can be milliseconds or microseconds. Server offset uses the midpoint between `/time` request start and response end and updates atomically. `recvWindow` defaults to `5000M`, is at most `60000M`, permits three fractional millisecond digits, and rejects floating point.

## ADR-0021 — Signer and Key Formats

The `Signer` protocol separates algorithm from UTF-8 signing. HMAC-SHA256 emits lowercase hex; Ed25519 emits Base64. String representations reveal no key material and each call uses a thread-safe JDK primitive. Ed25519 accepts a JDK `PrivateKey` or unencrypted PKCS#8 PEM; encrypted PKCS#8, OpenSSH, raw seed, and RSA are unsupported.

## ADR-0022 — Phase 3 Dependency Decision

No runtime dependency was added. JDK 25 supplies UTF-8, HMAC, Ed25519, PKCS#8, and Base64; Clojure/JDK core supplies decimal and canonicalization behavior.

## ADR-0023 — HTTP Client Lifecycle

REST uses JDK 25 `HttpClient`, reuses one connection pool, follows no redirects, and applies client connect plus per-request timeouts. Creation makes no network call. All V1 parameters—including POST/PUT/DELETE—use the query string; bodies are empty. Signed requests require exact `:signed-query`.

## ADR-0024 — Precision-Safe JSON

Official `org.clojure/data.json` 2.5.2 parses with `:bigdec true`, preserving decimal and large-integer precision, keywordizing keys, and retaining unknown fields. Empty body is `nil`; malformed/trailing JSON becomes a normalized `:api` error without raw body. Writer converts `BigDecimal` to plain strings and rejects `float`, `double`, and `Ratio`.

## ADR-0025 — HTTP Error and Retry Safety

Classification combines HTTP status, Binance numeric code, endpoint execution, and retry policy. Safe reads get bounded exponential retries for transient network/timeout/`5xx`. `429` retries only with parseable `Retry-After`; `418` never retries. Command uncertainty becomes `:unknown-execution` after one attempt.

## ADR-0026 — Rate-Limit Observation State

Every wire attempt increments local raw-request and static-weight estimates. `X-MBX-USED-WEIGHT-*`, `X-MBX-ORDER-COUNT-*`, and `Retry-After` populate interval-based authoritative observation state. Raw count remains local because Binance exposes no matching header.

## ADR-0027 — Phase 4 Dependency Decision

The only new runtime dependency is official `org.clojure/data.json` 2.5.2, with no external runtime transitives and `BigDecimal` parsing. JDK 25 supplies HTTP/TLS/pooling/timeouts.

## ADR-0028 — Public Spot Registry and Facade

Seven public REST endpoints enter the default declarative registry. `binance-clj.spot` offers a small facade while generic `execute!` remains extensible. Symbol selections are Clojure data; JSON-array parameters are compacted then RFC-3986 encoded. Kebab-case API keys map to official camelCase wire names.

## ADR-0029 — Dynamic Request Weight

Specs retain non-negative static weights. Validation resolves parameter-sensitive `:request-weight`, which serialization prioritizes, so ticker and depth calls track real symbol-count/limit costs.

## ADR-0030 — Public Response Normalization

Endpoint parsers validate object/array shape. Known financial/filter fields become strict `BigDecimal`; timestamps, IDs, and counts remain integers. Depth's first two level values normalize while extra values survive. No unknown object field is dropped. Malformed finance produces safe normalized API errors.

## ADR-0031 — Public Testnet Gate

The no-credential live gate is opt-in through `BINANCE_RUN_PUBLIC_TESTNET=true`; default integration remains offline. It validates ping, time, exchange info, ticker price/24h, book ticker, and depth for BTCUSDT.

## ADR-0032 — Automatic Signed REST Pipeline from Config

Configuration creates one HMAC signer from `:api-secret` or Ed25519 signer from `:private-key`. Signed endpoints combine validated parameters, central receive window, and timestamp into one exact signed query plus API-key header. Missing signer fails before transport. Permission metadata is observable but server permission remains authoritative.

## ADR-0033 — Signed Spot Endpoint and Weight Contracts

Phase 6 adds account, trades, test/new/query/cancel order, and open-orders. Current weights are encoded per endpoint and parameters. `order/test` never reaches matching engine and is still not retried. New and cancel remain state-changing commands with `:never` retry.

## ADR-0034 — Exact Local Filter Preflight

V1 implements `PRICE_FILTER`, `LOT_SIZE`, `MARKET_LOT_SIZE`, `MIN_NOTIONAL`, and `NOTIONAL` using exact decimal comparison, multiplication, and remainder. Known filters cannot be missing, malformed, or duplicated silently. Bounds are inclusive, alignment exact, and nothing is rounded. MARKET base-quantity notional needs current reference price when applicable; Binance remains final authority.

## ADR-0035 — Symbol Snapshot Ownership

Order facades require caller-supplied `symbol-info` from `exchangeInfo`, avoiding hidden cache, expiry, or network calls during trading. The caller owns freshness and Binance revalidates on the wire.

## ADR-0036 — Client Order ID and Dual Production Lock

When absent, test/new order receives a unique UUID-based ID prefixed `clj-`, maximum 36 characters. Allowed characters are `[A-Za-z0-9._:/-]`. Production new/cancel requires both production environment and `:enable-live-trading? true`; reads and non-executing test orders remain available.

## ADR-0037 — Authentication and Trading Error Reasons

Numeric codes plus safe classification yield stable `:binance-reason` values for key/IP/permission, key format, signature/timestamp, missing order, cancel rejection, and insufficient balance. Mutable raw server text is not exposed.

## ADR-0038 — Signed Testnet Gate Safety

`BINANCE_RUN_SIGNED_TESTNET=true` opts into signed account read and non-executing HMAC MARKET quote-quantity `order/test`; it sends no live new/cancel command. Amount is derived from live filters. Secrets come only from process environment and are not logged. Acceptance passed on real Testnet on 2026-08-18.

## ADR-0039 — Explicit Server-Time Synchronization

The default clock is an offline offset-aware wrapper. `client/synchronize-time!` updates offset from the midpoint of a public time request. Synchronization is explicit and observable; long-lived applications should repeat it before/around signed work. A custom clock owns synchronization.

## ADR-0040 — Order Lifecycle State Machine

`submit-order!` returns `pending`, `confirmed`, `rejected`, `unknown`, `reconciled`, or `unresolved`. Valid transitions are `pending → confirmed|rejected|unknown` and `unknown → reconciled|unresolved`. Repeated `-2013` after uncertainty never proves rejection because Memory → Database visibility has no official upper bound.

## ADR-0041 — Exactly One Command Attempt

`submit-order!` crosses the command boundary exactly once. Timeout/network/relevant `5xx`/`-1006`/`-1007` or unexpected submit failure never causes another POST. Client ID is allocated before the command and preserved safely. Reconciliation only uses signed query by original client ID. A process-local claim set also prevents reusing an ID within one client lifetime.

## ADR-0042 — Bounded Query Fallback and Rate Limits

Default reconciliation permits five logical query attempts, starting with 250 ms exponential delay capped locally at 2,000 ms. Valid server `Retry-After` overrides that cap; `418` and non-retryable auth/config/validation/closed errors finish unresolved. One logical read may contain transient transport retries, but command attempts always equal one. Events contain only redacted safe metadata.

## ADR-0043 — Separate Shared WebSocket Channels

Market Streams and WebSocket API use distinct physical managers because endpoints and wire contracts differ. Each is shared at connector level rather than per consumer. Client close idempotently closes registered WebSocket resources.

## ADR-0044 — JDK Transport, Heartbeat, and Renewal

JDK 25 `java.net.http.WebSocket` adds no runtime dependency. Fragmented frames assemble into one JSON message; ping payload receives exact pong. Sixty-five seconds without server ping, shutdown/termination events, or unexpected close triggers reconnect. Planned renewal occurs at 23h50m before the 24-hour server limit.

## ADR-0045 — Reconnect, Restore, and Signed UDS

Reconnect uses exponential backoff plus up to 25% jitter; explicit close does not reconnect. Market registry prevents duplicate subscriptions and restores in one batch. Each UDS reconnect creates a fresh timestamp and signature—never reusing a request. Timestamp is integer and receive window is exact JSON decimal. No legacy listen key is used.

## ADR-0046 — Bounded Event Boundary

Network callbacks never invoke consumer code or block. Normalized messages enter a non-blocking `ArrayBlockingQueue`, default capacity 1,024 and `drop-oldest`, with optional `drop-newest`. Accepted, dropped, and depth counters are observable; blocking overflow is intentionally unsupported.

## ADR-0047 — Event Normalization and Late Reconciliation

Financial market/order/account fields become strict `BigDecimal`; timestamps/IDs remain integers and unknown fields survive. UDS adds stable kebab-case aliases without dropping raw Binance keys. A matching `executionReport` can later change an unresolved lifecycle to reconciled/observed without resubmission.

## ADR-0048 — Phase 9 Full Testnet Acceptance Order

Acceptance derives a tick/step-aligned, above-minimum-notional, non-marketable BTCUSDT LIMIT from current exchange info, book, and balances. Prefer SELL 2% above ask when base balance suffices; otherwise BUY 2% below bid with sufficient quote. Environment is hard-coded Testnet and one-create/one-cancel safety remains.

## ADR-0049 — Release Observability

No runtime dependency is added. Microbenchmarks cover JSON normalization, UDS normalization, bounded queue, and HMAC p50/p95/throughput. A short soak observes drops/depth/heap/threads. Secret scanning searches project text for exact environment credential values without printing them and stops release if found.

## ADR-0050 — Eventual Consistency in Testnet Acceptance

Immediately after successful creation, query may briefly return `-2013` because of Memory → Database visibility. The harness does not treat that as rejection. After UDS observation it polls only safe reads up to 20 times at 250 ms, raising unexpected API errors immediately. New and cancel commands are never repeated. Successful submit `orderId` is retained for a single cleanup cancel if a later acceptance step fails.

## ADR-0051 — Clojars Artifact and Build-Dependency Boundary

The Clojars coordinate is fixed as `io.github.ugurbay/binance-clj` under the verified GitHub reverse-domain group. `VERSION`, Git tag, POM, and JAR version derive from the same source. The artifact is a source JAR containing only `src`, runtime `resources`, the MIT license, AI-development notice, and Maven metadata. Tests, integration fixtures, development code, local toolchains, and credential files are excluded.

`io.github.clojure/tools.build` and `slipset/deps-deploy` are build-time dependencies isolated in the `:build` alias; they do not enter the connector runtime classpath or generated POM dependency list. `tools.build` provides reproducible JAR/POM generation and local installation, while `deps-deploy` performs one artifact/POM upload using Clojars' username plus deploy-token flow. Because release artifacts are immutable, the publication script requires a clean worktree, exact tag, full Phase 9 gate, secret scan, and external-consumer resolution before deployment.
