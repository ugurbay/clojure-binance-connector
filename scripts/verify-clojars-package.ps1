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

function Invoke-PackageCheck {
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

$version = (Get-Content -LiteralPath (Join-Path $projectRoot 'VERSION') -Raw).Trim()
$jarPath = Join-Path $projectRoot "target\binance-clj-$version.jar"
$pomPath = Join-Path $projectRoot "target\classes\META-INF\maven\io.github.ugurbay\binance-clj\pom.xml"
$probePath = Join-Path $projectRoot '.cache\clojars-consumer'

Push-Location $projectRoot
try {
    Invoke-PackageCheck 'Build Clojars JAR and POM' { clojure -T:build jar }

    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "Expected JAR was not created: $jarPath"
    }
    if (-not (Test-Path -LiteralPath $pomPath)) {
        throw "Expected POM was not created: $pomPath"
    }

    [xml] $pom = Get-Content -LiteralPath $pomPath -Raw
    if ($pom.project.groupId -ne 'io.github.ugurbay' -or
        $pom.project.artifactId -ne 'binance-clj' -or
        $pom.project.version -ne $version) {
        throw 'Generated Maven coordinates do not match io.github.ugurbay/binance-clj and VERSION.'
    }
    if ($pom.project.licenses.license.name -ne 'MIT License') {
        throw 'Generated POM does not contain the MIT license declaration.'
    }

    $jarCommand = if ($env:JAVA_HOME) {
        Join-Path $env:JAVA_HOME 'bin\jar.exe'
    }
    else {
        'jar'
    }
    $entries = & $jarCommand --list --file $jarPath
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect JAR contents (exit code $LASTEXITCODE)."
    }
    foreach ($requiredEntry in @(
            'binance_clj/core.clj',
            'META-INF/LICENSE',
            'META-INF/NOTICE.md',
            'META-INF/maven/io.github.ugurbay/binance-clj/pom.xml'
        )) {
        if ($entries -notcontains $requiredEntry) {
            throw "Required JAR entry is missing: $requiredEntry"
        }
    }

    Invoke-PackageCheck 'Install artifact into local Maven repository' { clojure -T:build install }

    New-Item -ItemType Directory -Path $probePath -Force | Out-Null
    Push-Location $probePath
    try {
        $deps = "{:deps {io.github.ugurbay/binance-clj {:mvn/version `"$version`"}}}"
        $expression = "(require '[binance-clj.core :as core]) (assert (= :ready (:status (core/runtime-info)))) (println (select-keys (core/runtime-info) [:name :phase :status]))"
        Invoke-PackageCheck 'Resolve and load the package as an external consumer' {
            clojure -Srepro -Sdeps $deps -M -e $expression
        }
    }
    finally {
        Pop-Location
    }

    Write-Host "Clojars package verification passed for io.github.ugurbay/binance-clj $version." -ForegroundColor Green
}
finally {
    Pop-Location
}
