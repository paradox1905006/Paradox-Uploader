#!/usr/bin/env bash
# Usage: ./build_release.sh [path/to/keystore.jks]
# Builds a signed release APK on Ubuntu. Needs: JDK 17+, Android SDK (set ANDROID_HOME or it uses ~/Android/Sdk).
set -euo pipefail
cd "$(dirname "$0")"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[ -d "$ANDROID_HOME" ] || { echo "ERROR: Android SDK not found at $ANDROID_HOME (set ANDROID_HOME)"; exit 1; }
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Generate gradle wrapper if missing (needs a 'gradle' command once)
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  command -v gradle >/dev/null || { echo "ERROR: install Gradle 8.11.1 once (see /opt/gradle-8.11.1/bin) to generate the wrapper"; exit 1; }
  gradle wrapper --gradle-version 8.11.1
fi

yes | "$ANDROID_HOME"/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null 2>&1 || true
./gradlew assembleRelease

BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
IN=app/build/outputs/apk/release/app-release-unsigned.apk
"$BT/zipalign" -f -v -p 4 "$IN" app-aligned.apk >/dev/null

KS="${1:-$(ls "$HOME"/*.jks "$HOME"/*.jsk 2>/dev/null | head -1)}"
[ -f "$KS" ] || { echo "ERROR: keystore not found. Pass it: ./build_release.sh ~/mykeystore.jks"; exit 1; }
"$BT/apksigner" sign --ks "$KS" --out ParadoxUploader-release.apk app-aligned.apk
rm -f app-aligned.apk
"$BT/apksigner" verify ParadoxUploader-release.apk && echo "DONE: $(pwd)/ParadoxUploader-release.apk"
