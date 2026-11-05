# Validation — version 2 rebuild, 2026-11-04

## Observed passes

- Syntax checks for application modules, audio worklet, speech worker and browser harness with Node 22.21.1.
- 13 automated checks: 7 VAD/segmentation/error-formatting tests, 4 shell/worklet/cache tests, 2 worker-client/offline-readiness tests. VAD cases cover leading silence, 300ms minimum sound, 700ms auto-stop at 16/44.1/48 kHz, quiet countdown reset and sensitivity. Worklet covers stereo downmix/RMS/512-frame transfer/partial flush. Client covers copied transfer, request IDs, concurrency rejection, cancellation and detailed errors. Cache readiness checks every recorded dependency URL.
- Browser real Moonshine setup through the locally vendored Transformers.js 3.7.1 + WASM runtime: warmup succeeded and 10 dependencies were present in the voice cache.
- Browser synthetic microphone test: public JFK speech fixture fed through a 48 kHz source stream into production capture, actual capture context 16 kHz, energy-based silence stop at 700ms, resulting PCM transcribed correctly. Stop reason was silence; first capture had ~11.75 seconds of audio and >10 seconds above threshold.
- Browser ~35-second repeated fixture: three sequential segments transcribed successfully, preserving speech across all parts.
- Browser second request: short excerpt correctly transcribed by the same worker.
- Browser manual Stop produced audio, Cancel produced none, concurrent/duplicate Stop calls were idempotent, and stream tracks were released in both cases.
- Updated app retained an existing note from the prior version; Settings displayed v2 energy VAD with 700ms/default-two-second options and sensitivity choices.
- Browser migration from the old cache-first service worker to v4 succeeded after reloading. The old IndexedDB note survived; no site data was cleared.

## Verification boundaries

Synthetic microphone tests exercise real WebAudio, production capture, worklet/VAD, PCM handling and real model inference; they do not test Android microphone hardware or permission prompts. No physical phone is controlled during this rebuild.

Still verify on Android: microphone gain/noise behavior, actual default context fallback/resampling, permissions, background/lock interruptions, screen keyboard, touch swipe thresholds, installability, TalkBack, backup file pickers, and a cold offline relaunch/transcription with USB unplugged and Wi-Fi/mobile data disabled. Offline cache logic is tested and warmup dependencies were present, but the full physical airplane-mode scenario remains unverified.

No backend or production deployment was introduced. The existing local server was reused for browser testing. README.md includes an upgrade path that preserves notes and requires fresh v2 voice setup rather than clearing browser storage.

## Version 2.1 sensitivity slider

- Added two passing regression cases for four-decade logarithmic slider mapping, persistence validation and detection at RMS .00002 with maximum sensitivity. Added a passing level-monitor lifecycle test verifying RMS forwarding, track/context cleanup and worklet-setup failure cleanup. Existing regression files continue to pass.
- In-app browser Settings displayed the slider, threshold marker and test controls. Setting maximum showed 1,000× normal / .00001 RMS. The real microphone test delivered live RMS levels and showed above-threshold detection. Moving the slider to minimum changed the feedback to below-threshold quiet while the test continued. Stop test returned to the idle state. Normal sensitivity was restored after testing.
- Full Android-specific acoustics/permission behavior still requires phone verification. The monitor keeps no PCM and invokes no model.

## Minimal design revision

Replaced promotional heading/wordmark, descriptions, counts, numbered cards and timestamps with TODO/DONE tabs and thin-separated note rows. Settings moved to bottom left; composer and shared action stay center/right. Recording state swaps the input for the existing level meter and timer, with a cancel icon above the stop button. Updated reconstruction design specification. Browser visually verified TODO and empty DONE layouts with existing notes preserved; regression suite passes. Actual recording-state visual verification was limited by a pending microphone request; microphone troubleshooting remains deferred at the user's request.
