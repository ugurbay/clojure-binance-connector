# Public Release and Maintainer Checklist

> English counterpart of [PUBLIC_RELEASE_CHECKLIST.md](PUBLIC_RELEASE_CHECKLIST.md). | [Türkçe](PUBLIC_RELEASE_CHECKLIST.md)

## Before Every Release

- [ ] Binance Spot API documentation/changelog and Testnet changes reviewed.
- [ ] References in `docs/BINANCE_VERSION.md` and its English counterpart are current.
- [ ] Public-API breaking-change analysis completed.
- [ ] Unit/contract and offline-integration tests are clean.
- [ ] Secret scan is clean.
- [ ] Lint and formatting checks are clean.
- [ ] Dependency and JDK/Clojure versions reviewed.
- [ ] `CHANGELOG.md` and `CHANGELOG.en.md` updated.
- [ ] Known limitations and migration notes updated.
- [ ] Opt-in full Spot Testnet acceptance passed when required.
- [ ] The tag points to a signable, immutable release commit.

## GitHub Repository Check

- [ ] Visibility is public.
- [ ] Default branch is `main`.
- [ ] Branch protection/ruleset and required CI reviewed.
- [ ] Private vulnerability reporting enabled.
- [ ] Issue and PR templates work.
- [ ] GitHub recognizes the license as MIT.
- [ ] Description, topics, and release links are current.
- [ ] Repository and artifacts contain no credentials.

## Production Consumer Handoff

- [ ] Release tag/commit pinned.
- [ ] User has read the disclaimer and security policy.
- [ ] Testnet smoke and order lifecycle independently repeated.
- [ ] Production API key uses least privilege and has withdrawal disabled.
- [ ] IP allowlist, secret manager, and rotation are defined.
- [ ] Order limits, position sizing, and kill switch exist in the application layer.
- [ ] Operational procedures exist for unknown execution, UDS gaps, and rate limits.
- [ ] Monitoring, alerting, and manual Binance UI access tested.

This checklist is not a warranty or certification; it helps maintainers and consumers follow a disciplined release process.
