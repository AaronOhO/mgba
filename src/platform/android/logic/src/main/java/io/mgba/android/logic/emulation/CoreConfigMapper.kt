/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.logic.emulation

import io.mgba.android.core.CoreConfig
import io.mgba.android.logic.settings.EmulatorSettings

fun EmulatorSettings.toCoreConfig(logFilePath: String = ""): CoreConfig = CoreConfig(
    options = linkedMapOf(
        "volume" to volume.toString(),
        "mute" to muted.intString(),
        "sampleRate" to sampleRate.toString(),
        "audioBuffers" to audioBufferFrames.toString(),
        "fpsTarget" to targetFps.toString(),
        "frameskip" to frameSkip.toString(),
        "videoSync" to videoSync.intString(),
        "audioSync" to audioSync.intString(),
        "rewindEnable" to rewindEnabled.intString(),
        "rewindBufferCapacity" to rewindCapacity.toString(),
        "rewindBufferInterval" to rewindInterval.toString(),
        "allowOpposingDirections" to allowOpposingDirections.intString(),
        "useBios" to useBios.intString(),
        "skipBios" to skipBios.intString(),
        "gba.bios" to biosPath,
        "idleOptimization" to idleOptimization.name.lowercase(),
        "gba.forceGbp" to forceGameBoyPlayer.intString(),
        "vbaBugCompat" to vbaBugCompatibility.intString(),
        "cheatAutoload" to cheatAutoload.intString(),
        "cheatAutosave" to cheatAutosave.intString(),
        "preload" to preloadRom.intString(),
        "logLevel" to logLevel.toString(),
        "logToFile" to logToFile.intString(),
        "logFile" to logFilePath,
    ),
    preloadRom = preloadRom,
    patchPath = patchPath,
    cheatsPath = cheatsPath,
    cheatAutoload = cheatAutoload,
    rewindEnabled = rewindEnabled,
    rewindCapacity = rewindCapacity,
    rewindInterval = rewindInterval,
)

fun EmulatorSettings.requiresCoreReload(previous: EmulatorSettings): Boolean =
    sampleRate != previous.sampleRate ||
        audioBufferFrames != previous.audioBufferFrames ||
        useBios != previous.useBios ||
        skipBios != previous.skipBios ||
        biosPath != previous.biosPath ||
        patchPath != previous.patchPath ||
        cheatsPath != previous.cheatsPath ||
        cheatAutoload != previous.cheatAutoload ||
        preloadRom != previous.preloadRom ||
        forceGameBoyPlayer != previous.forceGameBoyPlayer ||
        vbaBugCompatibility != previous.vbaBugCompatibility

private fun Boolean.intString(): String = if (this) "1" else "0"
