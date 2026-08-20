/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.mgba.android.R
import io.mgba.android.logic.emulation.EmulationSession
import io.mgba.android.logic.library.GameDataKind
import io.mgba.android.logic.library.LibraryGame
import io.mgba.android.logic.library.RomImportResult
import io.mgba.android.logic.library.RomSource
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RomRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("roms", Context.MODE_PRIVATE)
    private val database = GameLibraryDatabase(context)
    private val mutableGames = MutableStateFlow(emptyList<LibraryGame>())

    val games: StateFlow<List<LibraryGame>> = mutableGames.asStateFlow()

    init {
        migrateLegacyRom()
        refresh()
    }

    suspend fun import(source: RomSource): RomImportResult = withContext(Dispatchers.IO) {
        val sourceName = source.displayName
        require(sourceName.substringAfterLast('.', "").equals("gba", ignoreCase = true)) {
            context.getString(R.string.error_rom_type)
        }
        val romDirectory = File(context.filesDir, "roms").apply { mkdirs() }
        val saveDirectory = File(context.filesDir, "saves").apply { mkdirs() }
        val stateDirectory = File(context.filesDir, "states").apply { mkdirs() }
        val temporaryFile = File.createTempFile("import-", ".gba", context.cacheDir)
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            source.openStream().use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ROM_BYTES) { context.getString(R.string.error_rom_too_large) }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }

            require(temporaryFile.length() > 0) { context.getString(R.string.error_empty_rom) }
            val id = digest.digest().take(8).joinToString("") { "%02x".format(it) }
            database.game(id)?.takeIf { it.romFile.isFile }?.let {
                return@withContext RomImportResult(it, alreadyImported = true)
            }

            val romFile = File(romDirectory, "$id.gba")
            if (!temporaryFile.renameTo(romFile)) temporaryFile.copyTo(romFile, overwrite = true)
            val game = LibraryGame(
                id = id,
                name = sourceName.substringBeforeLast('.'),
                sourceName = sourceName,
                romFile = romFile,
                saveFile = File(saveDirectory, "$id.sav"),
                stateFile = File(stateDirectory, "$id.ss0"),
                fileSize = romFile.length(),
            )
            database.upsert(game)
            refresh()
            RomImportResult(game, alreadyImported = false)
        } finally {
            temporaryFile.delete()
        }
    }

    suspend fun markPlayed(id: String): LibraryGame? = withContext(Dispatchers.IO) {
        database.markPlayed(id, System.currentTimeMillis())
        refresh()
        database.game(id)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        database.setFavorite(id, favorite)
        refresh()
    }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { context.getString(R.string.error_empty_game_name) }
        database.rename(id, normalized)
        refresh()
    }

    suspend fun setCover(id: String, coverFile: File?) = withContext(Dispatchers.IO) {
        database.setCoverPath(id, coverFile?.absolutePath)
        refresh()
    }

    suspend fun delete(id: String, deleteSaveData: Boolean) = withContext(Dispatchers.IO) {
        val game = database.game(id) ?: return@withContext
        require(!game.romFile.exists() || game.romFile.delete()) {
            context.getString(R.string.error_delete_rom)
        }
        if (deleteSaveData) {
            game.saveFile.delete()
            deleteStateFiles(game)
        }
        game.coverFile?.delete()
        database.delete(id)
        refresh()
    }

    suspend fun readGameData(game: LibraryGame, kind: GameDataKind): ByteArray? = withContext(Dispatchers.IO) {
        game.file(kind).takeIf(File::isFile)?.readBytes()
    }

    suspend fun readDocument(uri: Uri, kind: GameDataKind): ByteArray = withContext(Dispatchers.IO) {
        val maximum = kind.maximumBytes()
        val output = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) {
                    context.getString(R.string.error_game_data_too_large)
                }
                output.write(buffer, 0, count)
            }
        } ?: error(context.getString(R.string.error_read_file))
        output.toByteArray().also { data ->
            require(data.isNotEmpty()) { context.getString(R.string.error_empty_file) }
        }
    }

    suspend fun replaceGameData(game: LibraryGame, kind: GameDataKind, data: ByteArray) =
        withContext(Dispatchers.IO) {
            require(data.isNotEmpty() && data.size <= kind.maximumBytes()) {
                context.getString(R.string.error_invalid_game_data)
            }
            val destination = game.file(kind)
            destination.parentFile?.mkdirs()
            val temporary = File.createTempFile("game-data-", ".tmp", destination.parentFile)
            try {
                temporary.writeBytes(data)
                if (!temporary.renameTo(destination)) temporary.copyTo(destination, overwrite = true)
            } finally {
                temporary.delete()
            }
        }

    suspend fun exportDocument(uri: Uri, data: ByteArray) = withContext(Dispatchers.IO) {
        require(data.isNotEmpty()) { context.getString(R.string.error_no_game_data) }
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(data)
            output.flush()
        } ?: error(context.getString(R.string.error_write_file))
    }

    suspend fun importBios(uri: Uri): File = importSupportFile(uri, "bios", "gba_bios.bin") { file ->
        require(file.length() == GBA_BIOS_SIZE) { context.getString(R.string.error_bios_size) }
    }

    suspend fun importPatch(uri: Uri): File {
        val displayName = queryDisplayName(uri)
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension in PATCH_EXTENSIONS) { context.getString(R.string.error_patch_type) }
        return importSupportFile(uri, "patches", "active.$extension")
    }

    suspend fun importCheats(uri: Uri): File = importSupportFile(uri, "cheats", "active.cheats")

    private fun refresh() {
        mutableGames.value = database.games().filter { it.romFile.isFile }
    }

    private fun migrateLegacyRom() {
        if (preferences.getBoolean(KEY_LIBRARY_MIGRATED, false)) return
        val romPath = preferences.getString(KEY_ROM_PATH, null)
        val romFile = romPath?.let(::File)?.takeIf(File::isFile)
        if (romFile != null) {
            val id = romFile.nameWithoutExtension
            val sourceName = preferences.getString(KEY_DISPLAY_NAME, null) ?: romFile.name
            val savePath = preferences.getString(KEY_SAVE_PATH, null)
                ?: File(context.filesDir, "saves/$id.sav").absolutePath
            val statePath = preferences.getString(KEY_STATE_PATH, null)
                ?: File(context.filesDir, "states/$id.ss0").absolutePath
            database.upsert(
                LibraryGame(
                    id = id,
                    name = sourceName.substringBeforeLast('.'),
                    sourceName = sourceName,
                    romFile = romFile,
                    saveFile = File(savePath),
                    stateFile = File(statePath),
                    importedAt = romFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                ),
            )
        }
        preferences.edit().putBoolean(KEY_LIBRARY_MIGRATED, true).apply()
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "game.gba"
    }

    private fun LibraryGame.file(kind: GameDataKind): File = when (kind) {
        GameDataKind.BATTERY_SAVE -> saveFile
        GameDataKind.QUICK_STATE -> stateFile
    }

    private fun deleteStateFiles(game: LibraryGame) {
        game.stateFile.delete()
        val directory = game.stateFile.parentFile ?: return
        val baseName = game.stateFile.nameWithoutExtension
        (1..EmulationSession.MAX_STATE_SLOT).forEach { slot ->
            File(directory, "$baseName.ss$slot").delete()
        }
    }

    private fun GameDataKind.maximumBytes(): Int = when (this) {
        GameDataKind.BATTERY_SAVE -> 1024 * 1024
        GameDataKind.QUICK_STATE -> 32 * 1024 * 1024
    }

    private suspend fun importSupportFile(
        uri: Uri,
        directoryName: String,
        fileName: String,
        validate: (File) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, directoryName).apply { mkdirs() }
        val temporary = File.createTempFile("import-", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary).use(input::copyTo)
            } ?: error(context.getString(R.string.error_read_file))
            require(temporary.length() > 0) { context.getString(R.string.error_empty_file) }
            validate(temporary)
            val destination = File(directory, fileName)
            temporary.copyTo(destination, overwrite = true)
            destination
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_ROM_PATH = "rom_path"
        const val KEY_SAVE_PATH = "save_path"
        const val KEY_STATE_PATH = "state_path"
        const val KEY_LIBRARY_MIGRATED = "library_migrated"
        const val GBA_BIOS_SIZE = 16_384L
        const val MAX_ROM_BYTES = 64L * 1024L * 1024L
        val PATCH_EXTENSIONS = setOf("ips", "ups", "bps")
    }
}
