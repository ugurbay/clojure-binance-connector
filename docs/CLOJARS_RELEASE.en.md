# Clojars Release Guide

**English** | [Türkçe](CLOJARS_RELEASE.md)

This document describes the safe and reproducible process for publishing `binance-clj` to Clojars. The target Maven coordinate is:

```clojure
io.github.ugurbay/binance-clj {:mvn/version "1.0.3"}
```

> [!IMPORTANT]
> Never paste a Clojars deploy token into a chat message, issue, log, or repository file. The token must exist only in the environment of the PowerShell process that performs the release.

> [!WARNING]
> Non-`SNAPSHOT` versions on Clojars are immutable and cannot be deployed again. Do not publish unless `VERSION`, the Git tag, and the generated POM match exactly.

## 1. One-Time Clojars Account Setup

1. **Sign in with GitHub** at [Clojars](https://clojars.org/). The GitHub account must be `ugurbay`.
2. Verify the email address in the profile and enable two-factor authentication where possible.
3. GitHub login automatically verifies the `io.github.ugurbay` group for that account. The package therefore uses `io.github.ugurbay/binance-clj`.
4. Record the Clojars username. It may differ from the GitHub username and becomes the `CLOJARS_USERNAME` value.

## 2. Create the First Deploy Token

1. Open [Clojars Deploy Tokens](https://clojars.org/tokens).
2. Create a token with a description such as `binance-clj-first-release`.
3. The artifact does not yet exist, so the first token cannot be restricted to that artifact. Use a temporary unscoped token for the initial publication.
4. The value is shown only once. Store it in a password manager and never in the repository.
5. After the first successful publication, revoke this token and create a new token restricted to `io.github.ugurbay/binance-clj` or its group for future releases.

The deploy token is not the Clojars account password. Release tooling passes it through the password field, hence the environment name `CLOJARS_PASSWORD`.

## 3. Pre-Release Preparation

Run commands from the repository root:

```powershell
cd 'C:\Users\ismailugur\Documents\ChatGPT\BINANCE CLOJURE API CONNECTOR\Clojure Binance Connector'
```

Review the version in `VERSION`. Select a previously unused Semantic Versioning value for every normal release. Validate without publishing:

```powershell
.\scripts\verify-clojars-package.ps1
```

This check:

- builds the source JAR and Maven POM;
- verifies coordinates, version, and MIT license metadata;
- verifies the license and OpenAI ChatGPT/Codex development notice inside the JAR;
- installs the artifact into the local Maven repository;
- loads its namespace from a clean external-consumer classpath.

Then run the normal release gate:

```powershell
.\scripts\verify-phase-9.ps1
```

To include live Testnet acceptance, use only Binance Spot Testnet credentials and explicitly opt in:

```powershell
.\scripts\verify-phase-9.ps1 -RunFullTestnet
```

Clojars publication does not require a Binance API key or secret.

## 4. Git Commit, Tag, and Push

The publication script rejects a dirty worktree and an untagged commit. Merge the release commit into the main branch, then create and push the tag matching `VERSION`. For `1.0.3`:

```powershell
git status
git tag -a v1.0.3 -m 'binance-clj v1.0.3'
git push origin v1.0.3
```

Before tagging, `git status` must be clean and `HEAD` must be the exact commit to publish. If anything is uncertain and no artifact was published, stop the release and inspect Git history before changing tags.

## 5. Supply Credentials Only to the Current Process

Replace the placeholders only inside your own PowerShell window:

```powershell
$env:CLOJARS_USERNAME='your-clojars-username'
$env:CLOJARS_PASSWORD='your-clojars-deploy-token'
```

Check presence without printing either value:

```powershell
[bool]$env:CLOJARS_USERNAME
[bool]$env:CLOJARS_PASSWORD
```

Both commands must print `True`. Do not run commands such as `Get-ChildItem Env:` that dump the complete environment. Disable PowerShell transcription/logging before entering the token if it is enabled.

## 6. Publish to Clojars

Publication cannot begin without the explicit confirmation switch:

```powershell
.\scripts\publish-clojars.ps1 -ConfirmRelease
```

The script enforces, in order:

- a process-local Clojars username and deploy token;
- a clean Git worktree;
- a `v<VERSION>` tag pointing at `HEAD`;
- Phase 9 tests, integration tests, secret scan, lint, format, and performance checks;
- JAR/POM and external-consumer verification;
- exactly one final Clojars deployment.

After success, remove credentials from the process environment:

```powershell
Remove-Item Env:CLOJARS_USERNAME
Remove-Item Env:CLOJARS_PASSWORD
```

Closing the PowerShell window also removes these process-local values.

## 7. Verify the Publication

The artifact page should become available at:

```text
https://clojars.org/io.github.ugurbay/binance-clj
```

Test actual Clojars resolution from a new, empty directory:

```powershell
clojure -Srepro -Sdeps '{:deps {io.github.ugurbay/binance-clj {:mvn/version "1.0.3"}}}' -M -e "(require '[binance-clj.core :as core]) (println (core/runtime-info))"
```

The result should include at least `:name "binance-clj"`, `:phase 9`, and `:status :ready`. Clojars/Maven indexes may take a short time to update. If resolution initially fails, never redeploy the same version; wait a few minutes and repeat only the consumer verification.

## 8. Consumer Configuration

`deps.edn`:

```clojure
{:deps
 {io.github.ugurbay/binance-clj {:mvn/version "1.0.3"}}}
```

Leiningen:

```clojure
:dependencies [[io.github.ugurbay/binance-clj "1.0.3"]]
```

Read [Getting Started](GETTING_STARTED.en.md) for Testnet-first usage and [Disclaimer](../DISCLAIMER.en.md) for legal and financial risks.

## Failure Modes

- **401/403:** The username is wrong, the token is invalid/revoked, or group permission is missing. Do not substitute the account password.
- **Group not verified:** Sign out of Clojars, sign back in with the correct `ugurbay` GitHub account, and confirm `io.github.ugurbay` verification.
- **Artifact already exists:** That release version has already been published. Do not redeploy; prepare a new release with an incremented `VERSION` if changes are required.
- **Dirty worktree / missing tag:** The script intentionally stopped. Review and commit changes, then place the exact version tag on the correct commit.
- **Credential leak scan:** Do not continue the release. Safely inspect the reported file and Git history, then revoke any exposed token or credential.

## Official Sources

- [Clojars: publishing with Clojure CLI](https://github.com/clojars/clojars-web/wiki/Clojure-CLI-deps.edn)
- [Clojars: Deploy Tokens](https://github.com/clojars/clojars-web/wiki/Deploy-Tokens)
- [Clojars: Verified Group Names](https://github.com/clojars/clojars-web/wiki/Verified-Group-Names)
- [Clojars: Pushing and validations](https://github.com/clojars/clojars-web/wiki/Pushing)
- [Clojure: tools.build guide](https://clojure.org/guides/tools_build)
- [deps-deploy](https://github.com/slipset/deps-deploy)
