# Security Policy

> English counterpart of [SECURITY.md](SECURITY.md). | [Türkçe](SECURITY.md)

## Supported Versions

| Version | Security updates |
|---|---|
| Current `main` and latest GitHub release | Supported |
| Older commits and tags | Best effort |

Every production user is responsible for pinning a version, scanning dependencies, and maintaining an incident-response process.

## Reporting a Vulnerability

Do not open a public issue for API-key disclosure, signature flaws, production-guard bypasses, duplicate-order risks, request forgery, credential logging, or similar security issues.

1. Use the repository's **Security → Report a vulnerability** link.
2. If private vulnerability reporting is unavailable, request a private contact channel through the maintainer's GitHub profile; do not disclose vulnerability details publicly.
3. Provide the affected commit/tag, reproduction steps, expected and actual behavior, and a safe proof of concept when possible.
4. Never include real API keys, secrets, signatures, private keys, account identifiers, or production order data.

After receiving a report, the maintainer will make a reasonable effort to acknowledge it, assess impact, and communicate a remediation plan. No fixed response or remediation time is guaranteed.

## Credential Policy

- Never add real credentials to the repository, issues, pull requests, logs, test fixtures, or screenshots.
- `.env` is not loaded automatically and is ignored by Git.
- Use different API keys for Testnet and production.
- Apply least privilege; this connector does not require withdrawal permission.
- Prefer an IP allowlist, secret manager, and regular key rotation.
- Revoke a key immediately if disclosure is suspected; deleting it from a file is not sufficient.

## Scope

This policy covers only code in this repository. Report Binance infrastructure vulnerabilities through Binance's official security channel. Use [SUPPORT.en.md](SUPPORT.en.md) and the issue templates for support questions and general bugs.
