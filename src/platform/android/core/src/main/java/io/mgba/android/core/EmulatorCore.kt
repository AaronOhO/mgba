/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.core

data class GameMetadata(
    val title: String,
    val width: Int,
    val height: Int,
    val audioSampleRate: Int,
    val displayName: String = "",
)

enum class EmulatorKey(val nativeCode: Int) {
    A(0),
    B(1),
    SELECT(2),
    START(3),
    RIGHT(4),
    LEFT(5),
    UP(6),
    DOWN(7),
    R(8),
    L(9),
}

interface EmulatorCore {
    fun load(romPath: String, savePath: String, config: CoreConfig): GameMetadata

    fun applyConfig(config: CoreConfig)

    fun runFrame(output: IntArray)

    fun readAudio(output: ShortArray): Int

    fun setKey(key: EmulatorKey, pressed: Boolean)

    fun clearKeys()

    fun configureRewind(enabled: Boolean, capacity: Int)

    fun captureRewind()

    fun rewind(): Boolean

    fun saveState(): ByteArray

    fun loadState(state: ByteArray): Boolean

    fun saveStateFile(path: String, flags: Int): Boolean

    fun loadStateFile(path: String, flags: Int): Boolean

    fun cloneSaveData(): ByteArray?

    fun restoreSaveData(data: ByteArray): Boolean

    fun reset()

    fun close()
}
