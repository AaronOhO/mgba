# mGBA Android

This directory contains the first Android frontend for the mGBA core in this
repository. Its GBA runtime path includes Storage Access Framework imports,
app-private ROM/save/state storage, mGBA core execution, GLES 3.0 video,
AudioTrack output, touch/controller input, and Android lifecycle handling.

## Architecture

- `RomRepository` imports a selected document into app-private storage and keeps
  its battery save beside a content-hash-based ROM identity.
- `EmulationSession` is the single-threaded owner of `EmulatorCore`; every core,
  input, reset, frame, and audio call is serialized on that thread.
- `MgbaNativeCore` is the Kotlin/JNI adapter. `mgba_android.c` embeds the existing
  C core as a static library and exposes only the small contract the app needs.
- `EmulatorViewModel` connects lifecycle/state, video, and `AudioTrack` without
  exposing Android UI objects to the core layer.
- `SettingsRepository` persists the Android/GBA subset of the Qt frontend
  configuration and applies hot settings or reload-required settings through
  `EmulationSession`.
- `MgbaApp` is the Compose application layer; `FrameView` is a GLES 3.0 renderer
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

Desktop shell, updater, Discord, game-library presentation, and GB/CGB/SGB-only
options are intentionally not exposed by this GBA-only Android frontend.

## Build and run

Required local toolchain: JDK 11, Android SDK 34, NDK 22.1.7171670, and CMake
3.22.1. From this directory:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the app, choose a `.gba` file, and use the on-screen controls. The app
copies the ROM into private storage, so it can reopen the last game without
retaining broad storage permission. Physical D-pad/gamepad keys are also mapped.
