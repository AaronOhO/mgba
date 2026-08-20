/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.mgba.android.logic.library.LibraryGame
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CoverRepository(context: Context) {
    private val contentResolver = context.contentResolver
    private val coverDirectory = File(context.filesDir, "covers").apply { mkdirs() }
    private val operationMutex = Mutex()

    suspend fun fetch(game: LibraryGame, force: Boolean = false): File? = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val destination = File(coverDirectory, "${game.id}.png")
            if (force) destination.delete()
            if (destination.isValidImage()) return@withLock destination
            CoverNameResolver.candidates(game.sourceName, game.name).firstNotNullOfOrNull { candidate ->
                download(CoverNameResolver.url(candidate), destination)
            }
        }
    }

    suspend fun delete(gameId: String) = withContext(Dispatchers.IO) {
        operationMutex.withLock { File(coverDirectory, "$gameId.png").delete() }
    }

    suspend fun importCover(game: LibraryGame, uri: Uri): File = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val destination = File(coverDirectory, "${game.id}.png")
            contentResolver.openInputStream(uri)?.use { input -> install(input, destination) }
                ?: error("The selected cover image is invalid or too large")
        }
    }

    private fun download(url: URL, destination: File): File? {
        return try {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                if (!connection.contentType.orEmpty().startsWith("image/")) return null
                val contentLength = connection.contentLength.toLong()
                if (contentLength > MAX_COVER_BYTES) return null
                install(connection.inputStream, destination)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun install(input: InputStream, destination: File): File? {
        val temporary = File.createTempFile("cover-", ".tmp", coverDirectory)
        return try {
            BufferedInputStream(input).use { buffered ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = buffered.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_COVER_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!temporary.isValidImage()) return null
            if (!temporary.renameTo(destination)) temporary.copyTo(destination, overwrite = true)
            destination.takeIf { it.isValidImage() }
        } finally {
            temporary.delete()
        }
    }

    private fun File.isValidImage(): Boolean {
        if (!isFile || length() !in 1..MAX_COVER_BYTES) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 12_000
        const val MAX_COVER_BYTES = 5L * 1024L * 1024L
        const val USER_AGENT = "mGBA Android/0.1"
    }
}

internal object CoverNameResolver {
    const val BASE_URL =
        "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/"

    fun candidates(sourceName: String, displayName: String): List<String> = listOf(
        sourceName.withoutGbaExtension(),
        displayName.withoutGbaExtension(),
    ).map(String::trim).filter(String::isNotEmpty).distinct()

    fun url(name: String): URL {
        val encoded = URLEncoder.encode("$name.png", Charsets.UTF_8.name()).replace("+", "%20")
        return URL(BASE_URL + encoded)
    }

    private fun String.withoutGbaExtension(): String =
        if (endsWith(".gba", ignoreCase = true)) dropLast(4) else this
}
