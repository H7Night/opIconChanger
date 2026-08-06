#!/bin/bash
# opIconChanger - Build Debug APK
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# scripts/ 的上一级即项目根
cd "$SCRIPT_DIR/.."

echo "========================================"
echo "  opIconChanger - Build Debug APK"
echo "========================================"

if [ ! -f "./gradlew" ]; then
    echo "[ERROR] gradlew not found!"
    exit 1
fi

# set JAVA_HOME for common environments
if [ -z "$JAVA_HOME" ] && [ -d "$HOME/Abandon/Application/scoop/apps/openjdk17/current" ]; then
    export JAVA_HOME="$HOME/Abandon/Application/scoop/apps/openjdk17/current"
fi

echo "[1/2] Assembling debug APK..."
./gradlew assembleDebug
echo "       Done."

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    SIZE=$(du -h "$APK" | cut -f1)
    echo "[2/2] APK: $APK ($SIZE)"
else
    echo "[WARN] APK not found."
fi
echo "Done."
