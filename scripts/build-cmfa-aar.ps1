$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$cmfa = Join-Path $root "third_party\cmfa-aar"
$appLibs = Join-Path $root "android\app\libs"

$gradle = Join-Path $root "gradle-8.10.2\bin\gradle.bat"
if (-not (Test-Path -LiteralPath $gradle)) {
    $gradle = Join-Path $cmfa "gradlew.bat"
}
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle 8.10.2 not found. Place it at gradle-8.10.2\bin\gradle.bat or restore third_party\cmfa-aar\gradlew.bat."
}

Write-Host "[*] Building CMFA AARs in $cmfa"
Push-Location $cmfa
try {
    & $gradle :common:assembleRelease :core:assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "CMFA AAR build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$coreAar = Join-Path $cmfa "core\build\outputs\aar\core-release.aar"
$commonAar = Join-Path $cmfa "common\build\outputs\aar\common-release.aar"
foreach ($aar in @($coreAar, $commonAar)) {
    if (-not (Test-Path -LiteralPath $aar)) {
        throw "Expected AAR not found: $aar"
    }
}

New-Item -ItemType Directory -Force -Path $appLibs | Out-Null
Copy-Item -LiteralPath $coreAar -Destination (Join-Path $appLibs "cmfa-core-release.aar") -Force
Copy-Item -LiteralPath $commonAar -Destination (Join-Path $appLibs "cmfa-common-release.aar") -Force

Write-Host "[+] CMFA AARs copied to android\app\libs"
