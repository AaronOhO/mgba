/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAudioSinkTest {
    @Test
    fun `buffer size is at least 100ms and aligned to a stereo PCM frame`() {
        assertEquals(13108, audioBufferSize(minimumSize = 7168, sampleRate = 32768))
    }

    @Test
    fun `larger platform minimum is preserved and aligned`() {
        assertEquals(16384, audioBufferSize(minimumSize = 16384, sampleRate = 32768))
    }
}
