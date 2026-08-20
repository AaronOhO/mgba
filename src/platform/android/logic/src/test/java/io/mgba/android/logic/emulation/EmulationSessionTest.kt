/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.logic.emulation

import io.mgba.android.core.CoreConfig
import io.mgba.android.core.EmulatorCore
import io.mgba.android.core.EmulatorKey
import io.mgba.android.core.GameMetadata
import io.mgba.android.logic.settings.EmulatorSettings
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EmulationSessionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `loaded core produces frames while resumed`() {
        val core = FakeCore()
        val session = EmulationSession(core, EmulatorSettings(targetFps = 200f))
        val runningState = AtomicReference<EmulatorState.Running>()
        val frame = AtomicReference<IntArray>()
        val frameReady = CountDownLatch(1)
        session.listener = object : EmulationSession.Listener {
            override fun onStateChanged(state: EmulatorState) {
                if (state is EmulatorState.Running) runningState.set(state)
            }

            override fun onFrame(pixels: IntArray, width: Int, height: Int, frameNumber: Long) {
                frame.set(pixels.copyOf())
                frameReady.countDown()
            }

            override fun onAudio(samples: ShortArray, frameCount: Int, sampleRate: Int, synchronize: Boolean) = Unit
        }

        try {
            session.resume()
            session.loadRom("game.gba", "game.sav", "game.ss0", "game.gba")

            assertTrue("A frame should be produced", frameReady.await(1, TimeUnit.SECONDS))
            assertEquals("Test Game", runningState.get().game.title)
            assertArrayEquals(intArrayOf(1, 2, 3, 4), frame.get())
        } finally {
            session.close()
        }
    }

    @Test
    fun `input is serialized onto the emulation thread`() {
        val core = FakeCore()
        val session = EmulationSession(core)
        val loaded = CountDownLatch(1)
        session.listener = object : EmulationSession.Listener {
            override fun onStateChanged(state: EmulatorState) {
                if (state is EmulatorState.Paused) loaded.countDown()
            }

            override fun onFrame(pixels: IntArray, width: Int, height: Int, frameNumber: Long) = Unit

            override fun onAudio(samples: ShortArray, frameCount: Int, sampleRate: Int, synchronize: Boolean) = Unit
        }

        try {
            session.loadRom("game.gba", "game.sav", "game.ss0", "game.gba")
            assertTrue("ROM should load", loaded.await(1, TimeUnit.SECONDS))
            session.setKey(EmulatorKey.A, true)
            assertTrue("Key should reach the core", core.keyChanged.await(1, TimeUnit.SECONDS))
            assertEquals(EmulatorKey.A to true, core.lastKey.get())
        } finally {
            session.close()
        }
    }

    @Test
    fun `key ABI matches mGBA GBAKey values`() {
        assertEquals((0..9).toList(), EmulatorKey.values().map(EmulatorKey::nativeCode))
    }

    @Test
    fun `battery save transfer is serialized through the core`() {
        val session = EmulationSession(FakeCore())
        val loaded = CountDownLatch(1)
        session.listener = object : EmulationSession.Listener {
            override fun onStateChanged(state: EmulatorState) {
                if (state is EmulatorState.Paused) loaded.countDown()
            }

            override fun onFrame(pixels: IntArray, width: Int, height: Int, frameNumber: Long) = Unit

            override fun onAudio(samples: ShortArray, frameCount: Int, sampleRate: Int, synchronize: Boolean) = Unit
        }

        try {
            session.loadRom("game.gba", "game.sav", "game.ss0", "game.gba")
            assertTrue("ROM should load", loaded.await(1, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(2, 3), session.cloneBatterySave())
            assertTrue(session.restoreBatterySave(byteArrayOf(4)))
        } finally {
            session.close()
        }
    }

    @Test
    fun `numbered save slots use independent state files`() {
        val core = FakeCore()
        val session = EmulationSession(core)
        val loaded = CountDownLatch(1)
        val stateDirectory = temporaryFolder.newFolder("states")
        val quickState = File(stateDirectory, "game.ss0")
        File(stateDirectory, "game.ss3").writeBytes(byteArrayOf(1))
        session.listener = object : EmulationSession.Listener {
            override fun onStateChanged(state: EmulatorState) {
                if (state is EmulatorState.Paused) loaded.countDown()
            }

            override fun onFrame(pixels: IntArray, width: Int, height: Int, frameNumber: Long) = Unit

            override fun onAudio(samples: ShortArray, frameCount: Int, sampleRate: Int, synchronize: Boolean) = Unit
        }

        try {
            session.loadRom("game.gba", "game.sav", quickState.path, "game.gba")
            assertTrue("ROM should load", loaded.await(1, TimeUnit.SECONDS))
            session.quickSave(3)
            assertTrue("State should save", core.stateSaved.await(1, TimeUnit.SECONDS))
            session.quickLoad(3)
            assertTrue("State should load", core.stateLoaded.await(1, TimeUnit.SECONDS))
            assertEquals(File(stateDirectory, "game.ss3").path, core.savedStatePath.get())
            assertEquals(File(stateDirectory, "game.ss3").path, core.loadedStatePath.get())
        } finally {
            session.close()
        }
    }

    private class FakeCore : EmulatorCore {
        val keyChanged = CountDownLatch(1)
        val lastKey = AtomicReference<Pair<EmulatorKey, Boolean>>()
        val stateSaved = CountDownLatch(1)
        val stateLoaded = CountDownLatch(1)
        val savedStatePath = AtomicReference<String>()
        val loadedStatePath = AtomicReference<String>()

        override fun load(romPath: String, savePath: String, config: CoreConfig) = GameMetadata(
            title = "Test Game",
            width = 2,
            height = 2,
            audioSampleRate = 32768,
        )

        override fun applyConfig(config: CoreConfig) = Unit

        override fun runFrame(output: IntArray) {
            intArrayOf(1, 2, 3, 4).copyInto(output)
        }

        override fun readAudio(output: ShortArray): Int = 0

        override fun setKey(key: EmulatorKey, pressed: Boolean) {
            lastKey.set(key to pressed)
            keyChanged.countDown()
        }

        override fun clearKeys() = Unit

        override fun configureRewind(enabled: Boolean, capacity: Int) = Unit

        override fun captureRewind() = Unit

        override fun rewind(): Boolean = false

        override fun saveState(): ByteArray = byteArrayOf(1)

        override fun loadState(state: ByteArray): Boolean = true

        override fun saveStateFile(path: String, flags: Int): Boolean {
            savedStatePath.set(path)
            stateSaved.countDown()
            return true
        }

        override fun loadStateFile(path: String, flags: Int): Boolean {
            loadedStatePath.set(path)
            stateLoaded.countDown()
            return true
        }

        override fun cloneSaveData(): ByteArray = byteArrayOf(2, 3)

        override fun restoreSaveData(data: ByteArray): Boolean = data.isNotEmpty()

        override fun reset() = Unit

        override fun close() = Unit
    }
}
