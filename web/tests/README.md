# Regression checks

Run `node --test tests/*.test.js` with Node 22+ and no installed dependencies. VAD tests use actual frame counts at multiple sample rates. Worklet tests cover downmix, RMS, batching and flush. Worker-client tests cover correlation, transferable-copy safety, concurrency/cancel/error recovery. Cache tests cover failed-response rejection, shell fallback and full dependency manifests.

Open `/tests/browser.html` on the served app for real browser tests. Buttons load the actual Moonshine model, inject a known clip through a synthetic 48 kHz MediaStream into production capture, check energy auto-stop, transcribe known speech, exercise manual/cancel/duplicate stop and track release, and transcribe a repeated clip exceeding 20 seconds. These tests do not access the real microphone or create notes. Run them separately from the main app on low-memory hardware.

`speech.wav` is the JFK inaugural speech fixture served by the Transformers.js documentation dataset:
https://huggingface.co/datasets/Xenova/transformers.js-docs/resolve/main/jfk.wav
It was resampled to mono 16 kHz PCM for a smaller test asset. The fixture is developer-only and is not downloaded or precached by the normal application. Expected recognizable words include “ask” and “country”. Synthetic capture verifies WebAudio and inference logic, not Android microphone permission, acoustics or background execution.
