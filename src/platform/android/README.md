# mGBA Android

The port is split into reusable `:core`, UI-free `:logic`, and product `:app`
modules. See [ARCHITECTURE.md](ARCHITECTURE.md) for the dependency rules and the
standalone core AAR contract.

This directory contains the first Android frontend for the mGBA core in this
repository. Its GBA runtime path includes Storage Access Framework imports,
app-private ROM/save/state storage, mGBA core execution, GLES 3.0 video,
AudioTrack output, touch/controller input, and Android lifecycle handling.

## Architecture

- `RomRepository` imports one or more selected ROMs into app-private
  storage. `GameLibraryDatabase` indexes each content-hash-based ROM identity,
  display name, favorite state, play history, save data, and cached cover.
- `CoverRepository` resolves the original ROM filename against the Libretro GBA
  `Named_Boxarts` collection, validates each downloaded image, and atomically
  stores it in app-private storage. Game details also accept a validated custom
  image, while Refresh cover art returns to the Libretro source. A missing
  network or cover never blocks play.
- `EmulationSession` is the single-threaded owner of `EmulatorCore`; every core,
  input, reset, frame, and audio call is serialized on that thread.
- `MgbaNativeCore` is the Kotlin/JNI adapter. `mgba_android.c` embeds the existing
  C core as a static library and exposes only the small contract the app needs.
- `EmulatorViewModel` connects lifecycle/state, video, and `AudioTrack` without
  exposing Android UI objects to the core layer.
- `SettingsRepository` persists the Android/GBA subset of the Qt frontend
  configuration and applies hot settings or reload-required settings through
  `EmulationSession`.
- `MgbaApp` is the Compose application layer. It provides a responsive home,
  searchable game library, game management dialogs, and player navigation.
  `FrameView` is a GLES 3.0 renderer
  with aspect/integer scaling, filtering, frame blending, and the 28 shader bundles
  shipped in the repository. Multi-pass FBOs and manifest uniforms are supported.

## Configuration

The Compose settings screen covers the Qt options that apply to the Android GBA
frontend: audio and synchronization, video scaling and shaders, fast-forward,
rewind, autofire, save-state extended data, physical key mapping, BIOS, patches,
cheats, GBA compatibility, logging, and foreground/background behavior. BIOS,
patch, and cheat documents are copied into app-private storage before native use.
Shader selection and per-pass uniform values are persisted in Android settings;
invalid shader programs fall back to unfiltered output with an on-screen error.

Desktop shell, updater, Discord, and GB/CGB/SGB-only options are intentionally
not exposed by this GBA-only Android frontend.

## Library and network behavior

The app does not request broad storage access. Android's document picker grants
access only to ROMs selected by the user, and imported ROMs remain inside the
app sandbox. Both standalone `.gba` ROMs and `.zip` archives are accepted; every
GBA entry in an archive becomes a separate library game. Archives are copied to
a bounded temporary cache, inspected without extracting paths, and deleted after
the import. Individual ROMs are limited to 64 MiB. Library metadata is stored in
a private SQLite database. Deleting a
game asks separately whether battery saves and quick saves should also be
removed. The same document picker exports and imports battery saves (`.sav`) and
quick saves (`.ss0`). Running games clone and restore battery data through the
mGBA core so exports cannot race an unflushed memory-mapped save.
The player preserves `.ss0` as the importable Quick slot and provides nine
additional independent save-state slots (`.ss1` through `.ss9`).

Android `VIEW` intents for known GBA ROM MIME types or `.gba` URI paths import
and immediately play a single game. The app intentionally does not claim generic
`application/octet-stream` or `application/zip` intents, so it does not appear as
an opener for unrelated binaries and archives. Files reported with generic MIME
types remain available through the in-app document picker.
Android backup rules retain settings and save files but exclude ROMs, covers,
BIOS files, patches, cheats, logs, and the derived library index.

The `INTERNET` permission is used only to download optional box art from:

```text
https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/
```

Cover requests contain only the selected ROM's original filename. Downloads use
HTTPS, timeouts, a 5 MiB size limit, image decoding validation, and an atomic
cache write. No analytics or account service is included.

## Build and run

Required local toolchain: JDK 11, Android SDK 34, NDK 22.1.7171670, and CMake
3.22.1. From this directory:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` and `bundleRelease` always run R8, resource shrinking, and
release lint. They emit an unsigned APK or Android App Bundle unless all four
signing values are provided as Gradle properties or environment variables:

```text
MGBA_ANDROID_KEYSTORE
MGBA_ANDROID_STORE_PASSWORD
MGBA_ANDROID_KEY_ALIAS
MGBA_ANDROID_KEY_PASSWORD
```

The launcher package includes legacy, adaptive, round, and Android 13 monochrome
variants of the official mGBA logo.

Release automation can also override `MGBA_ANDROID_VERSION_CODE` and
`MGBA_ANDROID_VERSION_NAME`. Keystores and `keystore.properties` are ignored by
Git and must never be committed.

Launch the app and add one or more `.gba` files or `.zip` archives to the local
library. The app copies each ROM into private storage, so it can reopen games
without retaining broad storage permission. Physical D-pad/gamepad keys are also
mapped.
