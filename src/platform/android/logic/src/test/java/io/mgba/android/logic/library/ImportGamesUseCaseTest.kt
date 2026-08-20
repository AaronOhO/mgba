/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.logic.library

import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportGamesUseCaseTest {
    @Test
    fun `summary separates new duplicate and failed imports`() = runBlocking {
        val useCase = ImportGamesUseCase { source ->
            when (source.displayName) {
                "new.gba" -> RomImportResult(game("new"), alreadyImported = false)
                "existing.gba" -> RomImportResult(game("existing"), alreadyImported = true)
                else -> error("Invalid ROM")
            }
        }

        val result = useCase(
            listOf(source("new.gba"), source("existing.gba"), source("broken.gba")),
        )

        assertEquals(RomImportSummary(imported = 1, alreadyImported = 1, failed = 1), result.summary)
        assertEquals(listOf("new", "existing"), result.games.map(LibraryGame::id))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation stops the remaining imports`() {
        runBlocking {
            val useCase = ImportGamesUseCase { throw CancellationException("Cancelled") }

            useCase(listOf(source("first.gba"), source("second.gba")))
        }
    }

    private fun source(name: String) = object : RomSource {
        override val displayName = name

        override fun openStream() = ByteArrayInputStream(byteArrayOf(1))
    }

    private fun game(id: String) = LibraryGame(
        id = id,
        name = id,
        sourceName = "$id.gba",
        romFile = File("$id.gba"),
        saveFile = File("$id.sav"),
        stateFile = File("$id.ss0"),
    )
}
