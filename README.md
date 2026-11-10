# VNotes for Android

VNotes stores typed and spoken tasks locally. The Flutter interface uses Material Design 3. Download the English Moonshine Tiny model once to enable offline dictation.

Read the [engineering manual](docs/index.html) for the architecture, data contracts, recording lifecycle and build decisions. Edit the numbered noweb chapters under `src/`. File chunks expand into `code/`, the Flutter project. The same chapters weave into the HTML manual under `docs/`.

## Develop

Use Python 3.9+, noweb (`notangle` and `noweave`), Flutter 3.47.9, JDK 17 and an Android SDK with platform 36. From the repository root:

```sh
python3 scripts/literate.py
python3 scripts/literate.py --check
cd code
flutter pub get
flutter analyze
flutter test
flutter run
```

To package a test installation, run `flutter build apk --debug` from `code/`. Install `build/app/outputs/flutter-apk/app-debug.apk` with `adb install -r`. An in-place upgrade of an earlier VNotes debug installation requires its signing key. Place that key in `code/android/.signing/debug.keystore` before building. Exported JSON backups also transfer notes between installations.

The Gradle wrapper, Flutter project metadata and dependency lockfile are toolchain inputs. Keep them under version control alongside the tangled sources. Android SDK locations and signing keys belong to each developer's machine.
