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
