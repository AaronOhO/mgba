/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.mgba.android.logic.library.LibraryGame
import java.io.File

internal class GameLibraryDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_GAMES (
                $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_SOURCE_NAME TEXT NOT NULL,
                $COLUMN_ROM_PATH TEXT NOT NULL,
                $COLUMN_SAVE_PATH TEXT NOT NULL,
                $COLUMN_STATE_PATH TEXT NOT NULL,
                $COLUMN_COVER_PATH TEXT,
                $COLUMN_FAVORITE INTEGER NOT NULL DEFAULT 0,
                $COLUMN_IMPORTED_AT INTEGER NOT NULL,
                $COLUMN_LAST_PLAYED_AT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_PLAY_COUNT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_FILE_SIZE INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun games(): List<LibraryGame> = readableDatabase.query(
        TABLE_GAMES,
        COLUMNS,
        null,
        null,
        null,
        null,
        "$COLUMN_FAVORITE DESC, $COLUMN_LAST_PLAYED_AT DESC, " +
            "$COLUMN_IMPORTED_AT DESC, $COLUMN_NAME COLLATE NOCASE",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toGame())
        }
    }

    fun game(id: String): LibraryGame? = readableDatabase.query(
        TABLE_GAMES,
        COLUMNS,
        "$COLUMN_ID = ?",
        arrayOf(id),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toGame() else null
    }

    fun upsert(game: LibraryGame) {
        writableDatabase.insertWithOnConflict(
            TABLE_GAMES,
            null,
            game.values(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun rename(id: String, name: String) {
        update(id, ContentValues().apply { put(COLUMN_NAME, name) })
    }

    fun setFavorite(id: String, favorite: Boolean) {
        update(id, ContentValues().apply { put(COLUMN_FAVORITE, favorite) })
    }

    fun markPlayed(id: String, playedAt: Long) {
        writableDatabase.execSQL(
            """
            UPDATE $TABLE_GAMES
            SET $COLUMN_LAST_PLAYED_AT = ?, $COLUMN_PLAY_COUNT = $COLUMN_PLAY_COUNT + 1
            WHERE $COLUMN_ID = ?
            """.trimIndent(),
            arrayOf(playedAt, id),
        )
    }

    fun setCoverPath(id: String, path: String?) {
        update(id, ContentValues().apply { put(COLUMN_COVER_PATH, path) })
    }

    fun delete(id: String) {
        writableDatabase.delete(TABLE_GAMES, "$COLUMN_ID = ?", arrayOf(id))
    }

    private fun update(id: String, values: ContentValues) {
        writableDatabase.update(TABLE_GAMES, values, "$COLUMN_ID = ?", arrayOf(id))
    }

    private fun LibraryGame.values(): ContentValues = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_NAME, name)
        put(COLUMN_SOURCE_NAME, sourceName)
        put(COLUMN_ROM_PATH, romFile.absolutePath)
        put(COLUMN_SAVE_PATH, saveFile.absolutePath)
        put(COLUMN_STATE_PATH, stateFile.absolutePath)
        put(COLUMN_COVER_PATH, coverFile?.absolutePath)
        put(COLUMN_FAVORITE, favorite)
        put(COLUMN_IMPORTED_AT, importedAt)
        put(COLUMN_LAST_PLAYED_AT, lastPlayedAt)
        put(COLUMN_PLAY_COUNT, playCount)
        put(COLUMN_FILE_SIZE, fileSize)
    }

    private fun Cursor.toGame(): LibraryGame = LibraryGame(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        name = getString(getColumnIndexOrThrow(COLUMN_NAME)),
        sourceName = getString(getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
        romFile = File(getString(getColumnIndexOrThrow(COLUMN_ROM_PATH))),
        saveFile = File(getString(getColumnIndexOrThrow(COLUMN_SAVE_PATH))),
        stateFile = File(getString(getColumnIndexOrThrow(COLUMN_STATE_PATH))),
        coverFile = getString(getColumnIndexOrThrow(COLUMN_COVER_PATH))?.let(::File)?.takeIf(File::isFile),
        favorite = getInt(getColumnIndexOrThrow(COLUMN_FAVORITE)) != 0,
        importedAt = getLong(getColumnIndexOrThrow(COLUMN_IMPORTED_AT)),
        lastPlayedAt = getLong(getColumnIndexOrThrow(COLUMN_LAST_PLAYED_AT)),
        playCount = getInt(getColumnIndexOrThrow(COLUMN_PLAY_COUNT)),
        fileSize = getLong(getColumnIndexOrThrow(COLUMN_FILE_SIZE)),
    )

    private companion object {
        const val DATABASE_NAME = "game-library.db"
        const val DATABASE_VERSION = 1
        const val TABLE_GAMES = "games"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_SOURCE_NAME = "source_name"
        const val COLUMN_ROM_PATH = "rom_path"
        const val COLUMN_SAVE_PATH = "save_path"
        const val COLUMN_STATE_PATH = "state_path"
        const val COLUMN_COVER_PATH = "cover_path"
        const val COLUMN_FAVORITE = "favorite"
        const val COLUMN_IMPORTED_AT = "imported_at"
        const val COLUMN_LAST_PLAYED_AT = "last_played_at"
        const val COLUMN_PLAY_COUNT = "play_count"
        const val COLUMN_FILE_SIZE = "file_size"
        val COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_NAME,
            COLUMN_SOURCE_NAME,
            COLUMN_ROM_PATH,
            COLUMN_SAVE_PATH,
            COLUMN_STATE_PATH,
            COLUMN_COVER_PATH,
            COLUMN_FAVORITE,
            COLUMN_IMPORTED_AT,
            COLUMN_LAST_PLAYED_AT,
            COLUMN_PLAY_COUNT,
            COLUMN_FILE_SIZE,
        )
    }
}
