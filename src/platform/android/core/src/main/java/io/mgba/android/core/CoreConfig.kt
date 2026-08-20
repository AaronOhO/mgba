/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.core

data class CoreConfig(
    val options: Map<String, String>,
    val preloadRom: Boolean,
    val patchPath: String,
    val cheatsPath: String,
    val cheatAutoload: Boolean,
    val rewindEnabled: Boolean,
    val rewindCapacity: Int,
    val rewindInterval: Int,
) {
    fun nativeOptions(): Array<String> = options
        .flatMap { (key, value) -> listOf(key, value) }
        .toTypedArray()
}
