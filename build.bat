@echo off
chcp 65001 >nul
echo ========================================
echo   opIconChanger - Build Script
echo ========================================
echo.

cd /d "%~dp0"

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
