<#
.SYNOPSIS
    opIconChanger - Build Debug APK
.DESCRIPTION
    构建 debug 版本 APK（无签名，速度更快，适合开发调试）
#>
$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
Set-Location $projectRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  opIconChanger - Build Debug APK" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$gradlew = Join-Path $projectRoot "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Host "[ERROR] gradlew.bat not found!" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = if (Test-Path "$env:USERPROFILE\Abandon\Application\scoop\apps\openjdk17\current") {
    "$env:USERPROFILE\Abandon\Application\scoop\apps\openjdk17\current"
} elseif ($env:JAVA_HOME) { $env:JAVA_HOME } else { $null }

Write-Host "[1/2] Assembling debug APK..." -ForegroundColor Yellow
& $gradlew assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] Build failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "       Done." -ForegroundColor Green

Write-Host "[2/2] Verifying output..." -ForegroundColor Yellow
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkPath) {
    $size = [math]::Round((Get-Item $apkPath).Length / 1KB, 1)
    Write-Host "       APK: $apkPath ($size KB)" -ForegroundColor Green
} else {
    Write-Host "[WARN] APK not found." -ForegroundColor Yellow
}
Write-Host "Done." -ForegroundColor Green
