/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.logic.settings

import io.mgba.android.core.EmulatorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorSettingsTest {
    @Test
    fun `player uses immersive mode by default`() {
        assertTrue(EmulatorSettings().fullscreenPlayer)
    }

    @Test
    fun `normalization keeps values inside supported ranges`() {
        val settings = EmulatorSettings(
            volume = 999,
            sampleRate = 100,
            frameSkip = -5,
            fastForwardRatio = 100f,
            rewindInterval = 0,
        ).normalized()

        assertEquals(0x100, settings.volume)
        assertEquals(8_000, settings.sampleRate)
        assertEquals(0, settings.frameSkip)
        assertEquals(16f, settings.fastForwardRatio)
        assertEquals(1, settings.rewindInterval)
    }

    @Test
    fun `non-positive fast forward ratio means unbounded`() {
        assertEquals(-1f, EmulatorSettings(fastForwardRatio = 0f).normalized().fastForwardRatio)
        assertEquals(-1f, EmulatorSettings(fastForwardHeldRatio = 0f).normalized().fastForwardHeldRatio)
    }

    @Test
    fun `rebinding an occupied physical key swaps the two bindings`() {
        val original = EmulatorSettings(
            inputBindings = mapOf(EmulatorKey.A to 100, EmulatorKey.B to 101),
        )
        val rebound = original.withInputBinding(EmulatorKey.B, 100)

        assertEquals(100, rebound.inputBindings.getValue(EmulatorKey.B))
        assertEquals(101, rebound.inputBindings.getValue(EmulatorKey.A))
    }

    @Test
    fun `save state flags match Qt defaults and configurable load data`() {
        assertEquals(31, EmulatorSettings().saveStateFlags())
        assertEquals(9, EmulatorSettings().loadStateFlags())
        assertEquals(
            15,
            EmulatorSettings(loadStateSaveData = true, loadStateCheats = true).loadStateFlags(),
        )
    }
}
