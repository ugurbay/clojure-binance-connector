[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchainsDir = Join-Path $projectRoot '.toolchains'
$archivePath = Join-Path $toolchainsDir 'temurin-25.zip'
$downloadUrl = 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk'

New-Item -ItemType Directory -Path $toolchainsDir -Force | Out-Null

$existingJdk = Get-ChildItem -LiteralPath $toolchainsDir -Directory -ErrorAction SilentlyContinue |
    Where-Object Name -Like 'jdk-25*' |
    Sort-Object Name -Descending |
    Select-Object -First 1

if ($existingJdk) {
    Write-Host "Portable JDK 25 already exists: $($existingJdk.FullName)"
    exit 0
}

Write-Host 'Downloading the latest Temurin JDK 25 from the official Adoptium API...'
& curl.exe -L --fail --output $archivePath $downloadUrl
if ($LASTEXITCODE -ne 0) {
    throw "JDK download failed with exit code $LASTEXITCODE."
}

try {
    Expand-Archive -LiteralPath $archivePath -DestinationPath $toolchainsDir -Force
}
finally {
    Remove-Item -LiteralPath $archivePath -Force -ErrorAction SilentlyContinue
}

$installedJdk = Get-ChildItem -LiteralPath $toolchainsDir -Directory |
    Where-Object Name -Like 'jdk-25*' |
    Sort-Object Name -Descending |
    Select-Object -First 1

if (-not $installedJdk) {
    throw 'JDK archive was extracted, but no jdk-25* directory was found.'
}

& (Join-Path $installedJdk.FullName 'bin\java.exe') -version
Write-Host "Portable JDK installed: $($installedJdk.FullName)" -ForegroundColor Green
