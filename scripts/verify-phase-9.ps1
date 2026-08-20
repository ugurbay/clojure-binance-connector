[CmdletBinding()]
param(
    [switch] $RunFullTestnet,
    [switch] $RunPublicAggTrade,
    [ValidateRange(1, 300)] [int] $SoakSeconds = 10
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$portableJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.toolchains') -Directory -ErrorAction SilentlyContinue |
    Where-Object Name -Like 'jdk-25*' |
    Sort-Object Name -Descending |
    Select-Object -First 1

if ($portableJdk) {
    $env:JAVA_HOME = $portableJdk.FullName
    $env:JAVA_CMD = Join-Path $portableJdk.FullName 'bin\java.exe'
    $env:Path = "$(Join-Path $portableJdk.FullName 'bin');$env:Path"
    Write-Host "Using portable JDK: $($portableJdk.FullName)"
}

function Invoke-PhaseCheck {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [scriptblock] $Command
    )
    Write-Host "==> $Name"
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

function Invoke-FullTestnetAcceptance {
    $required = @('BINANCE_API_KEY', 'BINANCE_API_SECRET')
    foreach ($name in $required) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            throw "$name is required for the full Testnet release gate."
        }
    }

    $flags = @(
        'BINANCE_RUN_PUBLIC_TESTNET',
        'BINANCE_RUN_SIGNED_TESTNET',
        'BINANCE_RUN_PUBLIC_WEBSOCKET_TESTNET',
        'BINANCE_RUN_SIGNED_WEBSOCKET_TESTNET',
        'BINANCE_RUN_WEBSOCKET_RESTORE_TESTNET',
        'BINANCE_RUN_RELEASE_TESTNET'
    )
    $previous = @{}
    try {
        foreach ($name in $flags) {
            $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, 'true', 'Process')
        }
        Invoke-PhaseCheck 'Full Spot Testnet REST, WebSocket restore, and LIMIT lifecycle acceptance' {
            clojure -X:integration-test
        }
    }
    finally {
        foreach ($name in $flags) {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
    }
}

function Invoke-PublicAggTradeAcceptance {
    $name = 'BINANCE_RUN_PUBLIC_AGGTRADE_PRODUCTION'
    $previous = [Environment]::GetEnvironmentVariable($name, 'Process')
    try {
        [Environment]::SetEnvironmentVariable($name, 'true', 'Process')
        Invoke-PhaseCheck 'Credential-free production aggTrade and reconnect acceptance' {
            clojure -X:integration-test
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable($name, $previous, 'Process')
    }
}

Push-Location $projectRoot
try {
    Invoke-PhaseCheck 'Environment' { clojure -M:verify-environment }
    Invoke-PhaseCheck 'Run smoke entry point' { clojure -M:run }
    Invoke-PhaseCheck 'Unit and contract tests' { clojure -X:test }
    Invoke-PhaseCheck 'Offline integration harness' { clojure -X:integration-test }

    if ($RunFullTestnet) {
        Invoke-FullTestnetAcceptance
    }

    if ($RunPublicAggTrade) {
        Invoke-PublicAggTradeAcceptance
    }

    Invoke-PhaseCheck 'Credential and secret leakage scan' { clojure -M:secret-scan }
    Invoke-PhaseCheck 'Lint' { clojure -M:lint }
    Invoke-PhaseCheck 'Format check' { clojure -M:format-check }
    Invoke-PhaseCheck 'Performance baseline' { clojure -M:benchmark }

    $previousSoak = [Environment]::GetEnvironmentVariable('BINANCE_SOAK_SECONDS', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('BINANCE_SOAK_SECONDS', $SoakSeconds, 'Process')
        Invoke-PhaseCheck 'Bounded event soak' { clojure -M:soak }
    }
    finally {
        [Environment]::SetEnvironmentVariable('BINANCE_SOAK_SECONDS', $previousSoak, 'Process')
    }

    if ($RunFullTestnet) {
        Write-Host 'Phase 9 release verification passed.' -ForegroundColor Green
    }
    else {
        Write-Host 'Phase 9 offline verification passed; full Testnet release acceptance was not requested.' `
            -ForegroundColor Yellow
    }
}
finally {
    Pop-Location
}
