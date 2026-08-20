<!-- Copyright (c) 2026 Jeffrey Pfau

     This Source Code Form is subject to the terms of the Mozilla Public
     License, v. 2.0. If a copy of the MPL was not distributed with this
     file, You can obtain one at http://mozilla.org/MPL/2.0/. -->
# Android architecture

The Android port uses three Gradle modules with one-way dependencies:

```text
:app  ->  :logic  ->  :core
```

`:logic` exposes the small set of core contracts required by its public API, so
the product has one declared dependency path. Neither lower module may reference
an upper module.

## `:core`: reusable mGBA Android integration

`:core` is an Android library that owns the JNI bridge, native CMake build, and
the smallest stable API needed to embed mGBA in another Android application:

- `EmulatorCore` is the platform-neutral execution contract.
- `MgbaNativeCore` is the bundled JNI-backed implementation.
- `CoreConfig`, `GameMetadata`, and `EmulatorKey` are public API models.
- `AndroidAudioSink` is an optional Android PCM output adapter.

It does not know about Compose, product navigation, game libraries, cover art,
settings storage, or localized resources. Consumers can build the standalone AAR
with `./gradlew :core:assembleRelease` and use the artifact from
`core/build/outputs/aar/core-release.aar`. See [core/README.md](core/README.md)
for the embedding example.

All calls on one `EmulatorCore` instance must be serialized. Applications that
want a ready-made execution loop should also depend on `:logic` and use
`EmulationSession`.

## `:logic`: UI-free business rules

`:logic` is packaged as an Android library so it can depend on the `:core` AAR,
but its source is pure Kotlin/JDK code. It owns:

- the serialized emulation session and state machine;
- normalized emulator settings and native configuration mapping;
- library domain models and multi-ROM import result aggregation.

It may depend on `:core`, Kotlin, the JDK, and Kotlin coroutines. It may not import
Android framework APIs, AndroidX, Compose, product repositories, resources, or
source-specific integrations.

## `:app`: product and delivery layer

`:app` owns everything specific to the shipped mGBA product:

- Compose screens, navigation, accessibility, and responsive controls;
- SAF document selection and Android lifecycle integration;
- SQLite, private file storage, and preferences implementations;
- Libretro cover-art lookup and any future game-source integrations;
- localization, app resources, signing, shrinking, and store packaging.

The view model maps Android events and localized product messages to the pure
logic APIs. Product integrations must not be added to `:core` or `:logic`.

## Enforced boundaries

`verifyCoreBoundary` rejects upper-layer imports from `:core`.
`verifyPureLogic` rejects Android, AndroidX, resource, UI, and product imports
from `:logic`. Both checks run automatically before their module builds.
