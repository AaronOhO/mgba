/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.core

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CoreConfigTest {
    @Test
    fun `native options preserve key value pairs`() {
        val config = CoreConfig(
            options = linkedMapOf("volume" to "256", "mute" to "0"),
            preloadRom = true,
            patchPath = "",
            cheatsPath = "",
            cheatAutoload = false,
            rewindEnabled = false,
            rewindCapacity = 300,
            rewindInterval = 1,
        )

        assertArrayEquals(arrayOf("volume", "256", "mute", "0"), config.nativeOptions())
    }
}
