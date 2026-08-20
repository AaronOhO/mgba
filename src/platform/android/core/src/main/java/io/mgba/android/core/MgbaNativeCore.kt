/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.core

class MgbaNativeCore : EmulatorCore {
    private var handle = 0L

    override fun load(romPath: String, savePath: String, config: CoreConfig): GameMetadata {
        close()
        handle = nativeOpen(
            romPath,
            savePath,
            config.nativeOptions(),
            config.preloadRom,
            config.patchPath,
            config.cheatsPath.takeIf { config.cheatAutoload },
        )
        check(handle != 0L) { "mGBA did not create a core" }
        nativeConfigureRewind(handle, config.rewindEnabled, config.rewindCapacity)
        return GameMetadata(
            title = nativeTitle(handle),
            width = nativeWidth(handle),
            height = nativeHeight(handle),
            audioSampleRate = nativeAudioSampleRate(handle),
        )
    }

    override fun applyConfig(config: CoreConfig) {
        checkLoaded()
        nativeApplyConfig(handle, config.nativeOptions())
        nativeConfigureRewind(handle, config.rewindEnabled, config.rewindCapacity)
    }

    override fun runFrame(output: IntArray) {
        checkLoaded()
        nativeRunFrame(handle, output)
    }

    override fun readAudio(output: ShortArray): Int {
        checkLoaded()
        return nativeReadAudio(handle, output)
    }

    override fun setKey(key: EmulatorKey, pressed: Boolean) {
        checkLoaded()
        nativeSetKey(handle, key.nativeCode, pressed)
    }

    override fun clearKeys() {
        if (handle != 0L) {
            nativeClearKeys(handle)
        }
    }

    override fun configureRewind(enabled: Boolean, capacity: Int) {
        if (handle != 0L) nativeConfigureRewind(handle, enabled, capacity)
    }

    override fun captureRewind() {
        if (handle != 0L) nativeCaptureRewind(handle)
    }

    override fun rewind(): Boolean = handle != 0L && nativeRewind(handle)

    override fun saveState(): ByteArray {
        checkLoaded()
        return nativeSaveState(handle)
    }

    override fun loadState(state: ByteArray): Boolean {
        checkLoaded()
        return nativeLoadState(handle, state)
    }

    override fun saveStateFile(path: String, flags: Int): Boolean {
        checkLoaded()
        return nativeSaveStateFile(handle, path, flags)
    }

    override fun loadStateFile(path: String, flags: Int): Boolean {
        checkLoaded()
        return nativeLoadStateFile(handle, path, flags)
    }

    override fun cloneSaveData(): ByteArray? {
        checkLoaded()
        return nativeCloneSaveData(handle)
    }

    override fun restoreSaveData(data: ByteArray): Boolean {
        checkLoaded()
        return nativeRestoreSaveData(handle, data)
    }

    override fun reset() {
        checkLoaded()
        nativeReset(handle)
    }

    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private fun checkLoaded() {
        check(handle != 0L) { "No ROM is loaded" }
    }

    private external fun nativeOpen(
        romPath: String,
        savePath: String,
        options: Array<String>,
        preloadRom: Boolean,
        patchPath: String,
        cheatsPath: String?,
    ): Long

    private external fun nativeApplyConfig(handle: Long, options: Array<String>)

    private external fun nativeTitle(handle: Long): String

    private external fun nativeWidth(handle: Long): Int

    private external fun nativeHeight(handle: Long): Int

    private external fun nativeAudioSampleRate(handle: Long): Int

    private external fun nativeRunFrame(handle: Long, output: IntArray)

    private external fun nativeReadAudio(handle: Long, output: ShortArray): Int

    private external fun nativeSetKey(handle: Long, key: Int, pressed: Boolean)

    private external fun nativeClearKeys(handle: Long)

    private external fun nativeConfigureRewind(handle: Long, enabled: Boolean, capacity: Int)

    private external fun nativeCaptureRewind(handle: Long)

    private external fun nativeRewind(handle: Long): Boolean

    private external fun nativeSaveState(handle: Long): ByteArray

    private external fun nativeLoadState(handle: Long, state: ByteArray): Boolean

    private external fun nativeSaveStateFile(handle: Long, path: String, flags: Int): Boolean

    private external fun nativeLoadStateFile(handle: Long, path: String, flags: Int): Boolean

    private external fun nativeCloneSaveData(handle: Long): ByteArray?

    private external fun nativeRestoreSaveData(handle: Long, data: ByteArray): Boolean

    private external fun nativeReset(handle: Long)

    private external fun nativeClose(handle: Long)

    companion object {
        init {
            System.loadLibrary("mgba-android")
        }
    }
}
