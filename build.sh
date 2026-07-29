#!/bin/bash
# opIconChanger - Build Release APK
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

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
