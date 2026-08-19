/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.core

import io.mgba.android.settings.EmulatorSettings
import java.io.Closeable
import java.io.File
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

sealed interface EmulatorState {
    object Idle : EmulatorState
    data class Loading(val romName: String) : EmulatorState
    data class Running(val game: GameMetadata) : EmulatorState
    data class Paused(val game: GameMetadata) : EmulatorState
    data class Error(val failure: EmulatorFailure, val detail: String? = null) : EmulatorState
}

enum class EmulatorFailure {
    ROM_LOAD,
    CONFIGURATION,
    EMULATION,
    ROM_IMPORT,
}

enum class EmulationMessage {
    AUTO_SAVE_RESTORED,
    FAST_FORWARD_ENABLED,
    FAST_FORWARD_DISABLED,
    QUICK_SAVE_SAVED,
    QUICK_SAVE_FAILED,
    QUICK_SAVE_LOADED,
    NO_QUICK_SAVE,
    GAME_RESET,
}

class EmulationSession(
    private val core: EmulatorCore,
    initialSettings: EmulatorSettings = EmulatorSettings(),
    private val logFilePath: String = "",
) : Closeable {
    interface Listener {
        fun onStateChanged(state: EmulatorState)

        fun onFrame(pixels: IntArray, width: Int, height: Int, frameNumber: Long)

        fun onAudio(samples: ShortArray, frameCount: Int, sampleRate: Int, synchronize: Boolean)

        fun onMessage(message: EmulationMessage) = Unit
    }

    private data class LoadedRom(
        val romPath: String,
        val savePath: String,
        val statePath: String,
        val displayName: String,
    )

    @Volatile
    var listener: Listener? = null

    @Volatile
    private var settings = initialSettings.normalized()

    private val requestedRunning = AtomicBoolean(false)
    private val fastForwardHeld = AtomicBoolean(false)
    private val fastForwardToggled = AtomicBoolean(false)
    private val rewinding = AtomicBoolean(false)
    private val executor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "mgba-emulation").apply { priority = Thread.NORM_PRIORITY + 1 }
    }.apply {
        removeOnCancelPolicy = true
    }

    private val autofireKeys = mutableSetOf<EmulatorKey>()
    private var autofireFrame = 0
    private var frameLoopActive = false
    private var game: GameMetadata? = null
    private var loadedRom: LoadedRom? = null
    private var frame = IntArray(0)
    private val audio = ShortArray(4096)
    private var frameNumber = 0L
    private var nextFrameAtNanos = 0L
    private var lastFrameIntervalNanos = 0L

    fun loadRom(
        romPath: String,
        savePath: String,
        statePath: String,
        displayName: String,
    ) {
        listener?.onStateChanged(EmulatorState.Loading(displayName))
        executor.execute {
            try {
                autoSaveCurrentGame()
                core.close()
                val source = LoadedRom(romPath, savePath, statePath, displayName)
                val metadata = core.load(romPath, savePath, settings.toCoreConfig(logFilePath))
                    .copy(displayName = displayName)
                loadedRom = source
                game = metadata
                frame = IntArray(metadata.width * metadata.height)
                frameNumber = 0
                if (settings.autoLoadState && File(statePath).isFile &&
                    core.loadStateFile(statePath, settings.loadStateFlags())) {
                    listener?.onMessage(EmulationMessage.AUTO_SAVE_RESTORED)
                }
                notifyPlaybackState(metadata)
                startFrameLoopIfNeeded()
            } catch (error: Throwable) {
                fail(error, EmulatorFailure.ROM_LOAD)
            }
        }
    }

    fun updateSettings(updated: EmulatorSettings) {
        val normalized = updated.normalized()
        val previous = settings
        settings = normalized
        executor.execute {
            val source = loadedRom
            val metadata = game
            try {
                if (source != null && metadata != null && normalized.requiresCoreReload(previous)) {
                    val state = core.saveState()
                    core.close()
                    val reloaded = core.load(source.romPath, source.savePath, normalized.toCoreConfig(logFilePath))
                        .copy(displayName = source.displayName)
                    core.loadState(state)
                    game = reloaded
                    frame = IntArray(reloaded.width * reloaded.height)
                    notifyPlaybackState(reloaded)
                } else if (metadata != null) {
                    core.applyConfig(runtimeCoreConfig())
                }
            } catch (error: Throwable) {
                fail(error, EmulatorFailure.CONFIGURATION)
            }
        }
    }

    fun resume() {
        requestedRunning.set(true)
        executor.execute {
            game?.let {
                listener?.onStateChanged(EmulatorState.Running(it))
                startFrameLoopIfNeeded()
            }
        }
    }

    fun pause() {
        requestedRunning.set(false)
        executor.execute {
            core.clearKeys()
            autoSaveCurrentGame()
            game?.let { listener?.onStateChanged(EmulatorState.Paused(it)) }
        }
    }

    fun setKey(key: EmulatorKey, pressed: Boolean) {
        executor.execute {
            if (game != null) core.setKey(key, pressed)
        }
    }

    fun setAutofire(key: EmulatorKey, enabled: Boolean) {
        executor.execute {
            if (enabled) {
                autofireKeys += key
            } else {
                autofireKeys -= key
                if (game != null) core.setKey(key, false)
            }
        }
    }

    fun setFastForward(enabled: Boolean) {
        fastForwardHeld.set(enabled)
        executor.execute {
            if (game != null) core.applyConfig(runtimeCoreConfig())
        }
    }

    fun toggleFastForward() {
        val enabled = !fastForwardToggled.get()
        fastForwardToggled.set(enabled)
        executor.execute {
            if (game != null) core.applyConfig(runtimeCoreConfig())
            listener?.onMessage(
                if (enabled) EmulationMessage.FAST_FORWARD_ENABLED
                else EmulationMessage.FAST_FORWARD_DISABLED,
            )
        }
    }

    fun setRewinding(enabled: Boolean) {
        rewinding.set(enabled && settings.rewindEnabled)
        if (enabled) executor.execute { core.clearKeys() }
    }

    fun quickSave() {
        executor.execute {
            val path = loadedRom?.statePath ?: return@execute
            val message = if (core.saveStateFile(path, settings.saveStateFlags())) {
                EmulationMessage.QUICK_SAVE_SAVED
            } else {
                EmulationMessage.QUICK_SAVE_FAILED
            }
            listener?.onMessage(message)
        }
    }

    fun quickLoad() {
        executor.execute {
            val path = loadedRom?.statePath ?: return@execute
            val message = if (File(path).isFile && core.loadStateFile(path, settings.loadStateFlags())) {
                EmulationMessage.QUICK_SAVE_LOADED
            } else {
                EmulationMessage.NO_QUICK_SAVE
            }
            listener?.onMessage(message)
        }
    }

    fun reset() {
        executor.execute {
            if (game != null) {
                core.clearKeys()
                core.reset()
                if (settings.showResetInfo) listener?.onMessage(EmulationMessage.GAME_RESET)
            }
        }
    }

    private fun runtimeCoreConfig(): CoreConfig {
        val current = settings
        return if (isFastForwarding()) {
            current.copy(volume = current.fastForwardVolume, muted = current.fastForwardMuted).toCoreConfig(logFilePath)
        } else {
            current.toCoreConfig(logFilePath)
        }
    }

    private fun notifyPlaybackState(metadata: GameMetadata) {
        val state = if (requestedRunning.get()) EmulatorState.Running(metadata) else EmulatorState.Paused(metadata)
        listener?.onStateChanged(state)
    }

    private fun startFrameLoopIfNeeded() {
        if (!requestedRunning.get() || game == null || frameLoopActive) return
        frameLoopActive = true
        runFrame()
    }

    private fun runFrame() {
        val metadata = game
        if (!requestedRunning.get() || metadata == null) {
            frameLoopActive = false
            return
        }

        try {
            val didRewind = rewinding.get() && settings.rewindEnabled && core.rewind()
            updateAutofire(didRewind)
            core.runFrame(frame)
            if (!didRewind) {
                frameNumber++
                if (settings.rewindEnabled && frameNumber % settings.rewindInterval == 0L) {
                    core.captureRewind()
                }
            }
            listener?.onFrame(frame, metadata.width, metadata.height, frameNumber)
            if (!didRewind) {
                val audioFrames = core.readAudio(audio)
                if (audioFrames > 0) {
                    listener?.onAudio(
                        audio,
                        audioFrames,
                        metadata.audioSampleRate,
                        settings.audioSync && !settings.videoSync && !isFastForwarding(),
                    )
                }
            }
        } catch (error: Throwable) {
            fail(error, EmulatorFailure.EMULATION)
            return
        }

        scheduleNextFrame()
    }

    private fun updateAutofire(rewindingNow: Boolean) {
        if (rewindingNow || autofireKeys.isEmpty()) return
        val threshold = settings.autofireThreshold
        autofireFrame = (autofireFrame + 1) % (threshold * 2)
        val pressed = autofireFrame < threshold
        autofireKeys.forEach { core.setKey(it, pressed) }
    }

    private fun frameDelayNanos(): Long {
        if (!settings.videoSync) return 0L
        val ratio = when {
            fastForwardHeld.get() -> settings.fastForwardHeldRatio
            fastForwardToggled.get() -> settings.fastForwardRatio
            else -> 1f
        }
        if (ratio <= 0f) return 0L
        return (NANOS_PER_SECOND / (settings.targetFps * ratio)).toLong()
    }

    private fun scheduleNextFrame() {
        val interval = frameDelayNanos()
        val now = System.nanoTime()
        if (interval <= 0L) {
            nextFrameAtNanos = now
            lastFrameIntervalNanos = 0L
            executor.execute(::runFrame)
            return
        }
        if (interval != lastFrameIntervalNanos || nextFrameAtNanos == 0L ||
            now - nextFrameAtNanos > interval * 2) {
            nextFrameAtNanos = now
        }
        nextFrameAtNanos += interval
        lastFrameIntervalNanos = interval
        executor.schedule(::runFrame, (nextFrameAtNanos - now).coerceAtLeast(0L), TimeUnit.NANOSECONDS)
    }

    private fun autoSaveCurrentGame() {
        if (!settings.autoSaveState) return
        val path = loadedRom?.statePath ?: return
        core.saveStateFile(path, settings.saveStateFlags())
    }

    private fun isFastForwarding(): Boolean = fastForwardHeld.get() || fastForwardToggled.get()

    private fun fail(error: Throwable, failure: EmulatorFailure) {
        requestedRunning.set(false)
        frameLoopActive = false
        game = null
        frame = IntArray(0)
        core.close()
        listener?.onStateChanged(EmulatorState.Error(failure, error.message))
    }

    override fun close() {
        requestedRunning.set(false)
        listener = null
        executor.submit {
            frameLoopActive = false
            autoSaveCurrentGame()
            game = null
            core.close()
        }.get(2, TimeUnit.SECONDS)
        executor.shutdownNow()
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
