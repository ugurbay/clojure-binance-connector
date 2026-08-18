## What changed

Describe the focused change.

## Why

Explain the user problem, root cause, and official Binance source where applicable.

## Safety impact

- [ ] No state-changing command retry was introduced.
- [ ] BigDecimal/precision behavior was reviewed.
- [ ] Credential/signature redaction was reviewed.
- [ ] Testnet/production guard behavior was reviewed.
- [ ] Rate-limit and compatibility impact was reviewed.

## Validation

- [ ] `clojure -M:verify-environment`
- [ ] `clojure -X:test`
- [ ] `clojure -X:integration-test`
- [ ] `clojure -M:secret-scan`
- [ ] `clojure -M:lint`
- [ ] `clojure -M:format-check`

List any additional Testnet or manual verification. Never paste credentials, signatures, signed queries, or private account/order data.

## AI assistance

State whether AI tools were used and what was independently reviewed or tested by a human.

## Breaking changes

Describe migration steps, or write “None.”
