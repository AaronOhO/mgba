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
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredRom(
    val displayName: String,
    val romFile: File,
    val saveFile: File,
    val stateFile: File,
)

class RomRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("roms", Context.MODE_PRIVATE)

    suspend fun import(uri: Uri): StoredRom = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val romDirectory = File(context.filesDir, "roms").apply { mkdirs() }
        val saveDirectory = File(context.filesDir, "saves").apply { mkdirs() }
        val stateDirectory = File(context.filesDir, "states").apply { mkdirs() }
        val temporaryFile = File.createTempFile("import-", ".gba", context.cacheDir)
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error(context.getString(R.string.error_read_rom))

            require(temporaryFile.length() > 0) { context.getString(R.string.error_empty_rom) }
            val id = digest.digest().take(8).joinToString("") { "%02x".format(it) }
            val romFile = File(romDirectory, "$id.gba")
            if (!temporaryFile.renameTo(romFile)) {
                temporaryFile.copyTo(romFile, overwrite = true)
            }
            val storedRom = StoredRom(
                displayName,
                romFile,
                File(saveDirectory, "$id.sav"),
                File(stateDirectory, "$id.ss0"),
            )
            saveLastRom(storedRom)
            storedRom
        } finally {
            temporaryFile.delete()
        }
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

    fun lastRom(): StoredRom? {
        val romPath = preferences.getString(KEY_ROM_PATH, null) ?: return null
        val romFile = File(romPath)
        if (!romFile.isFile) return null
        val savePath = preferences.getString(KEY_SAVE_PATH, null) ?: return null
        val statePath = preferences.getString(KEY_STATE_PATH, null)
            ?: File(context.filesDir, "states/${romFile.nameWithoutExtension}.ss0").absolutePath
        val stateFile = File(statePath).also { it.parentFile?.mkdirs() }
        val displayName = preferences.getString(KEY_DISPLAY_NAME, null) ?: romFile.name
        return StoredRom(displayName, romFile, File(savePath), stateFile)
    }

    private fun saveLastRom(rom: StoredRom) {
        preferences.edit()
            .putString(KEY_DISPLAY_NAME, rom.displayName)
            .putString(KEY_ROM_PATH, rom.romFile.absolutePath)
            .putString(KEY_SAVE_PATH, rom.saveFile.absolutePath)
            .putString(KEY_STATE_PATH, rom.stateFile.absolutePath)
            .apply()
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "game.gba"
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
        const val GBA_BIOS_SIZE = 16_384L
        val PATCH_EXTENSIONS = setOf("ips", "ups", "bps")
    }
}
