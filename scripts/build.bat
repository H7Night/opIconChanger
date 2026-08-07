@echo off
chcp 65001 >nul
echo ========================================
echo   opIconChanger - Build Debug APK
echo   (本地只构建 debug；release 由 GitHub Actions 签名构建)
echo ========================================
echo.

cd /d "%~dp0.."

echo [1/2] Assembling debug APK...
call .\gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo.
    echo [FAIL] Build failed! Check errors above.
    pause
    exit /b %errorlevel%
)

echo.
echo [2/2] Build complete!
echo.
echo Output: app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install:
echo   adb install app\build\outputs\apk\debug\app-debug.apk
echo.
pause
