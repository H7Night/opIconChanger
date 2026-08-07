#!/bin/bash
# opIconChanger - Build Release APK
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# scripts/ 的上一级即项目根
cd "$SCRIPT_DIR/.."

echo "========================================"
echo "  opIconChanger - Build Release APK"
echo "========================================"

if [ ! -f "./gradlew" ]; then
    echo "[ERROR] gradlew not found!"
    exit 1
fi

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

echo "[1/2] Assembling release APK..."
./gradlew assembleRelease
echo "       Done."

APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ]; then
    SIZE=$(du -h "$APK" | cut -f1)
    echo "[2/2] APK: $APK ($SIZE)"
else
    echo "[WARN] APK not found."
fi
echo "Done."
