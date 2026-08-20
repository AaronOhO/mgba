/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverNameResolverTest {
    @Test
    fun usesOriginalRomNameBeforeEditableDisplayName() {
        assertEquals(
            listOf("Advance Wars (USA)", "Advance Wars"),
            CoverNameResolver.candidates("Advance Wars (USA).gba", "Advance Wars"),
        )
    }

    @Test
    fun removesDuplicateCandidates() {
        assertEquals(
            listOf("Mario Kart - Super Circuit (USA)"),
            CoverNameResolver.candidates(
                "Mario Kart - Super Circuit (USA).gba",
                "Mario Kart - Super Circuit (USA)",
            ),
        )
    }

    @Test
    fun preservesPeriodsInEditableDisplayName() {
        assertEquals(
            listOf("unknown", "Dr. Mario"),
            CoverNameResolver.candidates("unknown.gba", "Dr. Mario"),
        )
    }

    @Test
    fun percentEncodesCoverFileNameAsOnePathSegment() {
        assertEquals(
            CoverNameResolver.BASE_URL + "Pokemon%20-%20Emerald%20Version%20%28USA%2C%20Europe%29.png",
            CoverNameResolver.url("Pokemon - Emerald Version (USA, Europe)").toString(),
        )
    }
}
