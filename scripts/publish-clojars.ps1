[CmdletBinding()]
param(
    [switch] $ConfirmRelease
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$version = (Get-Content -LiteralPath (Join-Path $projectRoot 'VERSION') -Raw).Trim()
$expectedTag = "v$version"

if (-not $ConfirmRelease) {
    throw 'Publication is immutable. Re-run with -ConfirmRelease after reviewing the release checklist.'
}

foreach ($name in @('CLOJARS_USERNAME', 'CLOJARS_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "$name must be set in the current PowerShell process. CLOJARS_PASSWORD must contain a deploy token, not the account password."
    }
}

Push-Location $projectRoot
try {
    if (git status --porcelain) {
        throw 'The Git working tree must be clean before Clojars publication.'
    }

    $headTags = @(git tag --points-at HEAD)
    if ($headTags -notcontains $expectedTag) {
        throw "HEAD must be tagged $expectedTag before publication."
    }

    .\scripts\verify-phase-9.ps1
    .\scripts\verify-clojars-package.ps1

    Write-Host "==> Deploy io.github.ugurbay/binance-clj $version to Clojars"
    clojure -T:build deploy
    if ($LASTEXITCODE -ne 0) {
        throw "Clojars deployment failed with exit code $LASTEXITCODE."
    }

    Write-Host "Clojars publication completed for io.github.ugurbay/binance-clj $version." -ForegroundColor Green
}
finally {
    Pop-Location
}
