/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.settings

import android.view.KeyEvent
import io.mgba.android.core.EmulatorKey

enum class IdleOptimization {
    IGNORE,
    REMOVE,
    DETECT,
}

data class EmulatorSettings(
    val volume: Int = 0x100,
    val muted: Boolean = false,
    val sampleRate: Int = 44_100,
    val audioBufferFrames: Int = 1536,
    val targetFps: Float = NATIVE_GBA_FPS,
    val frameSkip: Int = 0,
    val videoSync: Boolean = true,
    val audioSync: Boolean = true,
    val fastForwardRatio: Float = 2f,
    val fastForwardHeldRatio: Float = 2f,
    val fastForwardVolume: Int = 0x100,
    val fastForwardMuted: Boolean = false,
    val rewindEnabled: Boolean = false,
    val rewindCapacity: Int = 300,
    val rewindInterval: Int = 1,
    val autoLoadState: Boolean = true,
    val autoSaveState: Boolean = false,
    val loadStateScreenshot: Boolean = true,
    val loadStateSaveData: Boolean = false,
    val loadStateCheats: Boolean = false,
    val saveStateScreenshot: Boolean = true,
    val saveStateSaveData: Boolean = true,
    val saveStateCheats: Boolean = true,
    val preloadRom: Boolean = true,
    val autofireThreshold: Int = 1,
    val allowOpposingDirections: Boolean = false,
    val lockAspectRatio: Boolean = true,
    val integerScaling: Boolean = true,
    val interframeBlending: Boolean = false,
    val linearFiltering: Boolean = false,
    val showFps: Boolean = true,
    val showFrameCounter: Boolean = false,
    val showResetInfo: Boolean = true,
    val showOsd: Boolean = true,
    val showFilename: Boolean = false,
    val keepScreenOn: Boolean = true,
    val pauseOnFocusLost: Boolean = false,
    val muteOnFocusLost: Boolean = false,
    val pauseOnBackground: Boolean = true,
    val muteOnBackground: Boolean = true,
    val useBios: Boolean = true,
    val skipBios: Boolean = false,
    val biosPath: String = "",
    val patchPath: String = "",
    val cheatsPath: String = "",
    val cheatAutoload: Boolean = true,
    val cheatAutosave: Boolean = true,
    val idleOptimization: IdleOptimization = IdleOptimization.DETECT,
    val forceGameBoyPlayer: Boolean = false,
    val vbaBugCompatibility: Boolean = true,
    val shaderId: String = "",
    val shaderParameters: Map<String, Float> = emptyMap(),
    val logLevel: Int = LOG_WARN or LOG_ERROR or LOG_FATAL,
    val logToFile: Boolean = false,
    val inputBindings: Map<EmulatorKey, Int> = defaultInputBindings(),
) {
    fun withInputBinding(key: EmulatorKey, keyCode: Int): EmulatorSettings {
        val bindings = inputBindings.toMutableMap()
        val displacedKey = bindings.entries.firstOrNull { it.key != key && it.value == keyCode }?.key
        val previousKeyCode = bindings.getValue(key)
        bindings[key] = keyCode
        if (displacedKey != null) bindings[displacedKey] = previousKeyCode
        return copy(inputBindings = bindings)
    }

    fun loadStateFlags(): Int = STATE_RTC or
        (if (loadStateScreenshot) STATE_SCREENSHOT else 0) or
        (if (loadStateSaveData) STATE_SAVE_DATA else 0) or
        (if (loadStateCheats) STATE_CHEATS else 0)

    fun saveStateFlags(): Int = STATE_RTC or STATE_METADATA or
        (if (saveStateScreenshot) STATE_SCREENSHOT else 0) or
        (if (saveStateSaveData) STATE_SAVE_DATA else 0) or
        (if (saveStateCheats) STATE_CHEATS else 0)

    fun normalized(): EmulatorSettings = copy(
        volume = volume.coerceIn(0, 0x100),
        sampleRate = sampleRate.coerceIn(8_000, 96_000),
        audioBufferFrames = audioBufferFrames.coerceIn(256, 8192),
        targetFps = targetFps.coerceIn(1f, 240f),
        frameSkip = frameSkip.coerceIn(0, 10),
        fastForwardRatio = if (fastForwardRatio <= 0f) -1f else fastForwardRatio.coerceIn(1f, 16f),
        fastForwardHeldRatio = if (fastForwardHeldRatio <= 0f) -1f else fastForwardHeldRatio.coerceIn(1f, 16f),
        fastForwardVolume = fastForwardVolume.coerceIn(0, volume),
        rewindCapacity = rewindCapacity.coerceIn(1, 1800),
        rewindInterval = rewindInterval.coerceIn(1, 60),
        autofireThreshold = autofireThreshold.coerceIn(1, 30),
        shaderParameters = shaderParameters.filterValues(Float::isFinite),
        inputBindings = defaultInputBindings() + inputBindings,
    )

    companion object {
        const val NATIVE_GBA_FPS = 59.7275f
        const val LOG_FATAL = 1 shl 0
        const val LOG_ERROR = 1 shl 1
        const val LOG_WARN = 1 shl 2
        const val LOG_INFO = 1 shl 3
        const val LOG_DEBUG = 1 shl 4
        private const val STATE_SCREENSHOT = 1
        private const val STATE_SAVE_DATA = 2
        private const val STATE_CHEATS = 4
        private const val STATE_RTC = 8
        private const val STATE_METADATA = 16

        fun defaultInputBindings(): Map<EmulatorKey, Int> = mapOf(
            EmulatorKey.A to KeyEvent.KEYCODE_BUTTON_A,
            EmulatorKey.B to KeyEvent.KEYCODE_BUTTON_B,
            EmulatorKey.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
            EmulatorKey.START to KeyEvent.KEYCODE_BUTTON_START,
            EmulatorKey.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            EmulatorKey.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            EmulatorKey.UP to KeyEvent.KEYCODE_DPAD_UP,
            EmulatorKey.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            EmulatorKey.R to KeyEvent.KEYCODE_BUTTON_R1,
            EmulatorKey.L to KeyEvent.KEYCODE_BUTTON_L1,
        )
    }
}
