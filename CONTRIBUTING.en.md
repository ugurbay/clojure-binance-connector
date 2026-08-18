# Contributing Guide

> English counterpart of [CONTRIBUTING.md](CONTRIBUTING.md). | [Türkçe](CONTRIBUTING.md)

Contributions are welcome. Because a small defect in financial-system integration code can cause real loss, changes are expected to be evidence- and test-driven.

## Before You Start

1. Read the [Code of Conduct](CODE_OF_CONDUCT.en.md), [Security Policy](SECURITY.en.md), and [Disclaimer](DISCLAIMER.en.md).
2. Never report a vulnerability in a public issue.
3. Open an issue before proposing a large feature or public-API change.
4. Check the V1 boundary: this is a manual Spot trading connector; bot strategies, Futures, Margin, and UI work are out of scope.

## Development Environment

Requirements: JDK 25, Clojure CLI, and Git.

```powershell
git clone https://github.com/ugurbay/clojure-binance-connector.git
cd clojure-binance-connector
clojure -M:verify-environment
```

On Windows, install a portable Temurin JDK without changing the system JDK:

```powershell
.\scripts\install-portable-jdk.ps1
```

## Branches and Commits

- Create a short, descriptive branch in your fork.
- Prefer one logical change per pull request.
- Use short, imperative commit messages.
- Never commit generated binaries, local caches, credentials, or a personal `.env` file.

## Required Quality Gate

```powershell
clojure -M:verify-environment
clojure -X:test
clojure -X:integration-test
clojure -M:secret-scan
clojure -M:lint
clojure -M:format-check
```

To apply formatting:

```powershell
clojure -M:format
```

Live Testnet tests are not part of default PR verification. Do not run tests that use real credentials or place orders unless the maintainer explicitly requests them.

## Code Rules

- Use only `BigDecimal` for financial values; `float` and `double` are forbidden.
- Scientific notation must never reach the wire.
- Never automatically retry a state-changing command.
- Treat an order outcome as `UNKNOWN` after a timeout or relevant `5xx` response.
- Never log secrets or signed payloads.
- Keep effects at the boundary behind testable function interfaces.
- Official Binance documentation is the behavioral authority; do not copy third-party library behavior blindly.
- Document the rationale for every new dependency in `docs/ARCHITECTURE.md` and its English counterpart.

## Test Expectations

A bug-fix pull request should first include a regression test that reproduces the defect. An endpoint or parameter change should include an official-source link and endpoint contract test. A trading-safety change must consider at least:

- definitive success and rejection;
- timeout, transport, and `5xx` uncertainty;
- the no-resubmit guarantee;
- rate limits and `Retry-After`;
- precision, tick, step, and notional constraints;
- credential redaction; and
- separation between Testnet and production guards.

## Pull Request Description

State:

- what changed and why;
- the user and public-API impact;
- the official source supporting the behavior;
- tests that were run;
- trading, secret, compatibility, or migration risk; and
- for AI-assisted work, which parts were verified by a human.

By contributing, you confirm that you are authorized to publish your changes under the repository's [MIT License](LICENSE).
