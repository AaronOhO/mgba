/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.mgba.android.ui.EmulatorViewModel
import io.mgba.android.ui.MgbaApp

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<EmulatorViewModel> {
        EmulatorViewModel.Factory(application)
    }

    private val romPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importRom)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MgbaApp(
                viewModel = viewModel,
                onChooseRom = { romPicker.launch(arrayOf("*/*")) },
                onChooseBios = { biosPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onChoosePatch = { patchPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onChooseCheats = { cheatsPicker.launch(arrayOf("text/plain", "*/*")) },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
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
}
