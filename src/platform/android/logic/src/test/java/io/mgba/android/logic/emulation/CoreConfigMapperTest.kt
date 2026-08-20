/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.logic.emulation

import io.mgba.android.logic.settings.EmulatorSettings
import io.mgba.android.logic.settings.IdleOptimization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreConfigMapperTest {
    @Test
    fun `Qt compatible GBA options are mapped to native configuration`() {
        val config = EmulatorSettings(
            volume = 128,
            muted = true,
            frameSkip = 2,
            biosPath = "/bios/gba.bin",
            idleOptimization = IdleOptimization.REMOVE,
            logToFile = true,
        ).toCoreConfig("/logs/mgba.log")

        assertEquals("128", config.options.getValue("volume"))
        assertEquals("1", config.options.getValue("mute"))
        assertEquals("2", config.options.getValue("frameskip"))
        assertEquals("/bios/gba.bin", config.options.getValue("gba.bios"))
        assertEquals("remove", config.options.getValue("idleOptimization"))
        assertEquals("1", config.options.getValue("logToFile"))
        assertEquals("/logs/mgba.log", config.options.getValue("logFile"))
    }

    @Test
    fun `only native load boundaries require a core rebuild`() {
        val defaults = EmulatorSettings()

        assertFalse(defaults.copy(volume = 64).requiresCoreReload(defaults))
        assertFalse(defaults.copy(shaderId = "lcd").requiresCoreReload(defaults))
        assertTrue(defaults.copy(sampleRate = 48_000).requiresCoreReload(defaults))
        assertTrue(defaults.copy(patchPath = "/patch/game.ips").requiresCoreReload(defaults))
    }
}
