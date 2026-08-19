/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.mgba.android.core.AndroidAudioSink
import io.mgba.android.core.EmulationMessage
import io.mgba.android.core.EmulationSession
import io.mgba.android.core.EmulatorFailure
import io.mgba.android.core.EmulatorKey
import io.mgba.android.core.EmulatorState
import io.mgba.android.core.MgbaNativeCore
import io.mgba.android.data.RomRepository
import io.mgba.android.data.StoredRom
import io.mgba.android.settings.EmulatorSettings
import io.mgba.android.settings.SettingsRepository
import io.mgba.android.shader.ShaderCatalog
import io.mgba.android.R
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {
    fun interface FrameConsumer {
        fun render(pixels: IntArray, width: Int, height: Int)
    }

    data class PerformanceStats(val fps: Float = 0f, val frameNumber: Long = 0)

    private val repository = RomRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val logFile = File(application.filesDir, "logs/mgba.log").also { it.parentFile?.mkdirs() }
    private val session = EmulationSession(MgbaNativeCore(), settingsRepository.settings.value, logFile.absolutePath)
    private val audioSink = AndroidAudioSink()
    private val consumer = AtomicReference<FrameConsumer?>(null)
    private val mutableState = MutableStateFlow<EmulatorState>(EmulatorState.Idle)
    private val mutableStats = MutableStateFlow(PerformanceStats())
    private val mutableMessage = MutableStateFlow<String?>(null)
    private val mutableBindingTarget = MutableStateFlow<EmulatorKey?>(null)
    private var measuredFrames = 0
    private var measuredAt = System.nanoTime()

    val shaderCatalog = ShaderCatalog.load(application.assets)
    val state: StateFlow<EmulatorState> = mutableState.asStateFlow()
    val settings: StateFlow<EmulatorSettings> = settingsRepository.settings
    val stats: StateFlow<PerformanceStats> = mutableStats.asStateFlow()
    val message: StateFlow<String?> = mutableMessage.asStateFlow()
    val bindingTarget: StateFlow<EmulatorKey?> = mutableBindingTarget.asStateFlow()

    init {
        session.listener = object : EmulationSession.Listener {
            override fun onStateChanged(state: EmulatorState) {
                when (state) {
                    is EmulatorState.Running -> audioSink.start(state.game.audioSampleRate)
                    else -> audioSink.pause()
                }
                mutableState.value = state
            }

            override fun onFrame(pixels: IntArray, width: Int, height: Int, frameNumber: Long) {
                consumer.get()?.render(pixels, width, height)
                measuredFrames++
                val now = System.nanoTime()
                val elapsed = now - measuredAt
                if (elapsed >= 1_000_000_000L) {
                    mutableStats.value = PerformanceStats(
                        fps = measuredFrames * 1_000_000_000f / elapsed,
                        frameNumber = frameNumber,
                    )
                    measuredFrames = 0
                    measuredAt = now
                }
            }

            override fun onAudio(samples: ShortArray, frameCount: Int, sampleRate: Int, synchronize: Boolean) {
                audioSink.write(samples, frameCount, synchronize)
            }

            override fun onMessage(message: EmulationMessage) {
                if (settings.value.showOsd) mutableMessage.value = string(message.stringResource())
            }
        }
        repository.lastRom()?.let(::loadStoredRom)
    }

    fun importRom(uri: Uri) {
        mutableState.value = EmulatorState.Loading(string(R.string.message_importing_rom))
        viewModelScope.launch {
            runCatching { repository.import(uri) }
                .onSuccess(::loadStoredRom)
                .onFailure {
                    mutableState.value = EmulatorState.Error(EmulatorFailure.ROM_IMPORT, it.message)
                }
        }
    }

    fun importBios(uri: Uri) {
        importSupportFile(R.string.message_importing_bios, { repository.importBios(uri) }) { settings, path ->
            settings.copy(biosPath = path)
        }
    }

    fun importPatch(uri: Uri) {
        importSupportFile(R.string.message_importing_patch, { repository.importPatch(uri) }) { settings, path ->
            settings.copy(patchPath = path)
        }
    }

    fun importCheats(uri: Uri) {
        importSupportFile(R.string.message_importing_cheats, { repository.importCheats(uri) }) { settings, path ->
            settings.copy(cheatsPath = path)
        }
    }

    fun onForeground() = session.resume()

    fun onBackground() {
        when {
            settings.value.pauseOnBackground -> session.pause()
            settings.value.muteOnBackground -> audioSink.pause()
        }
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            session.resume()
        } else {
            when {
                settings.value.pauseOnFocusLost -> session.pause()
                settings.value.muteOnFocusLost -> audioSink.pause()
            }
        }
    }

    fun reset() = session.reset()

    fun setKey(key: EmulatorKey, pressed: Boolean) = session.setKey(key, pressed)

    fun beginInputBinding(key: EmulatorKey) {
        mutableBindingTarget.value = key
        mutableMessage.value = string(R.string.message_press_physical_key, keyDisplayName(key))
    }

    fun handlePhysicalKey(keyCode: Int, pressed: Boolean): Boolean {
        val target = mutableBindingTarget.value
        if (target != null && pressed) {
            updateSettings { it.withInputBinding(target, keyCode) }
            mutableBindingTarget.value = null
            mutableMessage.value = string(R.string.message_key_binding_updated, keyDisplayName(target))
            return true
        }
        val key = settings.value.inputBindings.entries.firstOrNull { it.value == keyCode }?.key
            ?: return false
        session.setKey(key, pressed)
        return true
    }

    fun setAutofire(key: EmulatorKey, enabled: Boolean) = session.setAutofire(key, enabled)

    fun setFastForward(enabled: Boolean) = session.setFastForward(enabled)

    fun toggleFastForward() = session.toggleFastForward()

    fun setRewinding(enabled: Boolean) = session.setRewinding(enabled)

    fun quickSave() = session.quickSave()

    fun quickLoad() = session.quickLoad()

    fun updateSettings(transform: (EmulatorSettings) -> EmulatorSettings) {
        settingsRepository.update(transform)
        session.updateSettings(settings.value)
    }

    fun resetSettings() {
        settingsRepository.reset()
        session.updateSettings(settings.value)
        mutableBindingTarget.value = null
    }

    fun clearMessage() {
        mutableMessage.value = null
    }

    fun reportShaderError(message: String) {
        mutableMessage.value = message
    }

    private fun importSupportFile(
        @StringRes loadingMessage: Int,
        import: suspend () -> java.io.File,
        update: (EmulatorSettings, String) -> EmulatorSettings,
    ) {
        mutableMessage.value = string(loadingMessage)
        viewModelScope.launch {
            runCatching { import() }
                .onSuccess { file ->
                    updateSettings { update(it, file.absolutePath) }
                    mutableMessage.value = string(R.string.message_file_applied, file.name)
                }
                .onFailure { mutableMessage.value = it.message ?: string(R.string.message_file_import_failed) }
        }
    }

    fun setFrameConsumer(frameConsumer: FrameConsumer?) {
        consumer.set(frameConsumer)
    }

    private fun loadStoredRom(rom: StoredRom) {
        session.loadRom(
            romPath = rom.romFile.absolutePath,
            savePath = rom.saveFile.absolutePath,
            statePath = rom.stateFile.absolutePath,
            displayName = rom.displayName,
        )
    }

    override fun onCleared() {
        consumer.set(null)
        session.close()
        audioSink.release()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EmulatorViewModel::class.java))
            return EmulatorViewModel(application) as T
        }
    }

    private fun keyDisplayName(key: EmulatorKey): String = string(key.stringResource())

    private fun string(@StringRes resource: Int, vararg arguments: Any): String =
        getApplication<Application>().getString(resource, *arguments)

    private fun EmulationMessage.stringResource(): Int = when (this) {
        EmulationMessage.AUTO_SAVE_RESTORED -> R.string.message_auto_save_restored
        EmulationMessage.FAST_FORWARD_ENABLED -> R.string.message_fast_forward_enabled
        EmulationMessage.FAST_FORWARD_DISABLED -> R.string.message_fast_forward_disabled
        EmulationMessage.QUICK_SAVE_SAVED -> R.string.message_quick_save_saved
        EmulationMessage.QUICK_SAVE_FAILED -> R.string.message_quick_save_failed
        EmulationMessage.QUICK_SAVE_LOADED -> R.string.message_quick_save_loaded
        EmulationMessage.NO_QUICK_SAVE -> R.string.message_no_quick_save
        EmulationMessage.GAME_RESET -> R.string.message_game_reset
    }

    private fun EmulatorKey.stringResource(): Int = when (this) {
        EmulatorKey.A -> R.string.key_a
        EmulatorKey.B -> R.string.key_b
        EmulatorKey.SELECT -> R.string.key_select
        EmulatorKey.START -> R.string.key_start
        EmulatorKey.RIGHT -> R.string.key_right
        EmulatorKey.LEFT -> R.string.key_left
        EmulatorKey.UP -> R.string.key_up
        EmulatorKey.DOWN -> R.string.key_down
        EmulatorKey.R -> R.string.key_r
        EmulatorKey.L -> R.string.key_l
    }
}
