/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.data

import io.mgba.android.logic.library.RomSource
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipFile

internal class RomSourceExpansion(
    val sources: List<RomSource>,
    val failedDocuments: Int,
    private val cachedArchives: List<File>,
) : Closeable {
    override fun close() {
        cachedArchives.forEach(File::delete)
    }
}

internal class RomSourceExpander(private val cacheDirectory: File) {
    fun expand(sources: List<RomSource>): RomSourceExpansion {
        cacheDirectory.mkdirs()
        val expanded = mutableListOf<RomSource>()
        val cachedArchives = mutableListOf<File>()
        var failedDocuments = 0

        sources.forEach { source ->
            runCatching { expand(source, cachedArchives) }
                .onSuccess { expanded += it }
                .onFailure { failedDocuments++ }
        }
        return RomSourceExpansion(expanded, failedDocuments, cachedArchives)
    }

    private fun expand(source: RomSource, cachedArchives: MutableList<File>): List<RomSource> =
        when (source.displayName.substringAfterLast('.', "").lowercase()) {
            "gba" -> listOf(source)
            "zip" -> {
                val archiveFile = cacheArchive(source)
                try {
                    listArchive(archiveFile).also { cachedArchives += archiveFile }
                } catch (exception: Exception) {
                    archiveFile.delete()
                    throw exception
                }
            }
            else -> error("Only .gba ROMs and .zip archives are supported")
        }

    private fun cacheArchive(source: RomSource): File {
        val destination = File.createTempFile("rom-archive-", ".zip", cacheDirectory)
        try {
            source.openStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ARCHIVE_BYTES) { "ZIP archive exceeds the import limit" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            return destination
        } catch (exception: Exception) {
            destination.delete()
            throw exception
        }
    }

    private fun listArchive(archiveFile: File): List<RomSource> = ZipFile(archiveFile).use { archive ->
        val roms = mutableListOf<RomSource>()
        val romEntryNames = mutableSetOf<String>()
        val entries = archive.entries()
        var entryCount = 0
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            entryCount++
            require(entryCount <= MAX_ARCHIVE_ENTRIES) { "ZIP archive contains too many files" }
            if (!entry.isDirectory && entry.name.substringAfterLast('.', "").equals("gba", true)) {
                require(romEntryNames.add(entry.name)) { "ZIP archive contains duplicate ROM entries" }
                roms += ZipRomSource(archiveFile, entry.name)
            }
        }
        require(roms.isNotEmpty()) { "ZIP archive does not contain a .gba ROM" }
        roms
    }

    private class ZipRomSource(
        private val archiveFile: File,
        private val entryName: String,
    ) : RomSource {
        override val displayName: String = entryName.substringAfterLast('/').ifEmpty { "game.gba" }

        override fun openStream(): InputStream {
            val archive = ZipFile(archiveFile)
            return try {
                val entry = archive.getEntry(entryName) ?: error("ROM entry is missing from ZIP archive")
                object : FilterInputStream(archive.getInputStream(entry)) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            archive.close()
                        }
                    }
                }
            } catch (exception: Exception) {
                archive.close()
                throw exception
            }
        }
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L
        const val MAX_ARCHIVE_ENTRIES = 4096
    }
}
