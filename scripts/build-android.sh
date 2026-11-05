#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root/android"
if [[ ! -f .signing/debug.keystore ]]; then
  mkdir -p .signing
  keytool -genkeypair -keystore .signing/debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Android Debug,O=Android,C=US'
fi
./gradlew assembleDebug testDebugUnitTest lintDebug
mkdir -p "$project_root/releases"
cp app/build/outputs/apk/debug/app-debug.apk "$project_root/releases/vnotes-android-debug.apk"
cd "$project_root/releases"
sha256sum vnotes-android-debug.apk > SHA256SUMS
