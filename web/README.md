# VNotes

A buildless vanilla JavaScript PWA for private English voice notes and a to-do list. Version 2.1: energy-based VAD, local browser runtime, detailed errors, and explicit long-note segmentation.

## Run and test on Android over USB

No npm installation, build, Android Studio or APK is needed. From this directory:

```sh
python3 -m http.server 5173 --bind 127.0.0.1
```

In a second terminal, connect an unlocked Android phone with USB debugging enabled and authorize your computer:

```sh
adb devices -l
adb reverse tcp:5173 tcp:5173
```

Open **http://localhost:5173** in normal Chrome on the phone. Keep this exact host/port: changing to 127.0.0.1 or a different port creates a separate storage origin. Plain HTTP on your computer's LAN IP cannot use the microphone. HTTPS hosting is the alternative to USB. Chrome's Install app / Add to Home screen menu installs the PWA.

## Updating the previous version

1. Keep the server running and the USB mapping active.
2. Open VNotes and wait a few seconds for the new service worker to install. Reload. If it still looks old, close all VNotes tabs/home-screen instances and reopen the same address.
3. In Settings verify **v2 energy VAD** appears. The updated service worker takes control automatically; future online reloads check for current source files.
4. Tap **Set up voice notes** once for this version. This uses local vendor files and downloads/caches the English Moonshine Tiny model. Keep the phone online and the app in the foreground until “Voice notes are ready offline.”
5. Existing notes, settings and failed-recording retry data are preserved. Do not clear site data to upgrade.

## Silence detection and errors

Microphone samples are batched into 512-frame blocks. The worklet calculates RMS loudness; at least 300 ms of above-threshold audio arms automatic silence stopping. The default is 2 seconds of silence; Settings also offers 700 ms, 1, 3 and 5 seconds.

- Quiet voice missed: **Settings → Microphone sensitivity** and move the slider right.
- Background noise prevents stopping: move the sensitivity slider left.
- Manual Stop also processes quiet speech below the VAD threshold. Cancel discards the capture.
- A live microphone meter and silence countdown show what the detector is doing. Settings also has **Test microphone**: adjust the slider while speaking and watching the level cross the threshold marker. Stop the test or close Settings to release the microphone. No audio is saved or transcribed. The logarithmic slider spans RMS thresholds 0.1 to 0.00001, up to 600 times more sensitive than the old High preset.
- Errors remain above the composer until dismissed. **Settings → Diagnostic details** includes engine errors, sample rate, peak RMS, speech duration and stop reason. It contains no note text or raw audio.
- Transcription can be cancelled. Retry audio remains in IndexedDB until successful processing or explicit Discard.

## Verify it

The developer page **http://localhost:5173/tests/browser.html** tests capture with a known speech fixture instead of the real microphone, then runs real Moonshine inference. It can test model loading, silence auto-stop, manual stop/cancel/track cleanup, repeated inference, and long-note segmentation. It does not create or modify notes. Run it separately from the main app to avoid loading two engines into memory on a low-end phone.

Optional automated tests (Node 22+; no npm packages):

```sh
node --test tests/*.test.js
```

For a real offline test: complete setup, enable airplane mode, turn Wi-Fi off, unplug USB, close and reopen VNotes, then record a fresh note. A desktop fixture test does not replace this physical-device check.

## Privacy, backup and customization

Notes and settings live in IndexedDB on this browser origin. Export/import JSON backups in Settings. Import adds missing IDs and preserves existing notes. No accounts, analytics, cloud transcription, or synchronization. Audio is processed locally and discarded after success; one failed recording is kept for retry.

The pinned runtime and WASM files are included under `vendor/`; model setup contacts Hugging Face for weights/tokenizer/configuration, receiving request metadata but never audio or notes. Browser storage can be evicted, so export backups regularly. Source includes approximately 22 MB of runtime files and a small developer audio fixture. Do not remove vendor files when copying the app to the server.

Edit CSS custom properties at the top of `style.css` for palette, typography and corner radius. See [docs/REBUILD-GUIDE.md](../docs/REBUILD-GUIDE.md) for a complete reconstruction specification, and [docs/VALIDATION.md](../docs/VALIDATION.md) for observed verification and remaining device checks.
