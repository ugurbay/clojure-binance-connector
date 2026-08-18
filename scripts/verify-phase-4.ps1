[CmdletBinding()]
param()

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
        [Parameter(Mandatory)]
        [string] $Name,

        [Parameter(Mandatory)]
        [scriptblock] $Command
    )

    Write-Host "==> $Name"
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

Push-Location $projectRoot
try {
    Invoke-PhaseCheck 'Environment' { clojure -M:verify-environment }
    Invoke-PhaseCheck 'Run smoke entry point' { clojure -M:run }
    Invoke-PhaseCheck 'Unit and HTTP contract tests' { clojure -X:test }
    Invoke-PhaseCheck 'Offline integration harness' { clojure -X:integration-test }
    Invoke-PhaseCheck 'Lint' { clojure -M:lint }
    Invoke-PhaseCheck 'Format check' { clojure -M:format-check }
    Invoke-PhaseCheck 'Benchmark harness' { clojure -M:benchmark }
    Write-Host 'Phase 4 verification passed.' -ForegroundColor Green
}
finally {
    Pop-Location
}
