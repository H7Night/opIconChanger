<#
.SYNOPSIS
    opIconChanger - Build Release APK & Install to device
.DESCRIPTION
    clean → assembleRelease → verify → adb install
#>
$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
Set-Location $projectRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  opIconChanger - Build & Install" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$gradlew = Join-Path $projectRoot "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Host "[ERROR] gradlew.bat not found!" -ForegroundColor Red
    exit 1
}

# --- JAVA_HOME ---
$env:JAVA_HOME = if (Test-Path "$env:USERPROFILE\Abandon\Application\scoop\apps\openjdk17\current") {
    "$env:USERPROFILE\Abandon\Application\scoop\apps\openjdk17\current"
} elseif ($env:JAVA_HOME) { $env:JAVA_HOME } else { $null }

# --- Build ---
Write-Host "[1/2] Assembling release APK..." -ForegroundColor Yellow
& $gradlew assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] Build failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "       Done." -ForegroundColor Green

$apkPath = Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "[FAIL] APK not found at $apkPath" -ForegroundColor Red
    exit 1
}

# --- Install ---
Write-Host "[2/2] Installing to device..." -ForegroundColor Yellow
$devices = adb devices 2>$null | Select-String -Pattern "\tdevice$"
if (-not $devices) {
    Write-Host "[FAIL] No device connected!" -ForegroundColor Red
    exit 1
}
adb install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] Install failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "       Installed." -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Done! Enable in LSPosed → scope: System Launcher" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
