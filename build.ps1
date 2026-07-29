<#
.SYNOPSIS
    opIconChanger 一键构建脚本
.DESCRIPTION
    清理 → 编译 release APK → 输出到 app/build/outputs/apk/release/
.NOTES
    签名配置: keystore/debug.jks (alias: opiconchanger, storepass: android)
#>

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  opIconChanger - Build Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 gradlew 是否存在
$gradlew = Join-Path $projectRoot "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Host "[ERROR] gradlew.bat not found!" -ForegroundColor Red
    Write-Host "Please open this project in Android Studio first to generate the Gradle wrapper."
    exit 1
}

Write-Host "[1/3] Cleaning previous build..." -ForegroundColor Yellow
& $gradlew clean 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] Clean failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "       Done." -ForegroundColor Green

Write-Host "[2/3] Assembling release APK..." -ForegroundColor Yellow
& $gradlew assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] Build failed! Check errors above." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "       Done." -ForegroundColor Green

Write-Host "[3/3] Verifying output..." -ForegroundColor Yellow
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk"
if (Test-Path $apkPath) {
    $size = [math]::Round((Get-Item $apkPath).Length / 1KB, 1)
    Write-Host "       APK: $apkPath ($size KB)" -ForegroundColor Green
} else {
    Write-Host "[WARN] APK not found at expected path. Check build output above." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Build Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "To install:" -ForegroundColor White
Write-Host "  adb install app\build\outputs\apk\release\app-release.apk" -ForegroundColor Gray
Write-Host ""
Write-Host "Then enable in LSPosed → scope: 'System Launcher'" -ForegroundColor Gray
