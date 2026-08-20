/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.ui

import android.view.KeyEvent
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.mgba.android.core.EmulatorKey
import io.mgba.android.R
import io.mgba.android.BuildConfig
import io.mgba.android.logic.settings.EmulatorSettings
import io.mgba.android.logic.settings.IdleOptimization
import io.mgba.android.shader.ShaderDefinition
import io.mgba.android.shader.ShaderUniformDefinition
import java.io.File
import kotlin.math.roundToInt

private enum class SettingsPage(@StringRes val title: Int) {
    AUDIO(R.string.settings_tab_audio),
    VIDEO(R.string.settings_tab_video),
    GAMEPLAY(R.string.settings_tab_gameplay),
    INPUT(R.string.settings_tab_input),
    SYSTEM(R.string.settings_tab_system),
}

@Composable
fun SettingsScreen(
    settings: EmulatorSettings,
    shaderCatalog: List<ShaderDefinition>,
    bindingTarget: EmulatorKey?,
    onUpdate: ((EmulatorSettings) -> EmulatorSettings) -> Unit,
    onBeginInputBinding: (EmulatorKey) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onChooseBios: () -> Unit,
    onChoosePatch: () -> Unit,
    onChooseCheats: () -> Unit,
) {
    var selectedPage by remember { mutableStateOf(SettingsPage.AUDIO) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(AppBackground),
    ) {
        val singleRow = maxWidth > maxHeight
        Column {
            SettingsNavigationBar(
                selectedPage = selectedPage,
                singleRow = singleRow,
                onSelectPage = { selectedPage = it },
                onReset = onReset,
                onClose = onClose,
            )
            when (selectedPage) {
                SettingsPage.AUDIO -> AudioSettings(settings, onUpdate)
                SettingsPage.VIDEO -> VideoSettings(settings, shaderCatalog, onUpdate)
                SettingsPage.GAMEPLAY -> GameplaySettings(settings, onUpdate)
                SettingsPage.INPUT -> InputSettings(settings, bindingTarget, onBeginInputBinding)
                SettingsPage.SYSTEM -> SystemSettings(
                    settings,
                    onUpdate,
                    onChooseBios,
                    onChoosePatch,
                    onChooseCheats,
                )
            }
        }
    }
}

@Composable
private fun SettingsNavigationBar(
    selectedPage: SettingsPage,
    singleRow: Boolean,
    onSelectPage: (SettingsPage) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    if (singleRow) {
        Row(
            modifier = Modifier.fillMaxWidth().background(PanelBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsTabs(selectedPage, onSelectPage, Modifier.weight(1f))
            SettingsActions(onReset, onClose)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().background(PanelBackground)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                SettingsActions(onReset, onClose)
            }
            SettingsTabs(selectedPage, onSelectPage, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsTabs(
    selectedPage: SettingsPage,
    onSelectPage: (SettingsPage) -> Unit,
    modifier: Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedPage.ordinal,
        modifier = modifier,
        backgroundColor = PanelBackground,
        contentColor = Accent,
        edgePadding = 0.dp,
    ) {
        SettingsPage.values().forEach { page ->
            Tab(
                selected = page == selectedPage,
                onClick = { onSelectPage(page) },
                modifier = Modifier.height(48.dp),
                text = { Text(stringResource(page.title), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun SettingsActions(onReset: () -> Unit, onClose: () -> Unit) {
    TextButton(onClick = onReset) {
        Text(stringResource(R.string.action_restore_defaults), color = Color.White, maxLines = 1)
    }
    TextButton(onClick = onClose) {
        Text(stringResource(R.string.action_done), color = Accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InputSettings(
    settings: EmulatorSettings,
    bindingTarget: EmulatorKey?,
    onBeginInputBinding: (EmulatorKey) -> Unit,
) = SettingsList {
    item { SectionTitle(stringResource(R.string.section_physical_key_mapping)) }
    if (bindingTarget != null) {
        item {
            Text(
                stringResource(R.string.binding_prompt, bindingTarget.displayName()),
                color = Accent,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
    EmulatorKey.values().forEach { key ->
        item {
            SettingRow(key.displayName(), KeyEvent.keyCodeToString(settings.inputBindings.getValue(key))) {
                Button(
                    onClick = { onBeginInputBinding(key) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (bindingTarget == key) Accent else ButtonColor,
                    ),
                ) {
                    Text(
                        stringResource(
                            if (bindingTarget == key) R.string.action_waiting_for_key
                            else R.string.action_rebind,
                        ),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSettings(
    settings: EmulatorSettings,
    update: ((EmulatorSettings) -> EmulatorSettings) -> Unit,
) = SettingsList {
    item { SectionTitle(stringResource(R.string.section_general)) }
    item {
        SwitchSetting(
            stringResource(R.string.setting_mute),
            stringResource(R.string.setting_mute_description),
            settings.muted,
        ) { update { s -> s.copy(muted = it) } }
    }
    item {
        SliderSetting(
            stringResource(R.string.setting_volume),
            "${settings.volume * 100 / 0x100}%",
            settings.volume.toFloat(),
            0f..0x100.toFloat(),
        ) {
            update { s -> s.copy(volume = it.toInt(), fastForwardVolume = s.fastForwardVolume.coerceAtMost(it.toInt())) }
        }
    }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_sample_rate),
            settings.sampleRate,
            listOf(32_768, 44_100, 48_000).map { it to "$it Hz" },
        ) { value -> update { it.copy(sampleRate = value) } }
    }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_audio_buffer),
            settings.audioBufferFrames,
            listOf(512, 1024, 1536, 2048, 4096, 8192).map {
                it to quantityString(R.plurals.value_frames, it)
            },
        ) { value -> update { it.copy(audioBufferFrames = value) } }
    }
    item {
        SwitchSetting(
            stringResource(R.string.setting_audio_sync),
            stringResource(R.string.setting_audio_sync_description),
            settings.audioSync,
        ) { update { s -> s.copy(audioSync = it) } }
    }
    item { SectionTitle(stringResource(R.string.section_fast_forward)) }
    item {
        SwitchSetting(stringResource(R.string.setting_mute_fast_forward), null, settings.fastForwardMuted) {
            update { s -> s.copy(fastForwardMuted = it) }
        }
    }
    item {
        SliderSetting(
            stringResource(R.string.setting_fast_forward_volume),
            "${settings.fastForwardVolume * 100 / 0x100}%",
            settings.fastForwardVolume.toFloat(),
            0f..settings.volume.coerceAtLeast(1).toFloat(),
        ) { value -> update { it.copy(fastForwardVolume = value.toInt()) } }
    }
}

@Composable
private fun VideoSettings(
    settings: EmulatorSettings,
    shaderCatalog: List<ShaderDefinition>,
    update: ((EmulatorSettings) -> EmulatorSettings) -> Unit,
) = SettingsList {
    item { SectionTitle(stringResource(R.string.section_frame_rate)) }
    item {
        SwitchSetting(
            stringResource(R.string.setting_video_sync),
            stringResource(R.string.setting_video_sync_description),
            settings.videoSync,
        ) { update { s -> s.copy(videoSync = it) } }
    }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_target_frame_rate),
            settings.targetFps,
            listOf(
                EmulatorSettings.NATIVE_GBA_FPS to stringResource(R.string.value_native_fps),
                30f to "30 FPS",
                60f to "60 FPS",
                90f to "90 FPS",
                120f to "120 FPS",
            ),
        ) { value -> update { it.copy(targetFps = value) } }
    }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_frame_skip),
            settings.frameSkip,
            (0..5).map { it to if (it == 0) stringResource(R.string.value_off) else "$it" },
        ) { value ->
            update { it.copy(frameSkip = value) }
        }
    }
    item { SectionTitle(stringResource(R.string.section_scaling_display)) }
    item {
        SwitchSetting(
            stringResource(R.string.setting_fullscreen_player),
            stringResource(R.string.setting_fullscreen_player_description),
            settings.fullscreenPlayer,
        ) { update { current -> current.copy(fullscreenPlayer = it) } }
    }
    item { SwitchSetting(stringResource(R.string.setting_lock_aspect_ratio), null, settings.lockAspectRatio) { update { s -> s.copy(lockAspectRatio = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_integer_scaling), stringResource(R.string.setting_integer_scaling_description), settings.integerScaling) { update { s -> s.copy(integerScaling = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_linear_filtering), stringResource(R.string.setting_linear_filtering_description), settings.linearFiltering) { update { s -> s.copy(linearFiltering = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_interframe_blending), stringResource(R.string.setting_interframe_blending_description), settings.interframeBlending) { update { s -> s.copy(interframeBlending = it) } } }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_gpu_shader),
            settings.shaderId,
            listOf("" to stringResource(R.string.value_off)) + shaderCatalog.map { it.id to it.name },
        ) { value -> update { it.copy(shaderId = value) } }
    }
    val selectedShader = shaderCatalog.firstOrNull { it.id == settings.shaderId }
    if (selectedShader != null) {
        item {
            SettingRow(
                selectedShader.name,
                listOfNotNull(
                    selectedShader.author.takeIf(String::isNotBlank)?.let {
                        stringResource(R.string.value_author, it)
                    },
                    selectedShader.description.takeIf(String::isNotBlank),
                    quantityString(R.plurals.value_passes, selectedShader.passes.size),
                ).joinToString(" · "),
            ) {}
        }
        selectedShader.passes.forEachIndexed { passIndex, pass ->
            pass.uniforms.forEach { uniform ->
                uniform.defaults.indices.forEach { component ->
                    item {
                        ShaderParameterSetting(
                            shader = selectedShader,
                            passIndex = passIndex,
                            uniform = uniform,
                            component = component,
                            settings = settings,
                            update = update,
                        )
                    }
                }
            }
        }
        if (selectedShader.passes.any { it.uniforms.isNotEmpty() }) {
            item {
                SettingRow(
                    stringResource(R.string.setting_shader_parameters),
                    stringResource(R.string.setting_shader_parameters_description, selectedShader.name),
                ) {
                    Button(
                        onClick = {
                            update { current ->
                                current.copy(
                                    shaderParameters = current.shaderParameters.filterKeys {
                                        !it.startsWith("${selectedShader.id}.")
                                    },
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = ButtonColor),
                    ) {
                        Text(stringResource(R.string.action_restore_defaults), color = Color.White)
                    }
                }
            }
        }
    }
    item { SwitchSetting(stringResource(R.string.setting_show_fps), null, settings.showFps) { update { s -> s.copy(showFps = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_show_frame_counter), null, settings.showFrameCounter) { update { s -> s.copy(showFrameCounter = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_show_filename), null, settings.showFilename) { update { s -> s.copy(showFilename = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_show_osd), null, settings.showOsd) { update { s -> s.copy(showOsd = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_show_reset_info), null, settings.showResetInfo) { update { s -> s.copy(showResetInfo = it) } } }
}

@Composable
private fun GameplaySettings(
    settings: EmulatorSettings,
    update: ((EmulatorSettings) -> EmulatorSettings) -> Unit,
) = SettingsList {
    item { SectionTitle(stringResource(R.string.section_speed)) }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_toggle_fast_forward_speed),
            settings.fastForwardRatio,
            listOf(-1f to stringResource(R.string.value_unlimited), 2f to "2×", 3f to "3×", 4f to "4×", 8f to "8×"),
        ) { value -> update { it.copy(fastForwardRatio = value) } }
    }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_hold_fast_forward_speed),
            settings.fastForwardHeldRatio,
            listOf(-1f to stringResource(R.string.value_unlimited), 2f to "2×", 3f to "3×", 4f to "4×", 8f to "8×"),
        ) { value -> update { it.copy(fastForwardHeldRatio = value) } }
    }
    item { SectionTitle(stringResource(R.string.section_rewind)) }
    item { SwitchSetting(stringResource(R.string.setting_enable_rewind), null, settings.rewindEnabled) { update { s -> s.copy(rewindEnabled = it) } } }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_rewind_capacity),
            settings.rewindCapacity,
            listOf(120, 300, 600, 1200).map { it to quantityString(R.plurals.value_frames, it) },
        ) { value -> update { it.copy(rewindCapacity = value) } }
    }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_rewind_interval),
            settings.rewindInterval,
            listOf(1, 2, 3, 5, 10).map { it to quantityString(R.plurals.value_frames, it) },
        ) { value -> update { it.copy(rewindInterval = value) } }
    }
    item { SectionTitle(stringResource(R.string.section_state_input)) }
    item { SwitchSetting(stringResource(R.string.setting_auto_load_state), null, settings.autoLoadState) { update { s -> s.copy(autoLoadState = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_auto_save_state), null, settings.autoSaveState) { update { s -> s.copy(autoSaveState = it) } } }
    item { SectionTitle(stringResource(R.string.section_state_extras)) }
    item { SwitchSetting(stringResource(R.string.setting_load_state_screenshot), null, settings.loadStateScreenshot) { update { s -> s.copy(loadStateScreenshot = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_load_state_save_data), null, settings.loadStateSaveData) { update { s -> s.copy(loadStateSaveData = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_load_state_cheats), null, settings.loadStateCheats) { update { s -> s.copy(loadStateCheats = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_save_state_screenshot), null, settings.saveStateScreenshot) { update { s -> s.copy(saveStateScreenshot = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_save_state_save_data), null, settings.saveStateSaveData) { update { s -> s.copy(saveStateSaveData = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_save_state_cheats), null, settings.saveStateCheats) { update { s -> s.copy(saveStateCheats = it) } } }
    item { SectionTitle(stringResource(R.string.section_input)) }
    item { SwitchSetting(stringResource(R.string.setting_preload_rom), stringResource(R.string.setting_preload_rom_description), settings.preloadRom) { update { s -> s.copy(preloadRom = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_allow_opposing_directions), null, settings.allowOpposingDirections) { update { s -> s.copy(allowOpposingDirections = it) } } }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_autofire_cycle),
            settings.autofireThreshold,
            listOf(1, 2, 3, 4, 5, 6).map { it to quantityString(R.plurals.value_frames, it) },
        ) { value -> update { it.copy(autofireThreshold = value) } }
    }
}

@Composable
private fun SystemSettings(
    settings: EmulatorSettings,
    update: ((EmulatorSettings) -> EmulatorSettings) -> Unit,
    onChooseBios: () -> Unit,
    onChoosePatch: () -> Unit,
    onChooseCheats: () -> Unit,
) = SettingsList {
    item { SectionTitle(stringResource(R.string.section_bios)) }
    item { SwitchSetting(stringResource(R.string.setting_use_bios), null, settings.useBios) { update { s -> s.copy(useBios = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_skip_bios), null, settings.skipBios) { update { s -> s.copy(skipBios = it) } } }
    item {
        FileSetting("GBA BIOS", settings.biosPath, onChooseBios) {
            update { it.copy(biosPath = "") }
        }
    }
    item { SectionTitle(stringResource(R.string.section_game_support_files)) }
    item {
        FileSetting(stringResource(R.string.setting_patch_file), settings.patchPath, onChoosePatch) {
            update { it.copy(patchPath = "") }
        }
    }
    item {
        FileSetting(stringResource(R.string.setting_cheats_file), settings.cheatsPath, onChooseCheats) {
            update { it.copy(cheatsPath = "") }
        }
    }
    item { SwitchSetting(stringResource(R.string.setting_cheat_autoload), null, settings.cheatAutoload) { update { s -> s.copy(cheatAutoload = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_cheat_autosave), null, settings.cheatAutosave) { update { s -> s.copy(cheatAutosave = it) } } }
    item { SectionTitle(stringResource(R.string.section_core)) }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_idle_loop_optimization),
            settings.idleOptimization,
            listOf(
                IdleOptimization.IGNORE to stringResource(R.string.value_ignore),
                IdleOptimization.REMOVE to stringResource(R.string.value_remove),
                IdleOptimization.DETECT to stringResource(R.string.value_detect),
            ),
        ) { value -> update { it.copy(idleOptimization = value) } }
    }
    item { SwitchSetting(stringResource(R.string.setting_force_game_boy_player), null, settings.forceGameBoyPlayer) { update { s -> s.copy(forceGameBoyPlayer = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_vba_bug_compatibility), null, settings.vbaBugCompatibility) { update { s -> s.copy(vbaBugCompatibility = it) } } }
    item { SectionTitle(stringResource(R.string.section_logging)) }
    item {
        ChoiceSetting(
            stringResource(R.string.setting_log_level),
            settings.logLevel,
            listOf(
                EmulatorSettings.LOG_FATAL to stringResource(R.string.value_fatal_only),
                (EmulatorSettings.LOG_FATAL or EmulatorSettings.LOG_ERROR) to stringResource(R.string.value_error),
                (EmulatorSettings.LOG_FATAL or EmulatorSettings.LOG_ERROR or EmulatorSettings.LOG_WARN) to stringResource(R.string.value_warning),
                (EmulatorSettings.LOG_FATAL or EmulatorSettings.LOG_ERROR or EmulatorSettings.LOG_WARN or EmulatorSettings.LOG_INFO) to stringResource(R.string.value_info),
                (EmulatorSettings.LOG_FATAL or EmulatorSettings.LOG_ERROR or EmulatorSettings.LOG_WARN or EmulatorSettings.LOG_INFO or EmulatorSettings.LOG_DEBUG) to stringResource(R.string.value_debug),
            ),
        ) { value -> update { it.copy(logLevel = value) } }
    }
    item {
        SwitchSetting(
            stringResource(R.string.setting_log_to_file),
            stringResource(R.string.setting_log_to_file_description),
            settings.logToFile,
        ) { update { s -> s.copy(logToFile = it) } }
    }
    item { SectionTitle(stringResource(R.string.section_android_lifecycle)) }
    item { SwitchSetting(stringResource(R.string.setting_keep_screen_on), null, settings.keepScreenOn) { update { s -> s.copy(keepScreenOn = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_pause_on_focus_lost), stringResource(R.string.setting_pause_on_focus_lost_description), settings.pauseOnFocusLost) { update { s -> s.copy(pauseOnFocusLost = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_mute_on_focus_lost), null, settings.muteOnFocusLost) { update { s -> s.copy(muteOnFocusLost = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_pause_on_background), null, settings.pauseOnBackground) { update { s -> s.copy(pauseOnBackground = it) } } }
    item { SwitchSetting(stringResource(R.string.setting_mute_on_background), null, settings.muteOnBackground) { update { s -> s.copy(muteOnBackground = it) } } }
    item { SectionTitle(stringResource(R.string.section_about)) }
    item {
        SettingRow(
            stringResource(R.string.app_name),
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        ) {}
    }
    item {
        SettingRow(
            stringResource(R.string.about_cover_art),
            stringResource(R.string.about_cover_art_source),
        ) {}
    }
    item {
        SettingRow(
            stringResource(R.string.about_license),
            stringResource(R.string.about_license_value),
        ) {}
    }
}

@Composable
private fun SettingsList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = Accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 5.dp),
    )
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    SettingRow(
        title,
        description,
        Modifier
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChecked,
            )
            .semantics(mergeDescendants = true) { contentDescription = title },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
            colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent),
        )
    }
}

@Composable
private fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValue: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            Text(valueLabel, color = Color(0xFFADB5C3))
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            steps = steps,
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = valueLabel
            },
        )
        Divider(color = Color(0xFF30353F))
    }
}

@Composable
private fun ShaderParameterSetting(
    shader: ShaderDefinition,
    passIndex: Int,
    uniform: ShaderUniformDefinition,
    component: Int,
    settings: EmulatorSettings,
    update: ((EmulatorSettings) -> EmulatorSettings) -> Unit,
) {
    val key = uniform.parameterKey(shader.id, passIndex, component)
    val value = settings.shaderParameters[key] ?: uniform.defaults[component]
    val range = uniform.range(component)
    val componentName = if (uniform.kind.components == 1) {
        uniform.readableName
    } else {
        "${uniform.readableName} [${listOf("X", "Y", "Z", "W")[component]}]"
    }
    val steps = if (uniform.kind.integer) {
        (range.endInclusive - range.start).roundToInt().minus(1).coerceAtLeast(0)
    } else {
        0
    }
    SliderSetting(
        title = "Pass ${passIndex + 1} · $componentName",
        valueLabel = if (uniform.kind.integer) value.roundToInt().toString() else "%.3f".format(value),
        value = value.coerceIn(range.start, range.endInclusive),
        range = range,
        steps = steps,
    ) { selected ->
        val normalized = if (uniform.kind.integer) selected.roundToInt().toFloat() else selected
        update { current ->
            current.copy(shaderParameters = current.shaderParameters + (key to normalized))
        }
    }
}

@Composable
private fun <T> ChoiceSetting(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: value.toString()
    SettingRow(title, null) {
        Column {
            Text(
                label,
                color = Accent,
                modifier = Modifier
                    .background(ButtonColor, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .semantics {
                        contentDescription = "$title, $label"
                        role = Role.Button
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (option, optionLabel) ->
                    DropdownMenuItem(onClick = {
                        expanded = false
                        onSelected(option)
                    }) {
                        Text(optionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSetting(
    title: String,
    path: String,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    SettingRow(
        title,
        path.takeIf { it.isNotBlank() }?.let { File(it).name }
            ?: stringResource(R.string.value_not_selected),
    ) {
        Button(onClick = onChoose, colors = ButtonDefaults.buttonColors(backgroundColor = ButtonColor)) {
            Text(stringResource(R.string.action_select), color = Color.White)
        }
        if (path.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClear, colors = ButtonDefaults.buttonColors(backgroundColor = ButtonColor)) {
                Text(stringResource(R.string.action_clear), color = Color.White)
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (description != null) Text(description, color = Color(0xFFADB5C3), fontSize = 12.sp)
        }
        action()
    }
    Divider(color = Color(0xFF30353F))
}

@Composable
private fun EmulatorKey.displayName(): String = stringResource(when (this) {
    EmulatorKey.A -> R.string.key_a
    EmulatorKey.B -> R.string.key_b
    EmulatorKey.SELECT -> R.string.key_select
    EmulatorKey.START -> R.string.key_start
    EmulatorKey.RIGHT -> R.string.key_right
    EmulatorKey.LEFT -> R.string.key_left
    EmulatorKey.UP -> R.string.key_up
    EmulatorKey.DOWN -> R.string.key_down
    EmulatorKey.R -> R.string.key_r
    EmulatorKey.L -> R.string.key_l
})

@Composable
private fun quantityString(@PluralsRes resource: Int, quantity: Int): String =
    LocalContext.current.resources.getQuantityString(resource, quantity, quantity)
