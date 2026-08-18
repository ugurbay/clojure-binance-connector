# binance-clj Working Instructions

> English counterpart of [AGENTS.md](AGENTS.md). | [Türkçe](AGENTS.md)

These instructions apply to the entire `Clojure Binance Connector/` directory.

## Source Priority

1. Official Binance Spot API documentation
2. Official Binance OpenAPI/Swagger definitions
3. Official Binance Python Spot SDK
4. Other official Binance SDKs
5. Documented architecture decisions in this repository

Do not use third-party Binance libraries as behavioral sources. If official docs and an official SDK disagree, the documentation wins and the finding is recorded in `docs/BINANCE_RESEARCH.md` and its English counterpart.

## Scope

- V1 is only a Binance Spot manual-trading connector.
- Do not add Futures, Margin, Options, autonomous trading, strategy, UI, or bot application-service code.
- Do not start a phase before the previous exit gate passes.
- Do not write Binance endpoint code before Phase 1 research completes.

## Code and Architecture

- Use idiomatic, immutable, data-oriented Clojure APIs.
- Prefer small single-responsibility modules over large namespaces.
- Do not copy Java/Python generated SDK class models.
- Do not leak transport, authentication, or Binance endpoint detail through the public API.
- Keep effects at boundaries; clocks, transports, and signers must be replaceable in tests.
- Document every new dependency rationale in both architecture documents.
- Do not add out-of-scope features merely for hypothetical future use.

## Financial Correctness

- Use only `BigDecimal` for prices, quantities, balances, commissions, and notionals.
- Never use `float` or `double` for finance.
- Never send scientific notation to Binance.
- Bind precision, tick, and step behavior to official sources and tests.
- Never round silently; reject invalid input explicitly.

## Order Safety

- Tests and applications default to Testnet.
- Production trading requires both production environment and explicit live-trading enablement.
- Treat trading timeout or relevant `5xx` as `UNKNOWN` execution.
- Never blindly resubmit an uncertain order; reconcile through UDS or query.
- Use a unique `newClientOrderId` for every order.
- Test and honor `429`, `418`, and `Retry-After`.

## Secret Safety

- Never log API keys, secrets, private keys, or signatures.
- Never put secrets in exception data, fixtures, snapshots, CI artifacts, or examples.
- Obtain real credentials only from ignored local environment or a secure secret store.
- Keep credentialed integration tests explicitly opt-in.

## Required Checks

```text
clojure -M:verify-environment
clojure -X:test
clojure -X:integration-test
clojure -M:lint
clojure -M:format-check
```

Update tests, lint, formatting, and documentation at every phase end. When a stop condition occurs, do not guess: recheck official sources and create a verification test.
