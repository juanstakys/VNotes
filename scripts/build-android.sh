#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
# Reuse an optional local toolchain without adding generated files to Git.
if [[ -z "${JAVA_HOME:-}" && -x "$project_root/.build-tools/jdk/bin/java" ]]; then
  export JAVA_HOME="$project_root/.build-tools/jdk"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [[ -z "${ANDROID_HOME:-}" && -d "$project_root/.build-tools/sdk" ]]; then
  export ANDROID_HOME="$project_root/.build-tools/sdk"
fi
if [[ -d "$project_root/.build-tools/gradle-home" ]]; then
  export GRADLE_USER_HOME="$project_root/.build-tools/gradle-home"
fi
cd "$project_root/android"
if [[ ! -f .signing/debug.keystore ]]; then
  mkdir -p .signing
  keytool -genkeypair -keystore .signing/debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Android Debug,O=Android,C=US'
fi
if [[ -x "$project_root/.build-tools/gradle-8.9/bin/gradle" ]]; then
  "$project_root/.build-tools/gradle-8.9/bin/gradle" --no-daemon assembleDebug testDebugUnitTest lintDebug
else
  ./gradlew assembleDebug testDebugUnitTest lintDebug
fi
mkdir -p "$project_root/releases"
cp app/build/outputs/apk/debug/app-debug.apk "$project_root/releases/vnotes-android-debug.apk"
cd "$project_root/releases"
sha256sum vnotes-android-debug.apk > SHA256SUMS
