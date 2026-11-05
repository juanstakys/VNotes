# VNotes native Android reconstruction guide

Version 3.0.0. This guide describes the Kotlin port independently of the PWA guide. A programmer can rebuild the native application without an LLM. The original PWA lives under `web/`, with its original code preserved; native Android is the primary project under `android/`.

## Product and visual contract

Keep the approved minimalist sketch: white background, two equal top tabs TODO/DONE, mint selected tab, plain note rows with separators, no title/description/count/timestamp. Settings is a circular icon at the bottom left. Center is the rounded text input; right is the shared microphone/plus/stop icon. While recording, replace the input with an energy level meter and elapsed time. Show a separate Cancel only when recording/processing. DONE replaces the composer with a permanent erase-all control. Keep functional dialogs, model setup, errors and retry messages; do not add promotional copy.

Active notes sort newest first. Type-add trims outside whitespace and preserves internal newlines; text limit 20,000 characters. Editing preserves list position. Completing a note moves it to DONE. DONE is soft deletion, sorted by latest completion. Restore sets creation time to now so it appears at top. Swipe right active -> DONE; left -> choose Type or Record. Swipe right done -> permanent erase confirmation; left -> restore. Row click/ellipsis offers equivalent actions for accessibility. Permanent erase-all confirms. A short Undo dialog follows reversible moves. Canceling edit/record does not alter the original. Recorded replacement opens editable review with Save, Record again and Cancel.

English recognition, no accounts/analytics/sync/cloud inference. Initial model download is allowed, then app works offline. Notes are private to the app. Export/import backups use Android's system document picker. Export the PWA JSON and import it into Android to transfer data; browser storage cannot be read directly by the native application.

## Build and packaging contract

Use Kotlin and native Android views, not WebView, HTML, JavaScript, Compose or a browser-dependent capture API. Platform Activity + programmatic LinearLayout/ScrollView/EditText/AlertDialog minimize UI dependencies. Native speech SDK brings its own transitive AndroidX/OkHttp dependencies.

Pinned tools: JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target SDK 35, min SDK 26 (Android 8.0). Dependency: `ai.moonshine:moonshine-voice:0.1.5`. Repository sources: Google Maven, Maven Central, Gradle plugin portal. Use the Gradle wrapper under android/. The build helper generates a standard debug signing key once in ignored android/.signing/debug.keystore, builds/tests, copies the APK into releases/ and records SHA-256. Keep this key for compatible future debug upgrades; production needs a separate private release key. Debug credentials are the public Android standard and provide no release identity.

The universal test APK includes arm64-v8a, armeabi-v7a, x86_64 native libraries. Legacy native packaging extracts compressed .so files at install; no model weights are bundled. Files under releases/ are deliverables, while local.properties, .gradle, build outputs, IDE state and signing keys are ignored. Debug app id is app.vnotes, version code 1, version name 3.0.0. Android manifest declares microphone and internet access. No storage permission is needed for private files or document pickers. Do not permit cleartext network requests. Android backups are disabled so the model/audio is not automatically uploaded by the app backup mechanism; users export explicit JSON backups.

Theme is a light native no-action-bar theme, white system bars on API27+ and green accent (API26 retains a dark navigation bar for visible system buttons). Keep newer theme attributes in values-v27 and values-v35 resource folders, with common attributes in the base style. Set adjustResize for keyboard. On API 35, this build opts out of enforced edge-to-edge to keep system bars/keyboard from covering controls. Handle orientation/screen/keyboard configuration changes in the Activity, and stop capture when leaving foreground. Font/icon drawing and min 44dp controls should remain accessible. Keep theme constants in MainActivity's small color palette and styles.xml.

## Modules

- MainActivity.kt: native UI construction/state, gestures, dialogs, permission flow, settings, capture coordination, retries, executor orchestration, SAF backup import/export.
- NoteStore.kt: SQLiteOpenHelper and Note model, versioned JSON parser/merge, PendingAudio/RetryStore atomic file storage.
- EnergyVad.kt: sample-duration energy VAD, logarithmic sensitivity and meter mapping, quiet-boundary long-audio segmentation. No Android imports; unit-testable on JVM.
- AudioCapture.kt: native mono PCM AudioRecord capture, RMS, test mode, stop/release lifecycle, two-minute bound.
- SpeechEngine.kt: native Moonshine setup/presence/loading/segmented inference, used exclusively from one executor.
- res/drawable/ic_launcher.xml: vector launcher monogram. res/values/styles.xml: native light theme.
- src/test/.../EnergyVadTest.kt: pure signal/duration/segmentation regressions.

The app does not require an HTTP server, adb reverse, Chrome, Firefox or WASM. ADB is only an optional installation/logging/testing tool.

## Native audio and energy VAD

Use AudioRecord with AudioSource.VOICE_RECOGNITION, mono CHANNEL_IN_MONO, PCM_16BIT, 16,000 Hz. Ask for RECORD_AUDIO at runtime before creating the recorder. Check getMinBufferSize returns a positive value, allocate at least max(minimum*2,4096) bytes, verify STATE_INITIALIZED, startRecording and verify RECORDSTATE_RECORDING. Report initialization/read errors instead of returning empty notes.

Read on a dedicated thread into 512-short blocks. Convert each short to normalized float with sample/32768f. RMS=sqrt(sum(value²)/count). Each actual frame count contributes count*1000/16000 milliseconds. Keep cumulative speechMs (RMS>=threshold) and current continuous quietMs. Above threshold resets quietMs, below adds to it. Require at least 300ms of above-threshold audio before arming automatic stop. Auto-stop when armed and quietMs>=silenceSeconds*1000. Leading silence and a brief click alone do not trigger stop. Default silence is 2 seconds; settings choices .7,1,2,3,5 seconds.

Sensitivity slider has 1001 positions (0..1000). RMS threshold = clamp(.1*10^(-position/250),.00001,.1), so rightward is more sensitive. Default .01 corresponds to position250. Store the numeric threshold, not the slider position. Meter position = clamp((20*log10(max(.00001,RMS))+100)/100,0,1). Settings displays the threshold marker on the same log scale, raw RMS, and above/below text. The test uses exactly the same capture source/constraints but never allocates a take buffer, saves PCM or invokes inference. Changes apply immediately to its comparison feedback and to the next recording.

A normal capture allocates at most 120*16000 floats (~7.7 MB). Stop at two minutes, on manual action, on Cancel, or when the Activity leaves foreground. Atomic flags coordinate the UI and read thread. Stop unblocks a blocking read; release the recorder in a finally block. Ignore the expected read failure after a requested stop. Cancel emits no audio. Manual stop allows quiet speech with peak>.0005 and >=150ms captured even if the automatic threshold never armed. Fully silent audio is discarded. The capture callback persists usable PCM before posting a UI callback, so finishing an Activity cannot discard an already completed take solely because UI delivery was skipped.

No AudioTrack, microphone loopback, native speech recognition intent or cloud API is involved. The microphone settings test must stop on dialog dismissal/app background. On interruption or permission rejection show an actionable error. If the device cannot initialize 16kHz capture, this version reports it; it does not silently fall back to a mislabelled sample rate.

## Moonshine native setup and inference

Use the official native Android SDK rather than porting Transformers.js. Native models are ORT-format assets and tokenizer data, distinct from the web ONNX model cache. Construct `ModelSpec.stt("en", JNI.MOONSHINE_MODEL_ARCH_TINY, false)` to choose the original small English Tiny architecture explicitly and omit spelling/diarization extras. Model root: filesDir/models/moonshine-tiny-en.

Call AssetDownloader.isModelPresent(root,spec) on the speech executor during initial startup. This resolves the compiled native file catalog and checks local files. Initial setup calls ensureModelPresent(root,spec,progressListener), whose HTTPS transfers use .part files, support HTTP Range resume, check space, and rename completed downloads. Display file progress on the main thread. Then `Transcriber().loadFromFiles(root.absolutePath, JNI.MOONSHINE_MODEL_ARCH_TINY)`. Declare setup successful only after loading succeeds. Reuse the opened engine for later captures; keep all its methods on a single executor to avoid concurrent native inference. Close it when Activity shuts down after queued work.

Feed float PCM into transcribeWithoutStreaming(segment,16000), then use transcript.text().trim(). Split takes exceeding 20 seconds explicitly: search 250ms windows from 12..20 seconds and cut at the midpoint of the quietest window (latest on ties). Every sample belongs to exactly one segment; no overlap or loss. Process segments sequentially and join nonempty texts with spaces. Quiet-boundary splitting reduces but cannot eliminate mid-word cuts. Reject empty final output as a retryable failure.

Native cancellation is cooperative: mark the result unwanted, show Cancelling, let the running transcription operation (including its segments) return, ignore its text, keep retry audio, and re-enable controls. Do not close/free JNI objects from a second thread while inference runs. Typed entry remains usable while inference/model setup runs. New microphone recording is blocked until the speech operation completes.

## Durable storage and backups

SQLite database vnotes.db, schema version1:
`notes(id TEXT PRIMARY KEY, text TEXT NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL, deleted INTEGER)`.
Null deleted means active. UUID ids, epoch millisecond timestamps. Preserve existing text/settings during updates. Use database transactions for bulk import. Import only missing IDs; do not overwrite matching local IDs or current settings.

SharedPreferences file settings contains float threshold and silence. Clamp threshold into [.00001,.1] on load. Persist changes immediately. No speech or note text goes into logs/analytics.

RetryStore uses Android AtomicFile at filesDir/pending-audio.bin. Format: big-endian int magic0x564E4131; Java DataOutput UTF note UUID; UTF edit target UUID or empty; int sample count; sampleCount float32 values in big-endian. Valid size 1..1920000. The note UUID is generated when creating the pending take and remains stable across retries; insert/replace by that same ID prevents duplicate notes if cleanup is interrupted after save. For an edit, target identifies the existing note and the transcript is reviewed before changing it. Successful transcription/review handoff clears the retry file; unsuccessful/cancelled inference retains it. Discard deletes it explicitly. Never create an empty note or lose typed draft before successful SQLite write.

Backup JSON retains the PWA format: format="vnotes", version=1, notes=[{id,text,createdAt,updatedAt,deletedAt}], settings={silence,threshold}. Export via ACTION_CREATE_DOCUMENT. Import via ACTION_OPEN_DOCUMENT, read at most ~10MB into memory with an explicit bounded loop compatible with API26. Validate count<=50000, unique nonblank IDs, nonblank text<=20000, finite positive date-range timestamps, null deletedAt or valid timestamp. Preview the count of missing IDs before import. Browser data does not migrate implicitly. Export files are user-owned outside app-private storage and survive uninstall.

## Rebuild sequence and acceptance

1. Create Gradle project and manifest/theme/vector icon with pinned versions above.
2. Implement pure VAD/slider/segmentation and JVM tests.
3. Add SQLite and retry atomic file; implement typed TODO/DONE actions and backups.
4. Construct minimal platform-view Activity and dialogs; wire gestures and accessible row/action alternatives.
5. Add runtime permission, AudioRecord capture/test and deterministic cleanup.
6. Add native Moonshine downloader/load/offline inference on a single executor.
7. Build debug APK, run tests, verify signing/manifest/ABIs, record checksum and install on a real phone.
8. Verify cold airplane-mode relaunch/transcription after setup, Android permissions denied/granted, soft/noisy speech, manual stop/silence/cancel/two-minute/background behavior, recorded-edit review, swipe/menus, backups/PWA migration, process restart with retry audio, keyboard layout, TalkBack and large text.

Compilation/JVM tests do not verify phone microphone hardware or actual Android JNI inference. Record observed checks and limits in ANDROID-VALIDATION.md; never imply device validation from an APK build alone.

Official API references: https://github.com/moonshine-ai/moonshine/tree/main/language-bindings/android and https://github.com/moonshine-ai/moonshine/tree/main/examples/android/Transcriber . Pin release APIs using Maven's 0.1.5 sources JAR when reconstructing; main may change. SDK is MIT; transitive ONNX Runtime and other licenses remain in upstream distributions.
