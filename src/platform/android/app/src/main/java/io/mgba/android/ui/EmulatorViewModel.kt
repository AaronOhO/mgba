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
import io.mgba.android.core.EmulatorKey
import io.mgba.android.core.MgbaNativeCore
import io.mgba.android.data.CoverRepository
import io.mgba.android.data.AndroidRomSource
import io.mgba.android.data.RomRepository
import io.mgba.android.data.RomSourceExpander
import io.mgba.android.settings.SettingsRepository
import io.mgba.android.logic.emulation.EmulationMessage
import io.mgba.android.logic.emulation.EmulationSession
import io.mgba.android.logic.emulation.EmulatorFailure
import io.mgba.android.logic.emulation.EmulatorState
import io.mgba.android.logic.library.GameDataKind
import io.mgba.android.logic.library.ImportGamesUseCase
import io.mgba.android.logic.library.LibraryGame
import io.mgba.android.logic.settings.EmulatorSettings
import io.mgba.android.shader.ShaderCatalog
import io.mgba.android.R
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {
    fun interface FrameConsumer {
        fun render(pixels: IntArray, width: Int, height: Int)
    }

    data class PerformanceStats(val fps: Float = 0f, val frameNumber: Long = 0)

    private val repository = RomRepository(application)
    private val importGames = ImportGamesUseCase(repository::import)
    private val romSourceExpander = RomSourceExpander(application.cacheDir)
    private val coverRepository = CoverRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val logFile = File(application.filesDir, "logs/mgba.log").also { it.parentFile?.mkdirs() }
    private val session = EmulationSession(MgbaNativeCore(), settingsRepository.settings.value, logFile.absolutePath)
    private val audioSink = AndroidAudioSink()
    private val consumer = AtomicReference<FrameConsumer?>(null)
    private val mutableState = MutableStateFlow<EmulatorState>(EmulatorState.Idle)
    private val mutableStats = MutableStateFlow(PerformanceStats())
    private val mutableMessage = MutableStateFlow<String?>(null)
    private val mutableBindingTarget = MutableStateFlow<EmulatorKey?>(null)
    private val mutableActiveGameId = MutableStateFlow<String?>(null)
    private val mutableSaveStateSlot = MutableStateFlow(EmulationSession.QUICK_STATE_SLOT)
    private val attemptedCoverIds = mutableSetOf<String>()
    private var pendingPlayTrackingId: String? = null
    private var playerVisible = false
    private var measuredFrames = 0
    private var measuredAt = System.nanoTime()

    val shaderCatalog = ShaderCatalog.load(application.assets)
    val games: StateFlow<List<LibraryGame>> = repository.games
    val state: StateFlow<EmulatorState> = mutableState.asStateFlow()
    val settings: StateFlow<EmulatorSettings> = settingsRepository.settings
    val stats: StateFlow<PerformanceStats> = mutableStats.asStateFlow()
    val message: StateFlow<String?> = mutableMessage.asStateFlow()
    val bindingTarget: StateFlow<EmulatorKey?> = mutableBindingTarget.asStateFlow()
    val activeGameId: StateFlow<String?> = mutableActiveGameId.asStateFlow()
    val saveStateSlot: StateFlow<Int> = mutableSaveStateSlot.asStateFlow()

    init {
        session.listener = object : EmulationSession.Listener {
            override fun onStateChanged(state: EmulatorState) {
                when (state) {
                    is EmulatorState.Running -> {
                        audioSink.start(state.game.audioSampleRate)
                        pendingPlayTrackingId?.let { id ->
                            pendingPlayTrackingId = null
                            viewModelScope.launch { repository.markPlayed(id) }
                        }
                    }
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
        viewModelScope.launch {
            repository.games.collect { games ->
                games.filter { it.coverFile == null && attemptedCoverIds.add(it.id) }.forEach { game ->
                    fetchCover(game)
                }
            }
        }
    }

    fun importRoms(uris: List<Uri>) = importRoms(uris, autoPlaySingle = false)

    fun openRom(uri: Uri) = importRoms(listOf(uri), autoPlaySingle = true)

    private fun importRoms(uris: List<Uri>, autoPlaySingle: Boolean) {
        if (uris.isEmpty()) return
        mutableMessage.value = string(R.string.message_importing_games)
        viewModelScope.launch {
            val expansion = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                romSourceExpander.expand(uris.map { AndroidRomSource(resolver, it) })
            }
            val imported = try {
                importGames(expansion.sources)
            } finally {
                expansion.close()
            }
            val summary = imported.summary.copy(
                failed = imported.summary.failed + expansion.failedDocuments,
            )
            mutableMessage.value = string(
                R.string.message_import_summary,
                summary.imported,
                summary.alreadyImported,
                summary.failed,
            )
            if (summary.imported == 0 && summary.alreadyImported == 0 && summary.failed == 1) {
                mutableMessage.value = string(R.string.message_rom_import_failed)
            }
            if (autoPlaySingle && summary.failed == 0 && imported.games.size == 1) {
                playGame(imported.games.single())
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

    fun onForeground() {
        if (playerVisible) session.resume()
    }

    fun onBackground() {
        when {
            settings.value.pauseOnBackground -> session.pause()
            settings.value.muteOnBackground -> audioSink.pause()
        }
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus && playerVisible) {
            session.resume()
        } else {
            when {
                settings.value.pauseOnFocusLost -> session.pause()
                settings.value.muteOnFocusLost -> audioSink.pause()
            }
        }
    }

    fun reset() = session.reset()

    fun setPlayerVisible(visible: Boolean) {
        if (visible && !playerVisible) session.resume()
        playerVisible = visible
    }

    fun playGame(game: LibraryGame) {
        mutableState.value = EmulatorState.Loading(game.name)
        mutableActiveGameId.value = game.id
        pendingPlayTrackingId = game.id
        session.resume()
        loadStoredRom(game)
    }

    fun resumePlayer() = session.resume()

    fun leavePlayer() {
        session.pause()
    }

    fun setFavorite(game: LibraryGame, favorite: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setFavorite(game.id, favorite) }
                .onFailure { mutableMessage.value = it.message ?: string(R.string.message_library_update_failed) }
        }
    }

    fun renameGame(game: LibraryGame, name: String) {
        viewModelScope.launch {
            runCatching { repository.rename(game.id, name) }
                .onFailure { mutableMessage.value = it.message ?: string(R.string.message_library_update_failed) }
        }
    }

    fun deleteGame(game: LibraryGame, deleteSaveData: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (mutableActiveGameId.value == game.id) {
                    session.pause()
                    mutableActiveGameId.value = null
                    mutableState.value = EmulatorState.Idle
                }
                repository.delete(game.id, deleteSaveData)
                coverRepository.delete(game.id)
            }.onFailure {
                mutableMessage.value = it.message ?: string(R.string.message_delete_game_failed)
            }
        }
    }

    fun importGameData(gameId: String, kind: GameDataKind, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val game = games.value.firstOrNull { it.id == gameId }
                    ?: error(string(R.string.error_game_not_found))
                val data = repository.readDocument(uri, kind)
                val active = mutableActiveGameId.value == game.id
                when {
                    kind == GameDataKind.BATTERY_SAVE && active -> {
                        check(withContext(Dispatchers.IO) { session.restoreBatterySave(data) }) {
                            string(R.string.message_game_data_import_failed)
                        }
                    }
                    else -> {
                        repository.replaceGameData(game, kind, data)
                        if (kind == GameDataKind.QUICK_STATE && active) {
                            check(withContext(Dispatchers.IO) { session.loadImportedQuickState() }) {
                                string(R.string.message_game_data_import_failed)
                            }
                        }
                    }
                }
            }.onSuccess {
                mutableMessage.value = string(
                    if (kind == GameDataKind.BATTERY_SAVE) R.string.message_save_imported
                    else R.string.message_state_imported,
                )
            }.onFailure {
                mutableMessage.value = it.message ?: string(R.string.message_game_data_import_failed)
            }
        }
    }

    fun exportGameData(gameId: String, kind: GameDataKind, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val game = games.value.firstOrNull { it.id == gameId }
                    ?: error(string(R.string.error_game_not_found))
                val data = if (kind == GameDataKind.BATTERY_SAVE && mutableActiveGameId.value == game.id) {
                    withContext(Dispatchers.IO) { session.cloneBatterySave() }
                } else {
                    repository.readGameData(game, kind)
                } ?: error(string(R.string.error_no_game_data))
                repository.exportDocument(uri, data)
            }.onSuccess {
                mutableMessage.value = string(
                    if (kind == GameDataKind.BATTERY_SAVE) R.string.message_save_exported
                    else R.string.message_state_exported,
                )
            }.onFailure {
                mutableMessage.value = it.message ?: string(R.string.message_game_data_export_failed)
            }
        }
    }

    fun refreshCover(game: LibraryGame) {
        attemptedCoverIds.remove(game.id)
        attemptedCoverIds.add(game.id)
        viewModelScope.launch {
            repository.setCover(game.id, null)
            fetchCover(game, force = true)
        }
    }

    fun importCover(gameId: String, uri: Uri) {
        attemptedCoverIds.add(gameId)
        viewModelScope.launch {
            runCatching {
                val game = games.value.firstOrNull { it.id == gameId }
                    ?: error(string(R.string.error_game_not_found))
                val cover = coverRepository.importCover(game, uri)
                repository.setCover(game.id, null)
                repository.setCover(game.id, cover)
            }.onSuccess {
                mutableMessage.value = string(R.string.message_cover_updated)
            }.onFailure {
                mutableMessage.value = string(R.string.message_cover_import_failed)
            }
        }
    }

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

    fun selectSaveStateSlot(slot: Int) {
        require(slot in EmulationSession.QUICK_STATE_SLOT..EmulationSession.MAX_STATE_SLOT)
        mutableSaveStateSlot.value = slot
    }

    fun quickSave() = session.quickSave(mutableSaveStateSlot.value)

    fun quickLoad() = session.quickLoad(mutableSaveStateSlot.value)

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

    private suspend fun fetchCover(game: LibraryGame, force: Boolean = false) {
        attemptedCoverIds.add(game.id)
        val cover = coverRepository.fetch(game, force)
        if (cover != null) repository.setCover(game.id, cover)
    }

    private fun loadStoredRom(rom: LibraryGame) {
        session.loadRom(
            romPath = rom.romFile.absolutePath,
            savePath = rom.saveFile.absolutePath,
            statePath = rom.stateFile.absolutePath,
            displayName = rom.name,
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
        EmulationMessage.SAVE_STATE_SAVED -> R.string.message_save_state_created
        EmulationMessage.SAVE_STATE_FAILED -> R.string.message_save_state_failed
        EmulationMessage.SAVE_STATE_LOADED -> R.string.message_save_state_loaded
        EmulationMessage.NO_SAVE_STATE -> R.string.message_no_save_state
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
