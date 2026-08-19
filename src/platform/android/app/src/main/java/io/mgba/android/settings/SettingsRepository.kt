/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.settings

import android.content.Context
import android.content.SharedPreferences
import io.mgba.android.core.EmulatorKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(read())

    val settings: StateFlow<EmulatorSettings> = mutableSettings.asStateFlow()

    fun update(transform: (EmulatorSettings) -> EmulatorSettings) {
        val updated = transform(mutableSettings.value).normalized()
        write(updated)
        mutableSettings.value = updated
    }

    fun reset() {
        val defaults = EmulatorSettings()
        write(defaults)
        mutableSettings.value = defaults
    }

    private fun read(): EmulatorSettings {
        val defaults = EmulatorSettings()
        return EmulatorSettings(
            volume = preferences.getInt("volume", defaults.volume),
            muted = preferences.getBoolean("muted", defaults.muted),
            sampleRate = preferences.getInt("sample_rate", defaults.sampleRate),
            audioBufferFrames = preferences.getInt("audio_buffer_frames", defaults.audioBufferFrames),
            targetFps = preferences.getFloat("target_fps", defaults.targetFps),
            frameSkip = preferences.getInt("frame_skip", defaults.frameSkip),
            videoSync = preferences.getBoolean("video_sync", defaults.videoSync),
            audioSync = preferences.getBoolean("audio_sync", defaults.audioSync),
            fastForwardRatio = preferences.getFloat("fast_forward_ratio", defaults.fastForwardRatio),
            fastForwardHeldRatio = preferences.getFloat("fast_forward_held_ratio", defaults.fastForwardHeldRatio),
            fastForwardVolume = preferences.getInt("fast_forward_volume", defaults.fastForwardVolume),
            fastForwardMuted = preferences.getBoolean("fast_forward_muted", defaults.fastForwardMuted),
            rewindEnabled = preferences.getBoolean("rewind_enabled", defaults.rewindEnabled),
            rewindCapacity = preferences.getInt("rewind_capacity", defaults.rewindCapacity),
            rewindInterval = preferences.getInt("rewind_interval", defaults.rewindInterval),
            autoLoadState = preferences.getBoolean("auto_load_state", defaults.autoLoadState),
            autoSaveState = preferences.getBoolean("auto_save_state", defaults.autoSaveState),
            loadStateScreenshot = preferences.getBoolean("load_state_screenshot", defaults.loadStateScreenshot),
            loadStateSaveData = preferences.getBoolean("load_state_save_data", defaults.loadStateSaveData),
            loadStateCheats = preferences.getBoolean("load_state_cheats", defaults.loadStateCheats),
            saveStateScreenshot = preferences.getBoolean("save_state_screenshot", defaults.saveStateScreenshot),
            saveStateSaveData = preferences.getBoolean("save_state_save_data", defaults.saveStateSaveData),
            saveStateCheats = preferences.getBoolean("save_state_cheats", defaults.saveStateCheats),
            preloadRom = preferences.getBoolean("preload_rom", defaults.preloadRom),
            autofireThreshold = preferences.getInt("autofire_threshold", defaults.autofireThreshold),
            allowOpposingDirections = preferences.getBoolean("allow_opposing", defaults.allowOpposingDirections),
            lockAspectRatio = preferences.getBoolean("lock_aspect", defaults.lockAspectRatio),
            integerScaling = preferences.getBoolean("integer_scaling", defaults.integerScaling),
            interframeBlending = preferences.getBoolean("interframe_blending", defaults.interframeBlending),
            linearFiltering = preferences.getBoolean("linear_filtering", defaults.linearFiltering),
            showFps = preferences.getBoolean("show_fps", defaults.showFps),
            showFrameCounter = preferences.getBoolean("show_frame_counter", defaults.showFrameCounter),
            showResetInfo = preferences.getBoolean("show_reset_info", defaults.showResetInfo),
            showOsd = preferences.getBoolean("show_osd", defaults.showOsd),
            showFilename = preferences.getBoolean("show_filename", defaults.showFilename),
            keepScreenOn = preferences.getBoolean("keep_screen_on", defaults.keepScreenOn),
            pauseOnFocusLost = preferences.getBoolean("pause_on_focus_lost", defaults.pauseOnFocusLost),
            muteOnFocusLost = preferences.getBoolean("mute_on_focus_lost", defaults.muteOnFocusLost),
            pauseOnBackground = preferences.getBoolean("pause_on_background", defaults.pauseOnBackground),
            muteOnBackground = preferences.getBoolean("mute_on_background", defaults.muteOnBackground),
            useBios = preferences.getBoolean("use_bios", defaults.useBios),
            skipBios = preferences.getBoolean("skip_bios", defaults.skipBios),
            biosPath = preferences.getString("bios_path", defaults.biosPath) ?: defaults.biosPath,
            patchPath = preferences.getString("patch_path", defaults.patchPath) ?: defaults.patchPath,
            cheatsPath = preferences.getString("cheats_path", defaults.cheatsPath) ?: defaults.cheatsPath,
            cheatAutoload = preferences.getBoolean("cheat_autoload", defaults.cheatAutoload),
            cheatAutosave = preferences.getBoolean("cheat_autosave", defaults.cheatAutosave),
            idleOptimization = preferences.enum("idle_optimization", defaults.idleOptimization),
            forceGameBoyPlayer = preferences.getBoolean("force_gbp", defaults.forceGameBoyPlayer),
            vbaBugCompatibility = preferences.getBoolean("vba_bug_compat", defaults.vbaBugCompatibility),
            shaderId = preferences.getString("shader_id", null) ?: legacyShaderId(),
            shaderParameters = preferences.getStringSet("shader_parameters", emptySet())
                .orEmpty()
                .mapNotNull { entry ->
                    val separator = entry.lastIndexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    entry.substring(separator + 1).toFloatOrNull()?.let { entry.substring(0, separator) to it }
                }
                .toMap(),
            logLevel = preferences.getInt("log_level", defaults.logLevel),
            logToFile = preferences.getBoolean("log_to_file", defaults.logToFile),
            inputBindings = EmulatorKey.values().associateWith { key ->
                preferences.getInt("input_${key.name.lowercase()}", defaults.inputBindings.getValue(key))
            },
        ).normalized()
    }

    private fun write(settings: EmulatorSettings) {
        val editor = preferences.edit()
            .putInt("volume", settings.volume)
            .putBoolean("muted", settings.muted)
            .putInt("sample_rate", settings.sampleRate)
            .putInt("audio_buffer_frames", settings.audioBufferFrames)
            .putFloat("target_fps", settings.targetFps)
            .putInt("frame_skip", settings.frameSkip)
            .putBoolean("video_sync", settings.videoSync)
            .putBoolean("audio_sync", settings.audioSync)
            .putFloat("fast_forward_ratio", settings.fastForwardRatio)
            .putFloat("fast_forward_held_ratio", settings.fastForwardHeldRatio)
            .putInt("fast_forward_volume", settings.fastForwardVolume)
            .putBoolean("fast_forward_muted", settings.fastForwardMuted)
            .putBoolean("rewind_enabled", settings.rewindEnabled)
            .putInt("rewind_capacity", settings.rewindCapacity)
            .putInt("rewind_interval", settings.rewindInterval)
            .putBoolean("auto_load_state", settings.autoLoadState)
            .putBoolean("auto_save_state", settings.autoSaveState)
            .putBoolean("load_state_screenshot", settings.loadStateScreenshot)
            .putBoolean("load_state_save_data", settings.loadStateSaveData)
            .putBoolean("load_state_cheats", settings.loadStateCheats)
            .putBoolean("save_state_screenshot", settings.saveStateScreenshot)
            .putBoolean("save_state_save_data", settings.saveStateSaveData)
            .putBoolean("save_state_cheats", settings.saveStateCheats)
            .putBoolean("preload_rom", settings.preloadRom)
            .putInt("autofire_threshold", settings.autofireThreshold)
            .putBoolean("allow_opposing", settings.allowOpposingDirections)
            .putBoolean("lock_aspect", settings.lockAspectRatio)
            .putBoolean("integer_scaling", settings.integerScaling)
            .putBoolean("interframe_blending", settings.interframeBlending)
            .putBoolean("linear_filtering", settings.linearFiltering)
            .putBoolean("show_fps", settings.showFps)
            .putBoolean("show_frame_counter", settings.showFrameCounter)
            .putBoolean("show_reset_info", settings.showResetInfo)
            .putBoolean("show_osd", settings.showOsd)
            .putBoolean("show_filename", settings.showFilename)
            .putBoolean("keep_screen_on", settings.keepScreenOn)
            .putBoolean("pause_on_focus_lost", settings.pauseOnFocusLost)
            .putBoolean("mute_on_focus_lost", settings.muteOnFocusLost)
            .putBoolean("pause_on_background", settings.pauseOnBackground)
            .putBoolean("mute_on_background", settings.muteOnBackground)
            .putBoolean("use_bios", settings.useBios)
            .putBoolean("skip_bios", settings.skipBios)
            .putString("bios_path", settings.biosPath)
            .putString("patch_path", settings.patchPath)
            .putString("cheats_path", settings.cheatsPath)
            .putBoolean("cheat_autoload", settings.cheatAutoload)
            .putBoolean("cheat_autosave", settings.cheatAutosave)
            .putString("idle_optimization", settings.idleOptimization.name)
            .putBoolean("force_gbp", settings.forceGameBoyPlayer)
            .putBoolean("vba_bug_compat", settings.vbaBugCompatibility)
            .putString("shader_id", settings.shaderId)
            .putStringSet(
                "shader_parameters",
                settings.shaderParameters.mapTo(mutableSetOf()) { (key, value) -> "$key=$value" },
            )
            .remove("shader_preset")
            .putInt("log_level", settings.logLevel)
            .putBoolean("log_to_file", settings.logToFile)
        settings.inputBindings.forEach { (key, keyCode) ->
            editor.putInt("input_${key.name.lowercase()}", keyCode)
        }
        editor.apply()
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T {
        val name = getString(key, default.name) ?: return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    private fun legacyShaderId(): String = when (preferences.getString("shader_preset", "NONE")) {
        "LCD" -> "lcd"
        "SCANLINES" -> "scanlines"
        else -> ""
    }

    private companion object {
        const val PREFERENCES_NAME = "emulator_settings"
    }
}
