# Native Android validation

Latest build: VNotes 3.3.1 (version code 5), package app.vnotes.

## Android-only repository cleanup — 2026-11-09

- Removed the browser app, its tests/assets/vendored dependencies and browser-specific reconstruction/validation documents.
- Updated the README, Android guide and release instructions for the Android-only layout. Preserved the stable backup format and existing native build/install paths.
- Native app sources, build configuration, signing key and the 3.3.1 APK are unchanged; no new application build is needed for this repository cleanup. Verification for this change checks remaining documentation links, removed-file references and the existing APK checksum.

## 3.3.1 sliding tabs and whole-card swipe — 2026-11-07

- Final `assembleDebug testDebugUnitTest lintDebug` passed. All 21 tests pass with zero failures/errors: 16 signal/navigation/live-session tests and five native UI tests. Lint reports no errors.
- New UI regressions verify the outer Material card follows a completion drag while its inner row stays at translation zero; cancellation returns the card to its original position, and releasing a qualifying swipe moves the note to DONE.
- Tested both designs with TODO -> DONE entering from the right and reverse navigation entering from the left. Rapid reversal discards the stale outgoing page, and after animation exactly one page remains at translation zero. Existing design-switch/draft/tab restoration tests still pass.
- Transitions use two real scroll pages in a clipped host, 220ms deceleration, fixed top/bottom controls, stale-callback generation protection, cleanup on background and no animation when disabled by Android or before layout. Activity swipe navigation and tab taps share this path. Outgoing pages cannot receive note touches during a slide.
- Final APK v2 signature verified, same certificate as 3.3.0; 16KB-aware zip alignment passed. Manifest verified app.vnotes, 3.3.1/code 5, minimum API26. Updated releases APK and SHA-256. Notes/model/audio storage formats are unchanged.
- No physical phone/emulator run was performed. On-device verify animation smoothness, both tap/swipe navigation directions, rapid reversal, keyboard-open transitions, scrolling versus horizontal gestures, complete-card drag/cancel/complete and undo in both designs. Native microphone/transcription code is unchanged.

## 3.3 bottom-up lists and selectable design — 2026-11-07

- Final `assembleDebug testDebugUnitTest lintDebug` passed. All 19 tests pass, zero failures/errors: 16 existing VAD/swipe/live-session regressions and three native UI tests. Lint reports no errors.
- Robolectric/API28 exercises real theme resources and native Activity views. Verified Original remains default, short content measures at the bottom of a fill-viewport scroll area, and adding notes retains newest-first order. Verified Material segmented tabs, card rows, FABs, outlined text inputs, settings switch/slider/dropdown and dialog construction.
- The toggle test uses Android's actual Activity recreation path, checks the persisted preference, then verifies the new Material screen retains an unsent draft and DONE selection. SQLite connections close when an old Activity is destroyed. Design changes are blocked during speech/model operations.
- TextInputLayout children use LinearLayout.LayoutParams and its themed context; the tests caught and prevented a runtime field-construction failure before release. Material snackbar uses the library component anchored above composer controls; Original retains its existing bar. This snackbar behavior has not been exercised on hardware in this update.
- Final APK v2 signature and 16KB-aware zip alignment verified. Package app.vnotes, version 3.3.0/code 4, minimum API26. Signing certificate matches the previous release, allowing in-place updates without clearing notes/settings/model files. Deliverable and SHA-256 updated in releases/.
- No phone/emulator run was performed. On-device check both designs with short/overflowing TODO and DONE lists, new text/voice additions, scrolling, keyboard open, large font sizes, gestures, menus, edit/review dialogs, undo placement/action, recording feedback and model setup. Toggle both directions and relaunch to check persistence. Native microphone/transcription behavior is unchanged and still requires device testing.
- Reconstruction guide documents viewport anchoring, Material component selection, shared behavior, palette resources, saved preference and recreation state.

## 3.2 live transcription — 2026-11-07

- Final `assembleDebug testDebugUnitTest lintDebug` passed. All 16 JVM tests pass: previous VAD/swipe cases plus six live-session regressions. No lint errors.
- Tests verify partial text arrives before Stop, immutable audio ownership, ordered feeding/final flushing, cancellation skipping queued recognition, suppression of late events, recoverable stream failures, bounded input and line-ID-based revision without duplicate words. Tests use a deterministic fake backend; they do not execute the Android JNI speech engine.
- Integrated the pinned Moonshine 0.1.5 start/addAudio/stop stream API. Explicitly enable decode_incomplete_lines=true, verified as a supported key in the shipped native binary and against the native core path. Reuse the existing Tiny model and model directory; no migration/download is introduced.
- Final APK v2 signature verified; certificate matches the previous installed-version APK. Package app.vnotes, 3.2.0/code 3. Zip alignment verified and release checksum updated.
- No physical Android/emulator live-speech test was performed. On the phone, verify words appear while continuing to speak, hypotheses revise without duplication, pauses shorter than the configured silence duration remain one note, manual/silence Stop retains the final phrase, Cancel leaves no note/preview, recorded edits remain unchanged until Save, repeated takes do not show stale text, and airplane-mode operation still works. Check responsiveness and memory on long takes and weak CPUs.
- Stream errors preserve raw PCM and fall back to existing saved-audio recognition. Recording cancellation clears the live preview immediately; finalization cancellation retains retry audio. Buffered retry-file output avoids per-float disk writes without changing its format.

## 3.1 gesture and snackbar update — 2026-11-05

- `assembleDebug testDebugUnitTest lintDebug` passed. All 10 JVM tests pass: five existing VAD/splitting tests plus five navigation regressions covering left TODO -> DONE, right DONE -> TODO, right TODO item -> completion, no left-DONE destruction, and rejection of vertical/short gestures. Lint reports no errors.
- Snackbar is a non-modal horizontal bar immediately above the composer, message on left and UNDO on right, with accessible timeout handling. No move AlertDialog remains. Undo retains later text edits and cannot resurrect erased records.
- Activity dispatch handles navigation on the whole main screen, including blank space and controls; child gestures are cancelled when navigating. Settings/edit dialogs retain their own gestures. Editing is only reachable from the ellipsis menu; row-body taps do not open it. Permanent deletion has no swipe entry point.
- Package metadata verified as 3.1.0/code 2, minimum SDK 26. APK v2 signature and zip alignment verified; signing certificate matches the previous 3.0.0 release, allowing in-place installation without clearing data or model files. Checksum updated.
- No phone/emulator UI test was performed for this update. On-device verify snackbar placement with keyboard open, UNDO, swipes starting on rows/blank space/composer/tabs, vertical scrolling, ellipsis edit, and explicit erase controls. Recording/finishing still blocks navigation.

The following baseline checks were completed for 3.0.0:

## Completed

- Native Kotlin sources compiled with Kotlin 2.0.21, Android Gradle Plugin 8.7.3 and JDK 17. The application uses native Android views and audio capture.
- `assembleDebug testDebugUnitTest` succeeded. Five Kotlin unit tests passed, zero failures/errors: leading silence/click rejection, speech/minimum duration and silence stop at 16/44.1/48 kHz sample counts, quiet countdown reset, full logarithmic sensitivity range, and bounded lossless long-note segmentation.
- Android lint passed after moving API27/API35 theme attributes into version-qualified resource folders. Remaining warnings concern English-only UI strings, optional backup configuration and minor view implementation suggestions; no lint errors.
- APK manifest verified with Android build tools: app.vnotes, 3.0.0, minimum SDK 26, target SDK 35, launcher VNotes.
- APK v2 signature verified by apksigner. Certificate is the standard-form local Android debug identity, not a production identity. Local key is preserved under ignored android/.signing/ for future compatible test updates.
- APK archive contains compiled DEX, manifest/resources, and Moonshine/JNI/ONNX Runtime .so files for ARM64, ARMv7 and x86-64.
- APK zip alignment verified. All three ARM64 native libraries have 16KB-aligned ELF LOAD segments.
- APK copied to releases/vnotes-android-debug.apk; SHA-256 in releases/SHA256SUMS.
- Native source under android/, build helper under scripts/, Android reconstruction and validation guides under docs/.

## Verification limits

No physical Android phone or Android emulator was used to install/run this APK. JVM signal tests, packaging/signature validation and static checks do not verify microphone hardware or actual native JNI inference on the user's device. This is an installable debug test build; do not describe it as device-validated or production-ready.

On the phone, verify:

1. Install/open, typed create/edit, TODO/DONE order and persistence after reopening.
2. Denied/granted microphone permission and Android's global microphone switch.
3. Settings sensitivity slider, native live meter and Stop/close/background cleanup.
4. English Tiny download completion/error/retry and recording/transcription online, then cold airplane-mode relaunch and a new transcription after download.
5. Silence auto-stop, quiet/noisy speech, manual stop, Cancel, 2-minute cap and app backgrounding. Confirm microphone privacy indicator clears.
6. Recorded replacement review/cancel/save, failed-audio retry across restart, cooperative transcription cancel.
7. Swipe/row menus/Undo/erase confirmations, file-picker backups and JSON backup import without duplicates.
8. Keyboard resizing, display/font sizes, TalkBack and orientation.

Model files are downloaded by the native SDK during setup and are not included in the APK. Transfer notes between installations using JSON backup export/import.
