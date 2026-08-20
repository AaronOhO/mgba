/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.logic.library

import java.io.InputStream
import kotlinx.coroutines.CancellationException

interface RomSource {
    val displayName: String

    fun openStream(): InputStream
}

data class RomImportSummary(
    val imported: Int,
    val alreadyImported: Int,
    val failed: Int,
)

data class RomImportBatchResult(
    val summary: RomImportSummary,
    val games: List<LibraryGame>,
)

class ImportGamesUseCase(
    private val importGame: suspend (RomSource) -> RomImportResult,
) {
    suspend operator fun invoke(sources: List<RomSource>): RomImportBatchResult {
        var imported = 0
        var alreadyImported = 0
        var failed = 0
        val games = mutableListOf<LibraryGame>()
        sources.forEach { source ->
            try {
                val result = importGame(source)
                games += result.game
                if (result.alreadyImported) alreadyImported++ else imported++
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                failed++
            }
        }
        return RomImportBatchResult(
            summary = RomImportSummary(imported, alreadyImported, failed),
            games = games,
        )
    }
}
