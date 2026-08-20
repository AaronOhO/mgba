<!-- Copyright (c) 2026 Jeffrey Pfau

     This Source Code Form is subject to the terms of the Mozilla Public
     License, v. 2.0. If a copy of the MPL was not distributed with this
     file, You can obtain one at http://mozilla.org/MPL/2.0/. -->
# mGBA Android core

This module is the reusable Android binding for the mGBA core. It contains no
product UI, library database, cover provider, or app resources.

Use it from this Gradle build with:

```groovy
dependencies {
    implementation project(':core')
}
```

Or build `core-release.aar` with `./gradlew :core:assembleRelease`, copy it into
another Android project's `libs` directory, and use:

```groovy
dependencies {
    implementation files('libs/core-release.aar')
}
```

The AAR includes `libmgba-android.so` for `arm64-v8a` and `x86_64` and publishes
its JNI keep rules to R8 consumers.

## Minimal API usage

```kotlin
val core: EmulatorCore = MgbaNativeCore()
val config = CoreConfig(
    options = mapOf("volume" to "256"),
    preloadRom = true,
    patchPath = "",
    cheatsPath = "",
    cheatAutoload = false,
    rewindEnabled = false,
    rewindCapacity = 300,
    rewindInterval = 1,
)

val game = core.load(romPath, savePath, config)
val pixels = IntArray(game.width * game.height)
core.runFrame(pixels)
core.setKey(EmulatorKey.A, true)
core.setKey(EmulatorKey.A, false)
core.close()
```

Serialize all calls made to a single `EmulatorCore` instance. The `:logic`
module provides `EmulationSession` when an application wants a ready-made frame
loop, input serialization, rewind, quick saves, and lifecycle-safe shutdown.
