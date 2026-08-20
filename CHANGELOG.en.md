# Changelog

> English counterpart of [CHANGELOG.md](CHANGELOG.md). | [Türkçe](CHANGELOG.md)

Significant changes are recorded here. The project aims to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.2] - 2026-08-20

### Added

- Public `aggregate-trades` stream helper (`<symbol>@aggTrade`)
- Exact-`BigDecimal` price/quantity and stable aliases for `aggTrade`
- Contract tests for ID ranges, maker flags, and malformed payloads
- Credential-free production `aggTrade` reconnect/restore acceptance gate

### Clarified

- Partial depth is an atomic top-N view, not diff-depth continuity or a full book

## [1.0.1] - 2026-08-18

### Added

- `.en.md` English counterparts for every Turkish Markdown document
- A central bilingual documentation index linking Turkish and English editions
- Direct English README links to English security, legal, usage, API, architecture, and phase-evidence documents
- Clojars source-JAR and Maven-POM generation for `io.github.ugurbay/binance-clj`
- Local-Maven external-consumer verification and a guarded Clojars publication script
- Turkish and English Clojars account, token, release, and consumer guides

### Changed

- Declared immutable artifact version `1.0.1` in `VERSION`
- Isolated build-only `tools.build` and `deps-deploy` dependencies from the runtime POM

## [1.0.0] - 2026-08-18

Initial public V1 release candidate.

### Added

- Testnet-by-default client configuration with a dual production guard
- Public Spot REST: ping, time, exchange info, ticker, book ticker, and depth
- Signed account, trades, test order, query/open orders, new order, and cancel order
- HMAC-SHA256 and Ed25519 signer infrastructure
- Exact `BigDecimal` financial model and Binance-filter preflight
- Rate-limit metadata, `429`/`418` behavior, and safe-read retries
- No-resubmit unknown-execution reconciliation lifecycle
- Market WebSocket and signed User Data Stream
- Unit, contract, offline-integration, and opt-in full Spot Testnet release harnesses
- Secret-leakage scanning, benchmarks, soak testing, and comprehensive architecture/research documents

### Verified

- JDK 25 / Clojure 1.12.5
- 103 unit/contract tests, 624 assertions
- 10 offline integration tests, 20 assertions
- Full Spot Testnet: 10 tests, 47 assertions
- Non-marketable LIMIT lifecycle: `NEW → query/openOrders → cancel → CANCELED`
- Credential leakage: 0; lint/format: clean; soak drops/thread growth: 0

### Security

- Production trading disabled by default
- No automatic retry for state-changing commands
- Credential and signature redaction
- `.env`, key files, and local toolchains excluded from Git

[Unreleased]: https://github.com/ugurbay/clojure-binance-connector/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/ugurbay/clojure-binance-connector/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/ugurbay/clojure-binance-connector/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/ugurbay/clojure-binance-connector/releases/tag/v1.0.0
