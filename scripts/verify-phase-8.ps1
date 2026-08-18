[CmdletBinding()]
param(
    [switch] $RunPublicWebSocketTestnet,
    [switch] $RunSignedUserStreamTestnet,
    [switch] $RunUserStreamEventsTestnet
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

function Invoke-WithEnvironmentFlag {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [string] $EnvironmentVariable
    )
    $previousValue = [Environment]::GetEnvironmentVariable($EnvironmentVariable, 'Process')
    try {
        [Environment]::SetEnvironmentVariable($EnvironmentVariable, 'true', 'Process')
        Invoke-PhaseCheck $Name { clojure -X:integration-test }
    }
    finally {
        [Environment]::SetEnvironmentVariable($EnvironmentVariable, $previousValue, 'Process')
    }
}

Push-Location $projectRoot
try {
    Invoke-PhaseCheck 'Environment' { clojure -M:verify-environment }
    Invoke-PhaseCheck 'Run smoke entry point' { clojure -M:run }
    Invoke-PhaseCheck 'Unit and WebSocket contract tests' { clojure -X:test }
    Invoke-PhaseCheck 'Offline integration harness' { clojure -X:integration-test }

    if ($RunPublicWebSocketTestnet) {
        Invoke-WithEnvironmentFlag 'Credential-free market WebSocket Testnet acceptance' `
            'BINANCE_RUN_PUBLIC_WEBSOCKET_TESTNET'
    }

    if ($RunSignedUserStreamTestnet) {
        if ([string]::IsNullOrWhiteSpace($env:BINANCE_API_KEY) -or
            [string]::IsNullOrWhiteSpace($env:BINANCE_API_SECRET)) {
            throw 'BINANCE_API_KEY and BINANCE_API_SECRET are required for signed User Data Stream acceptance.'
        }
        Invoke-WithEnvironmentFlag 'Signed User Data Stream Testnet subscription acceptance' `
            'BINANCE_RUN_SIGNED_WEBSOCKET_TESTNET'
    }

    if ($RunUserStreamEventsTestnet) {
        if ([string]::IsNullOrWhiteSpace($env:BINANCE_API_KEY) -or
            [string]::IsNullOrWhiteSpace($env:BINANCE_API_SECRET)) {
            throw 'BINANCE_API_KEY and BINANCE_API_SECRET are required for User Data Stream event acceptance.'
        }
        Invoke-WithEnvironmentFlag 'Signed User Data Stream MARKET order event acceptance' `
            'BINANCE_RUN_USER_STREAM_EVENTS_TESTNET'
    }

    Invoke-PhaseCheck 'Lint' { clojure -M:lint }
    Invoke-PhaseCheck 'Format check' { clojure -M:format-check }
    Invoke-PhaseCheck 'Benchmark harness' { clojure -M:benchmark }

    if ($RunUserStreamEventsTestnet) {
        Write-Host 'Phase 8 verification passed, including real Testnet order/account events.' `
            -ForegroundColor Green
    }
    elseif ($RunSignedUserStreamTestnet) {
        Write-Host 'Phase 8 protocol verification passed; real Testnet order/account event acceptance was not requested.' `
            -ForegroundColor Yellow
    }
    else {
        Write-Host 'Phase 8 implementation verification passed; signed User Data Stream acceptance was not requested.' `
            -ForegroundColor Yellow
    }
}
finally {
    Pop-Location
}
