/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android

import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.mgba.android.logic.library.GameDataKind
import io.mgba.android.logic.library.LibraryGame
import io.mgba.android.ui.EmulatorViewModel
import io.mgba.android.ui.MgbaApp

class MainActivity : ComponentActivity() {
    private data class GameDataRequest(val gameId: String, val kind: GameDataKind)

    private val viewModel by viewModels<EmulatorViewModel> {
        EmulatorViewModel.Factory(application)
    }

    private val romPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importRoms(uris)
    }

    private val biosPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBios)
    }

    private val patchPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPatch)
    }

    private val cheatsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCheats)
    }

    private var pendingCoverGameId: String? = null
    private var pendingGameDataImport: GameDataRequest? = null
    private var pendingGameDataExport: GameDataRequest? = null

    private val coverPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val gameId = pendingCoverGameId
        pendingCoverGameId = null
        if (uri != null && gameId != null) viewModel.importCover(gameId, uri)
    }

    private val gameDataImportPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pendingGameDataImport
        pendingGameDataImport = null
        if (uri != null && request != null) viewModel.importGameData(request.gameId, request.kind, uri)
    }

    private val gameDataExportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val request = pendingGameDataExport
        pendingGameDataExport = null
        if (uri != null && request != null) viewModel.exportGameData(request.gameId, request.kind, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MgbaApp(
                viewModel = viewModel,
                onChooseRom = { romPicker.launch(arrayOf("*/*")) },
                onChooseBios = { biosPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onChoosePatch = { patchPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onChooseCheats = { cheatsPicker.launch(arrayOf("text/plain", "*/*")) },
                onChooseCover = { game ->
                    pendingCoverGameId = game.id
                    coverPicker.launch(arrayOf("image/*"))
                },
                onImportGameData = { game, kind ->
                    pendingGameDataImport = GameDataRequest(game.id, kind)
                    gameDataImportPicker.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onExportGameData = { game, kind ->
                    pendingGameDataExport = GameDataRequest(game.id, kind)
                    gameDataExportPicker.launch(game.exportFileName(kind))
                },
            )
        }
        if (savedInstanceState == null) handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme != ContentResolver.SCHEME_CONTENT && uri.scheme != ContentResolver.SCHEME_FILE) return
        viewModel.openRom(uri)
    }

    override fun onStop() {
        viewModel.onBackground()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        viewModel.onWindowFocusChanged(hasFocus)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (viewModel.handlePhysicalKey(keyCode, true)) true else super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (viewModel.handlePhysicalKey(keyCode, false)) true else super.onKeyUp(keyCode, event)
    }

    private fun LibraryGame.exportFileName(kind: GameDataKind): String {
        val safeName = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "game" }
        return "$safeName.${kind.extension}"
    }
}
