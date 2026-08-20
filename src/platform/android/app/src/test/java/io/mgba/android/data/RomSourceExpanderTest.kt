/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.data

import io.mgba.android.logic.library.RomSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RomSourceExpanderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `expands every GBA entry and ignores unrelated archive files`() {
        val cache = temporaryFolder.newFolder("cache")
        val firstRom = byteArrayOf(1, 2, 3)
        val secondRom = byteArrayOf(4, 5, 6)
        val archive = source(
            "Collection.ZIP",
            zip(
                "readme.txt" to byteArrayOf(0),
                "nested/Game One.GBA" to firstRom,
                "Game Two.gba" to secondRom,
            ),
        )

        RomSourceExpander(cache).expand(listOf(archive)).use { expansion ->
            assertEquals(0, expansion.failedDocuments)
            assertEquals(listOf("Game One.GBA", "Game Two.gba"), expansion.sources.map { it.displayName })
            assertArrayEquals(firstRom, expansion.sources[0].openStream().use { it.readBytes() })
            assertArrayEquals(secondRom, expansion.sources[1].openStream().use { it.readBytes() })
            assertEquals(1, cache.listFiles()?.size)
        }

        assertEquals(0, cache.listFiles()?.size)
    }

    @Test
    fun `keeps standalone ROMs and isolates invalid selected documents`() {
        val cache = temporaryFolder.newFolder("cache")
        val standalone = source("Homebrew.GBA", byteArrayOf(7))
        val noRomArchive = source("manuals.zip", zip("manual.txt" to byteArrayOf(8)))
        val expansion = RomSourceExpander(cache).expand(
            listOf(
                standalone,
                source("notes.txt", byteArrayOf(9)),
                source("broken.zip", byteArrayOf(10)),
                noRomArchive,
            ),
        )

        expansion.use {
            assertEquals(3, it.failedDocuments)
            assertEquals(1, it.sources.size)
            assertSame(standalone, it.sources.single())
        }
        assertEquals(0, cache.listFiles()?.size)
    }

    private fun source(name: String, data: ByteArray) = object : RomSource {
        override val displayName = name

        override fun openStream() = ByteArrayInputStream(data)
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { archive ->
                entries.forEach { (name, data) ->
                    archive.putNextEntry(ZipEntry(name))
                    archive.write(data)
                    archive.closeEntry()
                }
            }
            bytes.toByteArray()
        }
}
