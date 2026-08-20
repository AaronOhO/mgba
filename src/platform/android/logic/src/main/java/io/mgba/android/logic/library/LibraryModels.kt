/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.logic.library

import java.io.File

data class LibraryGame(
    val id: String,
    val name: String,
    val sourceName: String,
    val romFile: File,
    val saveFile: File,
    val stateFile: File,
    val coverFile: File? = null,
    val favorite: Boolean = false,
    val importedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0,
    val fileSize: Long = romFile.length(),
)

data class RomImportResult(val game: LibraryGame, val alreadyImported: Boolean)

enum class GameDataKind(val extension: String) {
    BATTERY_SAVE("sav"),
    QUICK_STATE("ss0"),
}
