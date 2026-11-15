param(
    [switch]$SkipGoCore
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$core = Join-Path $root "core"
$android = Join-Path $root "android"
$dist = Join-Path $root "dist"

& (Join-Path $PSScriptRoot "build-cmfa-aar.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "CMFA AAR build failed with exit code $LASTEXITCODE"
}

if (-not $SkipGoCore) {
    if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
        throw "Go toolchain not found. Install Go or rerun with -SkipGoCore to use the prebuilt libgbfcore.so."
    }

    $coreBinary = Join-Path $android "app\src\main\jniLibs\arm64-v8a\libgbfcore.so"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $coreBinary) | Out-Null

    $oldGoOS = $env:GOOS
    $oldGoArch = $env:GOARCH
    $oldCgo = $env:CGO_ENABLED
    try {
        $env:GOOS = "android"
        $env:GOARCH = "arm64"
        $env:CGO_ENABLED = "0"
        Write-Host "[*] Building Go core for android/arm64"
        Push-Location $core
        try {
            & go build -trimpath "-ldflags=-s -w" -o $coreBinary .
            if ($LASTEXITCODE -ne 0) {
                throw "Go core build failed with exit code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    } finally {
        $env:GOOS = $oldGoOS
        $env:GOARCH = $oldGoArch
        $env:CGO_ENABLED = $oldCgo
    }
}

$gradle = Join-Path $root "gradle-9.7.1\bin\gradle.bat"
if (-not (Test-Path -LiteralPath $gradle)) {
    $gradle = Join-Path $android "gradlew.bat"
}
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle 9.7.1 not found. Place it at gradle-9.7.1\bin\gradle.bat or restore android\gradlew.bat."
}

Write-Host "[*] Building Android APK"
Push-Location $android
try {
    & $gradle :app:assembleDebug -x :app:buildGoCore
    if ($LASTEXITCODE -ne 0) {
        throw "Android build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$apk = Join-Path $android "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    throw "APK not found: $apk"
}

$version = "1.0.0"
$configFile = Join-Path $core "config\config.go"
if (Test-Path -LiteralPath $configFile) {
    $match = Select-String -Path $configFile -Pattern 'AppVersion\s*=\s*"([^"]+)"' | Select-Object -First 1
    if ($match -and $match.Matches.Count -gt 0) {
        $version = $match.Matches[0].Groups[1].Value
    }
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null
$output = Join-Path $dist "GBF-Cache-$version.apk"
Copy-Item -LiteralPath $apk -Destination $output -Force

Write-Host "[+] APK: $output"
