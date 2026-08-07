@echo off
chcp 65001 >nul
echo ========================================
echo   opIconChanger - Build Script
echo ========================================
echo.

cd /d "%~dp0.."

rem --- Release 签名（自动注入签名环境变量） ---
set "RELEASE_KEYSTORE_FILE="
set "RELEASE_KEYSTORE_PASS="
set "RELEASE_KEYSTORE_ALIAS="
if exist "keystore\release.jks" if exist "keystore\keystore.pass" (
    set /p RELEASE_KEYSTORE_PASS=<keystore\keystore.pass
    set "RELEASE_KEYSTORE_FILE=%CD%\keystore\release.jks"
    set "RELEASE_KEYSTORE_ALIAS=opiconchanger"
    echo [SIGN] Release 签名已启用 (keystore\release.jks)
) else (
    echo [WARN] 未找到 keystore\release.jks 或 keystore\keystore.pass，构建未签名 APK
)

echo [1/2] Assembling release APK...
call .\gradlew.bat assembleRelease
if %errorlevel% neq 0 (
    echo.
    echo [FAIL] Build failed! Check errors above.
    pause
    exit /b %errorlevel%
)

echo.
echo [2/2] Build complete!
echo.
echo Output: app\build\outputs\apk\release\app-release.apk
echo.
echo To install:
echo   adb install app\build\outputs\apk\release\app-release.apk
echo.
pause
