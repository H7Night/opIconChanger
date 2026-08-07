#!/bin/bash
# opIconChanger - Build Release APK & Install to device
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# scripts/ 的上一级即项目根
cd "$SCRIPT_DIR/.."

echo "========================================"
echo "  opIconChanger - Build & Install"
echo "========================================"

if [ ! -f "./gradlew" ]; then
    echo "[ERROR] gradlew not found!"
    exit 1
fi

# --- JAVA_HOME ---
if [ -z "$JAVA_HOME" ] && [ -d "$HOME/Abandon/Application/scoop/apps/openjdk17/current" ]; then
    export JAVA_HOME="$HOME/Abandon/Application/scoop/apps/openjdk17/current"
fi

# --- Release 签名（自动注入签名环境变量） ---
export RELEASE_KEYSTORE_FILE=""
export RELEASE_KEYSTORE_PASS=""
export RELEASE_KEYSTORE_ALIAS=""
if [ -f "keystore/release.jks" ] && [ -f "keystore/keystore.pass" ]; then
    export RELEASE_KEYSTORE_FILE="$PWD/keystore/release.jks"
    export RELEASE_KEYSTORE_PASS="$(tr -d '\r\n' < keystore/keystore.pass)"
    export RELEASE_KEYSTORE_ALIAS="opiconchanger"
    echo "[SIGN] Release 签名已启用 (keystore/release.jks)"
else
    echo "[WARN] 未找到 keystore/release.jks 或 keystore/keystore.pass，构建未签名 APK"
fi

# --- Build ---
echo "[1/2] Assembling release APK..."
./gradlew assembleRelease
echo "       Done."

APK="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
    echo "[FAIL] APK not found at $APK"
    exit 1
fi

# --- Install ---
echo "[2/2] Installing to device..."
DEVICES=$(adb devices 2>/dev/null | grep -c 'device$')
if [ "$DEVICES" -eq 0 ]; then
    echo "[FAIL] No device connected!"
    exit 1
fi
adb install -r "$APK"
echo "       Installed."

echo ""
echo "========================================"
echo "  Done! Enable in LSPosed → scope: System Launcher"
echo "========================================"
