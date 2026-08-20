/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.mgba.android.logic.library.RomSource
import java.io.InputStream

class AndroidRomSource(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : RomSource {
    override val displayName: String by lazy {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return@lazy cursor.getString(nameIndex)
        }
        uri.lastPathSegment?.substringAfterLast('/') ?: "game.gba"
    }

    override fun openStream(): InputStream =
        contentResolver.openInputStream(uri) ?: error("Could not open ROM input")
}
