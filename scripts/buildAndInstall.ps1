<#
.SYNOPSIS
    opIconChanger - Build Debug APK & Install to device
.DESCRIPTION
    clean → assembleDebug → verify → adb install
    release 由 GitHub Actions 签名构建
#>
$ErrorActionPreference = "Stop"
# scripts/ 的上一级即项目根
$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
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
Write-Host "[1/2] Assembling debug APK..." -ForegroundColor Yellow
& $gradlew assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] Build failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "       Done." -ForegroundColor Green

$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "[FAIL] APK not found at $apkPath" -ForegroundColor Red
    exit 1
}

# --- Install ---
Write-Host "[2/2] Installing to device..." -ForegroundColor Yellow
$devices = adb devices 2>$null | Select-String -Pattern "\tdevice(\s|$)"
if (-not $devices) {
    Write-Host "[FAIL] No device connected!" -ForegroundColor Red
    exit 1
}

# 先尝试 -r 覆盖安装；若因签名不一致失败（debug → release 签名切换），
# 自动卸载旧版本后重新安装
$installOut = (adb install -r $apkPath 2>&1) | Out-String
if ($LASTEXITCODE -ne 0) {
    if ($installOut -match "UPDATE_INCOMPATIBLE|signatures do not match") {
        Write-Host "[WARN] 已安装版本签名不一致，卸载旧版本后重新安装..." -ForegroundColor Yellow
        adb uninstall com.opiconchanger
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[FAIL] 卸载旧版本失败!" -ForegroundColor Red
            exit $LASTEXITCODE
        }
        $installOut = (adb install $apkPath 2>&1) | Out-String
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] Install failed!`n$installOut" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}
Write-Host "       Installed." -ForegroundColor Green
Write-Host "       adb output:`n$installOut"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Done! Enable in LSPosed → scope: System Launcher" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
