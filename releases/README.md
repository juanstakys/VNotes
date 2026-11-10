# Test APK

`vnotes-android-debug.apk` is the debug-signed native Kotlin Android test build (app.vnotes, 3.3.1, Android 8+). It contains ARM64, ARMv7 and x86-64 native speech libraries; English model assets download on initial setup.

Install: `adb install -r releases/vnotes-android-debug.apk` from the repository root, or copy/open the file on your phone. The app requests microphone permission. Verify file integrity with `sha256sum -c SHA256SUMS` from this directory.

This APK is for testing. Keep the local ignored debug signing key for compatible updates. Use a private release key and a release build for distribution.
