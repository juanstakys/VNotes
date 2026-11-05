# VNotes PWA — reconstruction specification

This is the preserved PWA specification; all application paths below are relative to `web/`. See ANDROID-REBUILD-GUIDE.md for the native Kotlin port.

Version 2.1, 2026-11-04. This document supersedes the first implementation guide. It is a standalone recipe for rebuilding VNotes without an LLM. Maintain it when code or behavior changes.

## Product decisions

VNotes is an installable Android PWA that turns typed or spoken English thoughts into plain-text tasks. There are no checkboxes: a task is active or deleted, and completing it means deleting it. No account, backend, analytics or synchronization. All transcription is local. An initial connected app/model download is acceptable; subsequent use must work offline.

Two tabs, List and Deleted. Active notes sort newest creation first. Edits preserve position; restoration puts the note at the top by updating its creation timestamp. Deleted sorts most recently deleted first; there is no expiry. Note text allows internal newlines and up to 20,000 characters. Empty/whitespace input is not a note.

A fixed bottom composer contains a multiline input and one circular button. Empty input: microphone. Populated input: plus. Recording: stop. Enter inserts a newline; Ctrl/Cmd+Enter submits. Clear typed input only after a successful durable save. Show elapsed recording time, live audio-level meter, silence countdown and a separate Cancel control. Disable typing during microphone capture, allow typing during inference. Only one capture/inference/setup request may run at a time. Typed notes are usable before voice setup and after voice failures.

Active swipe right soft-deletes, swipe left opens Type/Record edit choice. Type pre-fills old text. Record captures a proposed replacement, then opens an editable Save/Record again/Cancel review. Original remains unchanged until Save. Deleted swipe left restores, swipe right permanently erases. Confirm single and erase-all permanent deletion. Deleted replaces the composer with a round erase-all button, disabled when empty. Show Undo for reversible delete/restore. Ellipsis menus duplicate all gestures.

Default silence timeout is 2 seconds; choices are 0.7, 1, 2, 3, 5. A minimum 300 ms of above-threshold sound must occur before auto-stop is armed. Silence before speech must not stop the recording. A 120-second cap bounds memory. Manual stop remains available and accepts quiet speech below the auto-stop threshold; pure silence produces no note. Stop on app hiding/track interruption and process what is captured when possible; do not promise background capture on Android. A failed transcription retains one retry buffer, even across reload. Success or explicit Discard removes it.

Settings: silence timeout, microphone sensitivity, voice setup, diagnostic details, JSON backup export/import, installation guidance. Sensitivity is a native range slider, 0–100 in steps of 0.1. Map position p to RMS threshold .1 * 10^(-p/25), clamped to [.00001,.1]. Moving right increases sensitivity by lowering the threshold; default .01 maps to position 25. Preserve all valid stored numeric thresholds, including old presets. Display threshold and .01/threshold as sensitivity relative to normal. Energy VAD cannot distinguish a steady loud noise from speech; do not describe it as a semantic speech classifier.

## Design contract (simplified UI)

The user's reference sketch supersedes the original promotional design. Use a white screen and mint-green active controls, two equal-width sticky top tabs labeled TODO and DONE, and plain full-width note rows separated by thin lines. Do not show a wordmark, title, introduction, descriptions, empty-state copy, note ordinals, timestamps, counts, or gesture captions. Keep unobtrusive ellipsis action buttons for keyboard/accessibility. The content column is at most 640px wide, filling narrow screens. Preserve CSS tokens for easy theme changes.

Fix the bottom controls above the safe area: a circular settings icon on the left, a bordered rounded input in the center, and the circular record/send button on the right. When recording, hide the textarea and show the live microphone level bar and elapsed time in the same input slot. Keep Stop on the right and a small accessible Cancel icon directly above it. Recording details remain available to screen readers; processing/error/retry text appears only as needed. The DONE tab keeps settings on the left and erase-all on the right. A model download prompt is shown only until setup completes. Settings and functional pop-ups retain explanatory text needed for use.

Use the existing semantic tab/dialog/status/slider controls, focus outlines, pointer gestures, and reduced-motion support. Do not change note data or audio processing as part of visual simplification. Shell cache version v6 delivers this layout while preserving the voice cache.

## Module boundaries and dependencies

The application is buildless browser ES modules and vanilla JavaScript. Python's standard HTTP server is sufficient locally. Use stable HTTPS hosting or http://localhost for secure APIs. Relative URLs permit subdirectory hosting. No npm dependency install is required.

- index.html: semantic shell, tabs, setup card, composer, meter, persistent error panel, retry controls, dialog, toast, update banner.
- style.css: tokens, responsive layout, interaction and error states.
- app.js: UI rendering, gestures, note actions, dialogs, settings/backups, capture lifecycle flags, diagnostics, error presentation.
- db.js: IndexedDB wrappers, atomic transcript-save transaction.
- vad.js: pure sample-duration-based energy VAD.
- sensitivity.js: logarithmic slider conversion, valid persisted threshold range, and log-scaled meter positions.
- capture-worklet.js: downmix and 512-frame RMS batching on the audio thread.
- audio.js: microphone/context lifecycle, graph cleanup, stop/flush, rate conversion.
- speech-utils.js: pure long-note segmentation and useful error formatting.
- voice-client.js: one module worker, request IDs, timeout/cancel/error handling and transferable copies.
- stt-worker.js: lazy local runtime import, single-thread WASM setup, model warmup, sequential inference.
- offline.js: registration/controller handshake and full voice-cache manifest validation.
- sw.js: shell/model caching, automatic update activation, offline navigation fallback.
- vendor/: the three pinned generated runtime files and licenses, with SHA256SUMS.
- manifest.webmanifest and SVG/192px/512px PNG icons: installability.
- tests/: Node regression contracts and a browser audio/inference harness with a speech fixture.

## Persistent data and migration

Keep IndexedDB database `vnotes`, schema version 1. Preserve these stores during upgrades:

1. notes, out-of-line UUID key; value `{id, text, createdAt, updatedAt, deletedAt}`. Millisecond epoch timestamps. deletedAt=null for active, positive timestamp for deleted.
2. settings, string keys: silence (number), threshold (number), voiceManifestV2 (array of cached absolute request URLs). Old voiceReady may exist but is not trusted for this version.
3. audio, key pending; value `{audio: Float32Array, target: UUID|null}`. Mono 16 kHz PCM; target null means new note, otherwise replace the identified note after review.

Add creates crypto.randomUUID and current timestamps. Edit updates text/updatedAt, retaining order. Soft-delete sets deletedAt; restore clears it and sets createdAt=now. Undo writes the previous snapshot. Permanent deletion deletes the record. Erase-all and import are single-store atomic write transactions. New transcribed note insertion and pending-audio deletion must be one transaction over BOTH notes and audio, so a failed cleanup cannot create duplicates on Retry. Resolve storage writes on transaction completion, not request success.

Do not rename the database or clear storage to fix installation problems. Old notes and pending PCM are compatible. New voice setup uses a new cache namespace and requires one setup; no model cache migration is assumed. A changed origin (scheme/host/port) has different storage.

Backup: `{format:"vnotes",version:1,exportedAt:ISO,notes:[...],settings:{silence,threshold}}`. Import validates <=10 MB, <=50,000 notes, unique/nonempty string IDs, nonblank strings <=20,000 characters, positive finite date-range timestamps, and deletedAt null or valid timestamp. Whitelist note fields, reject malformed backups wholesale, merge only absent IDs, keep existing notes and settings. Show import count before committing. Export a UTF-8 JSON Blob using an anchor download and revoke its object URL.

## Energy-based VAD implementation

AudioWorklet.process receives variable-sized rendering quanta, commonly 128 frames. Average channels into mono, accumulate exactly 512 frames, calculate RMS = sqrt(sum(sample*sample)/count), then post `{type:"chunk", buffer:Float32Array, level:RMS}` with the buffer transferred. Use a fresh copy for each posted chunk so later writes cannot overwrite previous audio. No model, timer or network work runs in the audio thread. On a flush message, emit any partial chunk, send a flushed acknowledgment, and stop processing.

EnergyVAD stores actual sampleRate, silenceMs, threshold, speechMs, quietMs, totalMs, peak, level. For each block, ms = sampleCount*1000/actualSampleRate. Above-threshold blocks add ms to speechMs and reset quietMs; below-threshold blocks add ms to quietMs. Auto-stop iff speechMs>=300 AND quietMs>=silenceMs. Speech time is cumulative for the take. Derive silence timing from samples rather than performance.now callbacks, so delayed/coalesced messages do not invent silence. The UI timer (100ms) only displays progress and enforces the two-minute/blocked-audio watchdog limits.

At 16 kHz, a 512-frame block is 32 ms; at 48 kHz it is 10.67 ms. Never assume block duration is 32 ms unless the actual context rate is 16 kHz. Repeated voiced blocks restart the silence interval. A cough/click shorter than the minimum does not alone arm auto-stop. Leading silence is allowed until the recording limit.

## Microphone lifecycle

State machine: Idle -> Starting -> Recording -> Stopping -> Processing -> Idle or RetryPending. Cancel in Recording/Stopping returns to Idle without transcript. Cancel processing terminates the worker and retains pending audio. Guard all overlapping stop calls with a single shared promise; set the stopping state synchronously before awaits.

Inside the originating user tap, create and resume AudioContext({sampleRate:16000,latencyHint:"interactive"}); if the constructor rejects that rate, use the default. Resume BEFORE awaiting getUserMedia to retain browser gesture activation. Request mono, 16 kHz, echo cancellation, noise suppression, auto gain, but trust context.sampleRate rather than constraints. Check secure context and API availability and give actionable errors.

Graph: MediaStreamSource -> AudioWorkletNode -> zero-gain GainNode -> destination. Connecting to a muted destination keeps rendering alive without feedback. On Stop, send flush to worklet, await ack or a 250ms suspended-context timeout, clear timers, detach handlers, disconnect nodes, stop every microphone track and close context. No-samples is an error. An 8-second gap in arriving blocks cancels and reports an interruption. Stop on visibilitychange(hidden) or track ended. Manual Stop with >=150 ms captured and peak RMS>.0005 permits quiet speech even if it never armed automatic stopping. Fully silent captures are discarded. Cancel releases tracks and emits no onStop audio.

Concatenate chunks. If actual rate is 16 kHz, pass through directly. Otherwise resample through OfflineAudioContext(1,ceil(length*16000/actualRate),16000), using an AudioBuffer at the actual original rate. Store the resulting 16 kHz Float32Array before inference. The capture API's injectable getStream defaults to getUserMedia; the browser regression harness supplies a synthetic MediaStream for repeatable testing without microphone hardware.

## Moonshine engine and long notes

Follow the shared chat's three-thread architecture: main UI, AudioWorklet batching, module worker inference. Use Moonshine Tiny English, WASM CPU, one thread, no WebGPU or Silero requirement. Runtime is pinned to Transformers.js 3.7.1, matching the shared example. Download these exact generated distribution files when reconstructing:

- https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.7.1/dist/transformers.min.js
- https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.7.1/dist/ort-wasm-simd-threaded.jsep.mjs
- https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.7.1/dist/ort-wasm-simd-threaded.jsep.wasm

Put them under vendor/ and include Apache 2.0 Transformers / MIT ONNX Runtime 1.21.0 licenses. Record SHA-256 digests, replace the files together on upgrade. No dependency manager is needed at runtime. Generated web.js builds may have bare imports; use the self-contained min.js build. Vendor bytes are served locally, not imported from a CDN during use.

Worker imports ./vendor/transformers.min.js. Set env.allowLocalModels=false, env.useBrowserCache=false, wasm.numThreads=1, wasm.proxy=false, wasm.wasmPaths to the absolute local vendor/ URL. The service worker owns all model/runtime caching. Create pipeline('automatic-speech-recognition','onnx-community/moonshine-tiny-ONNX',{revision:'a6da124',device:'wasm',dtype:{encoder_model:'fp32',decoder_model_merged:'q8'},progress_callback}). Warm up with 16,000 zeros and max_new_tokens:16 before declaring readiness.

IMPORTANT: Moonshine's Transformers.js 3.7 path does not implement Whisper's chunk_length_s/stride_length_s pipeline logic. Split recordings explicitly into at most 20-second slices. While >20 seconds remain, search 250ms RMS windows between 12 and 20 seconds from the current offset; choose the quietest window's midpoint as the cut (latest minimum on ties). Each sample belongs to exactly one slice: no loss, no overlap, no duplicated transcript seam. This reduces mid-word splits but does not guarantee they never occur. Transcribe slices sequentially with max_new_tokens=max(16,min(256,ceil(durationSeconds*8))), trim and join nonempty texts with spaces. Tokens are explicitly bounded even for subsecond clips, avoiding zero tokens from a floor(duration) default. Reject >121 seconds or invalid PCM inputs. Empty final text is a retryable failure, not an empty note.

Serialize all worker requests through one promise chain. Messages contain request id and type load/transcribe. Replies: status/progress/ready/result/error, retaining the id. Send error names, messages and stack details; numbers (WASM error codes) must remain visible. The client rejects concurrent requests, correlates IDs, times out at five minutes, terminates after worker errors/cancel, and creates a fresh worker on Retry. Transfer a COPY of PCM to preserve the retry buffer. Do not use browser SpeechRecognition; it may depend on a cloud service.

## Offline and update behavior

Cache names are vnotes-shell-v6 and vnotes-voice-v2. Install precaches HTML/CSS/local application modules/icons/manifest with cache:reload, then skipWaiting. Activate claims clients. Preserve old caches and IndexedDB; no user data deletion is part of an update. Online app-shell requests use network-first with cached fallback, addressing the first version's stale-shell problem. Model requests to huggingface.co and same-origin vendor/ files use cache-first. Cache successful CORS-readable responses only. Cache original model URLs even when fetch follows a signed asset redirect. Cache-write failure must be visible during voice setup. Offline navigation falls back to the cached root.

Register service worker using a URL relative to offline.js's import.meta.url, not the current page (developer test page lives under tests/). Await registration ready and an actual controller, with finite timeouts and retry on failure. Only then initialize the voice worker. After warmup, enumerate the voice cache, verify runtime/WASM/encoder/decoder/tokenizer entries, and save the COMPLETE URL list in settings.voiceManifestV2. On restart, confirm every URL in that manifest still exists before showing offline-ready. This replaces the old three-filename heuristic. A corrupt model still needs retry/setup diagnostics; cache presence is not a checksum validation of model weights.

Show an update banner on controller changes for an already controlled page. Reload only after draft/capture/inference is finished; preserve notes. The old version will not have this banner: users should wait for registration, reload, or close all VNotes windows and reopen. Verify v2 energy VAD in Settings. First v2 voice setup downloads to its own cache. No HTTPS certificate is needed for the localhost + USB ADB development route; regular hosting must be HTTPS. No COOP/COEP headers are required with single-thread WASM.

## Gestures, diagnostics and testing

Pointer Events with touch-action:pan-y. After 10px movement, select horizontal iff abs(dx)>1.3*abs(dy); only then capture pointer. Translate at most +/-140px, activate at +/-85px on release, reset on pointercancel. Ignore button-origin gestures. Tab roles/aria-selected/tabpanel/roving tabindex and arrow navigation provide keyboard access; dialogs/menus duplicate touch actions.

Errors remain above composer until dismissed. Settings diagnostic details includes build version, secure-context state, browser UA, connection indication, controller URL, configured RMS/silence, capture sampleRate/duration/peak/speechMs/reason, and the last 30 stage/error/stack entries. Do not log notes or audio. Common recovery should mention localhost/HTTPS, microphone permission, noisy/quiet sensitivity, app foreground, memory pressure, reconnecting for setup, or retained Retry audio as applicable. A generic disappearing toast alone is insufficient.

Reconstruction order: semantic shell and tokens; IndexedDB typed CRUD; Deleted/restore/confirm/Undo; edit dialogs; pure VAD/worklet with tests; capture cleanup/resampling; worker and vendor dependencies; offline caching; backup validation; Android install and regression tests. Maintain validation evidence separately from desired behavior.

Node tests need no installed packages: node --test tests/*.test.js. Test minimum speech, leading silence, continuous speech/quiet reset, thresholds, actual 16/44.1/48 kHz durations, worklet RMS/batching/partial flush/input immutability, lossless bounded chunk splitting, cache contracts and complete shell files. Browser harness feeds a known public JFK clip into a 48 kHz synthetic stream, runs actual production capture at 16 kHz, checks auto-stop and recognized words, and also tests duplicate stop/cancel cleanup and long-note inference. Browser test controls must not mutate notes or require the real microphone. Check real device capture, noisy rooms, permissions, USB/airplane-mode cold relaunch, Android keyboard/swipes, file backups, and TalkBack before release.

## Reference and change rationale

User-provided inspiration: https://assistant.kagi.com/share/72ef548d-6ac0-4d69-b914-5ff9fc7c6296 — Running Local Speech-to-Text Models, specifically its vanilla JS module-worker/audio-worklet and RMS auto-stop example. Adopt its architecture, 512-frame batches, 300ms minimum speech, .01 RMS default, manual stop and tunability. Keep the user's previously agreed 2-second silence default, with the example's 700ms exposed as an option. Deliberately use WASM only for predictable low-end Android compatibility rather than choosing GPU by API presence. No Silero/model-based VAD.

Original example source: https://github.com/huggingface/transformers.js-examples/tree/main/moonshine-web
Model: https://huggingface.co/onnx-community/moonshine-tiny-ONNX (MIT)
Transformers.js configuration: https://huggingface.co/docs/transformers.js/en/custom_usage_settings

## Settings microphone test (2.1)

Use a separate level-only monitor exported by audio.js. It uses the same processed mono microphone constraints, 16 kHz-requested AudioContext and capture-worklet RMS calculations as real recordings. Connect to a zero-gain destination. Read levels without keeping any PCM blocks, writing storage, loading the model, or transcribing. Show a log-scaled bar: position=clamp(20*log10(max(.00001,RMS))+100,0,100), and a vertical threshold marker on the same scale. Text identifies above-threshold sound versus quiet and prints raw RMS. Change detection immediately on slider input, persist numeric RMS on change, and validate the full supported range on startup.

Test microphone is an explicit button that becomes Stop test. Disable it while requesting permission. Stop/disconnect/release tracks/close context on Stop, dialog dismissal (Done/Escape), replacing the dialog content, page hiding, track interruption or initialization error. A generation counter rejects late permission results when Settings has closed: immediately stop the resulting monitor rather than retaining an orphan microphone. Do not let a test run alongside an actual note capture. The test is available without the speech model installed. Energy detection is a level threshold, so label above-threshold sound rather than claiming it is known speech.
