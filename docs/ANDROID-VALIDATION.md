# Native Android validation

Build: VNotes 3.0.0 (version code 1), package app.vnotes.

## Completed

- Native Kotlin sources compiled with Kotlin 2.0.21, Android Gradle Plugin 8.7.3 and JDK 17. No WebView or browser capture bridge is used.
- `assembleDebug testDebugUnitTest` succeeded. Five Kotlin unit tests passed, zero failures/errors: leading silence/click rejection, speech/minimum duration and silence stop at 16/44.1/48 kHz sample counts, quiet countdown reset, full logarithmic sensitivity range, and bounded lossless long-note segmentation.
- Android lint passed after moving API27/API35 theme attributes into version-qualified resource folders. Remaining warnings concern English-only UI strings, optional backup configuration and minor view implementation suggestions; no lint errors.
- Preserved PWA tests pass after moving source into web/.
- APK manifest verified with Android build tools: app.vnotes, 3.0.0, minimum SDK 26, target SDK 35, launcher VNotes.
- APK v2 signature verified by apksigner. Certificate is the standard-form local Android debug identity, not a production identity. Local key is preserved under ignored android/.signing/ for future compatible test updates.
- APK archive contains compiled DEX, manifest/resources, and Moonshine/JNI/ONNX Runtime .so files for ARM64, ARMv7 and x86-64.
- APK zip alignment verified. All three ARM64 native libraries have 16KB-aligned ELF LOAD segments.
- APK copied to releases/vnotes-android-debug.apk; SHA-256 in releases/SHA256SUMS.
- Original PWA source preserved under web/, native source under android/, build helper under scripts/, separate reconstruction guides under docs/.

## Verification limits

No physical Android phone or Android emulator was used to install/run this APK. JVM signal tests, packaging/signature validation and static checks do not verify microphone hardware or actual native JNI inference on the user's device. This is an installable debug test build; do not describe it as device-validated or production-ready.

On the phone, verify:

1. Install/open, typed create/edit, TODO/DONE order and persistence after reopening.
2. Denied/granted microphone permission and Android's global microphone switch.
3. Settings sensitivity slider, native live meter and Stop/close/background cleanup.
4. English Tiny download completion/error/retry and recording/transcription online, then cold airplane-mode relaunch and a new transcription after download.
5. Silence auto-stop, quiet/noisy speech, manual stop, Cancel, 2-minute cap and app backgrounding. Confirm microphone privacy indicator clears.
6. Recorded replacement review/cancel/save, failed-audio retry across restart, cooperative transcription cancel.
7. Swipe/row menus/Undo/erase confirmations, file-picker backups and import from PWA JSON without duplicates.
8. Keyboard resizing, display/font sizes, TalkBack and orientation.

Model files are downloaded by the native SDK during setup and are not included in the APK. The existing browser model cache is intentionally not shared. Export browser notes and import their JSON backup to migrate user data.
