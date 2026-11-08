# VNotes

Native Kotlin Android app for offline English voice notes and a minimal TODO/DONE list. The original vanilla JavaScript PWA is preserved under `web/`.

## Install on your phone

The test build is **[releases/vnotes-android-debug.apk](releases/vnotes-android-debug.apk)**. Android 8.0+ (API 26); ARM64, ARMv7 and x86-64 builds are included in one APK. This is a debug-signed build for testing, not a Play Store release.

Copy the APK to your phone and open it, allowing installation from that source when Android asks. Or enable USB debugging, authorize the computer, and run:

```sh
adb install -r releases/vnotes-android-debug.apk
```

No localhost server, USB port forwarding or browser is required. Start VNotes, tap **Download English voice model** while connected, then allow microphone access when recording or testing the microphone. Once downloaded, model inference and notes work offline.

The Kotlin app uses native Android `AudioRecord` and native Moonshine Tiny, not WebView or browser speech APIs. Its model files differ from the PWA and need their own one-time download.

The 3.3 update installs over the previous APK and preserves notes, settings and downloaded model files.

## Data and behavior

- TODO/DONE tabs, settings at bottom left, shared record/stop/send button at bottom right. Short lists sit at the bottom, growing upward with the newest note at the top; long lists scroll normally.
- Settings → **Material Design 3** switches the entire app between Material components and the default **Original Design**. The choice is saved, and switching preserves your unsent draft and selected tab.
- During recording, the text box becomes a microphone level bar and timer. A live transcript appears above it and revises as you speak. Stop or silence detection flushes the final phrase and saves one note; recorded edits still require review. Separate Cancel discards the recording and preview.
- Energy VAD: 300ms minimum sound, configurable silence interval, up to 2-minute capture, logarithmic sensitivity slider and a settings microphone test.
- Swipe left anywhere on the main screen to open DONE; swipe right in DONE to return to TODO. Right-swiping a TODO item still marks it done. Edit only through the item’s **··· → Edit** menu. Restore and individual permanent deletion are in the DONE item menu; the trash button erases all done notes after confirmation.
- Moves show a non-modal snackbar immediately above the input, with **UNDO** on the right.
- Settings exports/imports JSON backups via Android's document picker. Import the PWA's JSON backup to transfer existing notes; the app cannot access browser IndexedDB automatically. Existing matching IDs remain unchanged.
- Notes are stored in private SQLite. Failed recording audio stays in private storage for Retry/Discard. Uninstalling clears app data; export a backup before uninstalling.
- Microphone capture stops when the app leaves the foreground. No background recording service or audio uploads. Live recognition runs on a single background executor and reuses the existing English Tiny model, with no new download required. Update speed depends on phone CPU. Cancelling skips queued recognition and waits for an active native call to return; cancelled finalization retains retry audio. Failed live streams fall back to transcription from the saved audio.

## Repository

```text
android/         Native Kotlin app, Gradle wrapper and unit tests
web/             Preserved PWA and its tests/vendor files
scripts/         Reproducible build helper
releases/        Installable test APK and checksum
docs/           Reconstruction specifications and validation records
```

## Build

Install JDK 17 and Android SDK platform 35 / build tools, set `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
./scripts/build-android.sh
```

Alternatively open `android/` in Android Studio. Dependencies are pinned: Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Moonshine Voice 0.1.5, Material Components 1.10.0, AppCompat 1.6.1. UI tests use Robolectric 4.14.1. Initial dependency resolution needs internet. Keep `android/.signing/debug.keystore` (ignored by Git) for future debug updates; a different signing key cannot update the installed app in place. Do not use this public-standard debug identity for production signing.

The packaged model-downloader uses HTTPS and native Moonshine's file catalog. Models are not bundled in the APK. Voice recording is entirely local. See [native reconstruction guide](docs/ANDROID-REBUILD-GUIDE.md) and [validation](docs/ANDROID-VALIDATION.md).

To run the old PWA: `cd web && python3 -m http.server 5173 --bind 127.0.0.1`. Its original decisions remain in [the PWA reconstruction guide](docs/REBUILD-GUIDE.md).
