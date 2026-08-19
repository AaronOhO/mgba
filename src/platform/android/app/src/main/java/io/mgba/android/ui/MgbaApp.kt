/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.mgba.android.core.EmulatorKey
import io.mgba.android.core.EmulatorFailure
import io.mgba.android.core.EmulatorState
import io.mgba.android.R
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference

internal val AppBackground = Color(0xFF111318)
internal val PanelBackground = Color(0xFF1B1E24)
internal val Accent = Color(0xFF58D68D)
internal val ButtonColor = Color(0xFF30353F)
private val PressedButtonColor = Color(0xFF58D68D)

@Composable
fun MgbaApp(
    viewModel: EmulatorViewModel,
    onChooseRom: () -> Unit,
    onChooseBios: () -> Unit,
    onChoosePatch: () -> Unit,
    onChooseCheats: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val message by viewModel.message.collectAsState()
    val bindingTarget by viewModel.bindingTarget.collectAsState()
    val frameView = remember { AtomicReference<FrameView?>(null) }
    val hostView = LocalView.current
    var showSettings by remember { mutableStateOf(false) }

    DisposableEffect(hostView, settings.keepScreenOn) {
        hostView.keepScreenOn = settings.keepScreenOn
        onDispose { hostView.keepScreenOn = false }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            viewModel.clearMessage()
        }
    }

    DisposableEffect(viewModel) {
        viewModel.setFrameConsumer { pixels, width, height ->
            frameView.get()?.render(pixels, width, height)
        }
        onDispose {
            viewModel.setFrameConsumer(null)
            frameView.set(null)
        }
    }

    MaterialTheme(
        colors = darkColors(
            primary = Accent,
            background = AppBackground,
            surface = PanelBackground,
        ),
    ) {
        if (showSettings) {
            frameView.set(null)
            SettingsScreen(
                settings = settings,
                shaderCatalog = viewModel.shaderCatalog,
                bindingTarget = bindingTarget,
                onUpdate = viewModel::updateSettings,
                onBeginInputBinding = viewModel::beginInputBinding,
                onReset = viewModel::resetSettings,
                onClose = { showSettings = false },
                onChooseBios = onChooseBios,
                onChoosePatch = onChoosePatch,
                onChooseCheats = onChooseCheats,
            )
        } else BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(12.dp),
        ) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmulatorPanel(
                        modifier = Modifier.weight(1.25f).fillMaxHeight(),
                        state = state,
                        settings = settings,
                        stats = stats,
                        message = message,
                        viewModel = viewModel,
                        frameView = frameView,
                        onChooseRom = onChooseRom,
                        onOpenSettings = { showSettings = true },
                    )
                    Controller(
                        modifier = Modifier.weight(0.75f).fillMaxHeight().widthIn(min = 300.dp),
                        onKey = viewModel::setKey,
                        onAutofire = viewModel::setAutofire,
                        onFastForward = viewModel::setFastForward,
                        onToggleFastForward = viewModel::toggleFastForward,
                        onRewind = viewModel::setRewinding,
                        onQuickSave = viewModel::quickSave,
                        onQuickLoad = viewModel::quickLoad,
                        rewindEnabled = settings.rewindEnabled,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmulatorPanel(
                        modifier = Modifier.weight(0.9f).fillMaxWidth(),
                        state = state,
                        settings = settings,
                        stats = stats,
                        message = message,
                        viewModel = viewModel,
                        frameView = frameView,
                        onChooseRom = onChooseRom,
                        onOpenSettings = { showSettings = true },
                    )
                    Controller(
                        modifier = Modifier.weight(1.1f).fillMaxWidth(),
                        onKey = viewModel::setKey,
                        onAutofire = viewModel::setAutofire,
                        onFastForward = viewModel::setFastForward,
                        onToggleFastForward = viewModel::toggleFastForward,
                        onRewind = viewModel::setRewinding,
                        onQuickSave = viewModel::quickSave,
                        onQuickLoad = viewModel::quickLoad,
                        rewindEnabled = settings.rewindEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmulatorPanel(
    modifier: Modifier,
    state: EmulatorState,
    settings: io.mgba.android.settings.EmulatorSettings,
    stats: EmulatorViewModel.PerformanceStats,
    message: String?,
    viewModel: EmulatorViewModel,
    frameView: AtomicReference<FrameView?>,
    onChooseRom: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = modifier) {
        Header(
            state = state,
            showFilename = settings.showFilename,
            onChooseRom = onChooseRom,
            onReset = viewModel::reset,
            onSettings = onOpenSettings,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    FrameView(context, viewModel.shaderCatalog, viewModel::reportShaderError)
                        .also(frameView::set)
                },
                update = { it.applySettings(settings) },
                modifier = Modifier.fillMaxSize(),
            )
            StatusOverlay(state)
            PerformanceOverlay(settings.showFps, settings.showFrameCounter, stats)
            message?.let { OsdMessage(it) }
        }
    }
}

@Composable
private fun Header(
    state: EmulatorState,
    showFilename: Boolean,
    onChooseRom: () -> Unit,
    onReset: () -> Unit,
    onSettings: () -> Unit,
) = BoxWithConstraints {
    if (maxWidth < 600.dp) {
        Column {
            HeaderTitle(state, showFilename, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            HeaderActions(
                state = state,
                onChooseRom = onChooseRom,
                onReset = onReset,
                onSettings = onSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderTitle(state, showFilename, Modifier.weight(1f))
            HeaderActions(state, onChooseRom, onReset, onSettings)
        }
    }
}

@Composable
private fun HeaderTitle(state: EmulatorState, showFilename: Boolean, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when (state) {
                EmulatorState.Idle -> stringResource(R.string.status_choose_gba_rom)
                is EmulatorState.Loading -> state.romName
                is EmulatorState.Running -> state.game.displayTitle(showFilename)
                is EmulatorState.Paused -> stringResource(
                    R.string.status_paused_format,
                    state.game.displayTitle(showFilename),
                )
                is EmulatorState.Error -> state.displayMessage()
            },
            color = if (state is EmulatorState.Error) Color(0xFFFF8A80) else Color(0xFFADB5C3),
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeaderActions(
    state: EmulatorState,
    onChooseRom: () -> Unit,
    onReset: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.End) {
        if (state is EmulatorState.Running || state is EmulatorState.Paused) {
            Button(onClick = onReset, colors = ButtonDefaults.buttonColors(backgroundColor = ButtonColor)) {
                Text(stringResource(R.string.action_reset), color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
        }
        Button(onClick = onSettings, colors = ButtonDefaults.buttonColors(backgroundColor = ButtonColor)) {
            Text(stringResource(R.string.action_settings), color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onChooseRom, colors = ButtonDefaults.buttonColors(backgroundColor = Accent)) {
            Text(
                stringResource(R.string.action_choose_rom),
                color = Color(0xFF07130C),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BoxScope.PerformanceOverlay(
    showFps: Boolean,
    showFrameCounter: Boolean,
    stats: EmulatorViewModel.PerformanceStats,
) {
    if (!showFps && !showFrameCounter) return
    val frameText = stringResource(R.string.status_frame_format, stats.frameNumber)
    val text = buildList {
        if (showFps) add("%.1f FPS".format(stats.fps))
        if (showFrameCounter) add(frameText)
    }.joinToString(" · ")
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .background(Color(0x99000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun BoxScope.OsdMessage(message: String) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(10.dp)
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(message, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun StatusOverlay(state: EmulatorState) {
    val message = when (state) {
        EmulatorState.Idle -> stringResource(R.string.status_select_gba_file)
        is EmulatorState.Loading -> stringResource(R.string.status_loading)
        is EmulatorState.Error -> stringResource(R.string.status_load_failed) + "\n" + state.displayMessage()
        is EmulatorState.Paused -> stringResource(R.string.status_paused)
        is EmulatorState.Running -> null
    }
    if (message != null) {
        Box(
            modifier = Modifier
                .background(Color(0xB8000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(message, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Controller(
    modifier: Modifier,
    onKey: (EmulatorKey, Boolean) -> Unit,
    onAutofire: (EmulatorKey, Boolean) -> Unit,
    onFastForward: (Boolean) -> Unit,
    onToggleFastForward: () -> Unit,
    onRewind: (Boolean) -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    rewindEnabled: Boolean,
) {
    var autofireA by remember { mutableStateOf(false) }
    var autofireB by remember { mutableStateOf(false) }
    var fastForwardToggled by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton("L", EmulatorKey.L, onKey, Modifier.weight(1f).height(44.dp), RoundedCornerShape(12.dp))
            GameButton("R", EmulatorKey.R, onKey, Modifier.weight(1f).height(44.dp), RoundedCornerShape(12.dp))
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DirectionPad(onKey)
            ActionButtons(onKey)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameButton(
                "SELECT",
                EmulatorKey.SELECT,
                onKey,
                Modifier.weight(1f).height(42.dp),
                RoundedCornerShape(20.dp),
            )
            GameButton(
                "START",
                EmulatorKey.START,
                onKey,
                Modifier.weight(1f).height(42.dp),
                RoundedCornerShape(20.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(stringResource(R.string.action_quick_save), onQuickSave, Modifier.weight(1f))
            SmallActionButton(stringResource(R.string.action_quick_load), onQuickLoad, Modifier.weight(1f))
            HoldActionButton(stringResource(R.string.action_fast_forward), onFastForward, Modifier.weight(1f))
            HoldActionButton(stringResource(R.string.action_rewind), onRewind, Modifier.weight(1f), enabled = rewindEnabled)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(
                stringResource(
                    if (fastForwardToggled) R.string.action_fast_forward_on
                    else R.string.action_fast_forward_toggle,
                ),
                {
                    fastForwardToggled = !fastForwardToggled
                    onToggleFastForward()
                },
                Modifier.weight(1f),
                active = fastForwardToggled,
            )
            SmallActionButton(
                stringResource(if (autofireA) R.string.action_autofire_a_on else R.string.action_autofire_a),
                {
                    autofireA = !autofireA
                    onAutofire(EmulatorKey.A, autofireA)
                },
                Modifier.weight(1f),
                active = autofireA,
            )
            SmallActionButton(
                stringResource(if (autofireB) R.string.action_autofire_b_on else R.string.action_autofire_b),
                {
                    autofireB = !autofireB
                    onAutofire(EmulatorKey.B, autofireB)
                },
                Modifier.weight(1f),
                active = autofireB,
            )
        }
    }
}

private fun io.mgba.android.core.GameMetadata.displayTitle(showFilename: Boolean): String =
    if (showFilename && displayName.isNotBlank()) displayName else title

@Composable
private fun EmulatorState.Error.displayMessage(): String =
    detail?.takeIf { failure == EmulatorFailure.ROM_IMPORT && it.isNotBlank() }
        ?: stringResource(
            when (failure) {
                EmulatorFailure.ROM_LOAD -> R.string.error_rom_load
                EmulatorFailure.CONFIGURATION -> R.string.error_configuration
                EmulatorFailure.EMULATION -> R.string.error_emulation
                EmulatorFailure.ROM_IMPORT -> R.string.message_rom_import_failed
            },
        )

@Composable
private fun SmallActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    active: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = if (active) Accent else ButtonColor),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        Text(label, color = if (active) Color(0xFF07130C) else Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun HoldActionButton(
    label: String,
    onHeld: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    var pressed by remember { mutableStateOf(false) }
    DisposableEffect(enabled) {
        onDispose { if (pressed) onHeld(false) }
    }
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (!enabled) Color(0xFF24272D) else if (pressed) Accent else ButtonColor)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onPress = {
                    try {
                        pressed = true
                        onHeld(true)
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                        onHeld(false)
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else Color(0xFF666B75), fontSize = 11.sp)
    }
}

@Composable
private fun DirectionPad(onKey: (EmulatorKey, Boolean) -> Unit) {
    Box(Modifier.size(168.dp)) {
        GameButton("▲", EmulatorKey.UP, onKey, Modifier.size(58.dp).align(Alignment.TopCenter), RoundedCornerShape(10.dp))
        GameButton("▼", EmulatorKey.DOWN, onKey, Modifier.size(58.dp).align(Alignment.BottomCenter), RoundedCornerShape(10.dp))
        GameButton("◀", EmulatorKey.LEFT, onKey, Modifier.size(58.dp).align(Alignment.CenterStart), RoundedCornerShape(10.dp))
        GameButton("▶", EmulatorKey.RIGHT, onKey, Modifier.size(58.dp).align(Alignment.CenterEnd), RoundedCornerShape(10.dp))
    }
}

@Composable
private fun ActionButtons(onKey: (EmulatorKey, Boolean) -> Unit) {
    Box(Modifier.size(150.dp)) {
        GameButton("B", EmulatorKey.B, onKey, Modifier.size(68.dp).align(Alignment.BottomStart), CircleShape)
        GameButton("A", EmulatorKey.A, onKey, Modifier.size(68.dp).align(Alignment.TopEnd), CircleShape)
    }
}

@Composable
private fun GameButton(
    label: String,
    key: EmulatorKey,
    onKey: (EmulatorKey, Boolean) -> Unit,
    modifier: Modifier,
    shape: Shape,
) {
    var pressed by remember(key) { mutableStateOf(false) }
    DisposableEffect(key) {
        onDispose {
            if (pressed) onKey(key, false)
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (pressed) PressedButtonColor else ButtonColor)
            .pointerInput(key) {
                detectTapGestures(
                    onPress = {
                        try {
                            pressed = true
                            onKey(key, true)
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                            onKey(key, false)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (pressed) Color(0xFF07130C) else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}
