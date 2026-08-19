/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.shader

import android.content.res.AssetManager
import android.util.Log

enum class ShaderUniformKind(val components: Int, val integer: Boolean) {
    FLOAT(1, false),
    FLOAT2(2, false),
    FLOAT3(3, false),
    INT(1, true),
}

data class ShaderUniformDefinition(
    val name: String,
    val readableName: String,
    val kind: ShaderUniformKind,
    val defaults: List<Float>,
    val minimums: List<Float>?,
    val maximums: List<Float>?,
) {
    fun parameterKey(shaderId: String, pass: Int, component: Int): String =
        "$shaderId.$pass.$name.$component"

    fun range(component: Int): ClosedFloatingPointRange<Float> {
        val default = defaults[component]
        val minimum = minimums?.get(component) ?: 0f.coerceAtMost(default)
        val maximum = maximums?.get(component) ?: maxOf(2f, default * 2f)
        return if (minimum < maximum) minimum..maximum else (minimum - 1f)..(maximum + 1f)
    }
}

data class ShaderPassDefinition(
    val vertexSource: String?,
    val fragmentSource: String?,
    val width: Int,
    val height: Int,
    val integerScaling: Boolean,
    val blend: Boolean,
    val filter: Boolean,
    val uniforms: List<ShaderUniformDefinition>,
)

data class ShaderDefinition(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val passes: List<ShaderPassDefinition>,
) {
    fun defaultParameters(): Map<String, Float> = buildMap {
        passes.forEachIndexed { passIndex, pass ->
            pass.uniforms.forEach { uniform ->
                uniform.defaults.forEachIndexed { component, value ->
                    put(uniform.parameterKey(id, passIndex, component), value)
                }
            }
        }
    }
}

object ShaderCatalog {
    fun load(assetManager: AssetManager): List<ShaderDefinition> =
        assetManager.list("")
            .orEmpty()
            .filter { it.endsWith(SHADER_DIRECTORY_SUFFIX) }
            .mapNotNull { directory ->
                val id = directory.removeSuffix(SHADER_DIRECTORY_SUFFIX)
                runCatching {
                    val root = directory
                    val manifest = assetManager.open("$root/manifest.ini").bufferedReader().use { it.readText() }
                    ShaderManifestParser.parse(id, manifest) { fileName ->
                        assetManager.open("$root/$fileName").bufferedReader().use { it.readText() }
                    }
                }.onFailure { error ->
                    Log.e(LOG_TAG, "Could not load bundled shader $id", error)
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }

    private const val SHADER_DIRECTORY_SUFFIX = ".shader"
    private const val LOG_TAG = "mGBA-Shaders"
}

object ShaderManifestParser {
    fun parse(
        id: String,
        manifest: String,
        sourceLoader: (String) -> String,
    ): ShaderDefinition {
        val sections = parseSections(manifest)
        val shader = sections["shader"] ?: error("Missing [shader] section")
        val passCount = shader["passes"]?.toIntOrNull() ?: error("Invalid shader pass count")
        require(passCount in 1..MAX_PASSES) { "Shader pass count must be between 1 and $MAX_PASSES" }
        val passes = (0 until passCount).map { passIndex ->
            val sectionName = "pass.$passIndex"
            val pass = sections[sectionName] ?: error("Missing [$sectionName] section")
            val vertexFile = pass["vertexShader"]
            val fragmentFile = pass["fragmentShader"]
            ShaderPassDefinition(
                vertexSource = vertexFile?.let(sourceLoader),
                fragmentSource = fragmentFile?.let(sourceLoader),
                width = pass["width"]?.toIntOrNull() ?: 0,
                height = pass["height"]?.toIntOrNull() ?: 0,
                integerScaling = pass["integerScaling"].toBooleanValue(),
                blend = pass["blend"].toBooleanValue(),
                filter = pass["filter"].toBooleanValue(),
                uniforms = parseUniforms(sections, passIndex),
            )
        }
        return ShaderDefinition(
            id = id,
            name = shader["name"].orEmpty().ifBlank { id },
            author = shader["author"].orEmpty(),
            description = shader["description"].orEmpty(),
            passes = passes,
        )
    }

    private fun parseUniforms(
        sections: Map<String, Map<String, String>>,
        passIndex: Int,
    ): List<ShaderUniformDefinition> {
        val prefix = "pass.$passIndex.uniform."
        return sections.entries
            .filter { it.key.startsWith(prefix) }
            .map { (sectionName, values) ->
                val name = sectionName.removePrefix(prefix)
                val kind = when (values["type"]) {
                    "float" -> ShaderUniformKind.FLOAT
                    "float2" -> ShaderUniformKind.FLOAT2
                    "float3" -> ShaderUniformKind.FLOAT3
                    "int" -> ShaderUniformKind.INT
                    else -> error("Unsupported uniform type in [$sectionName]")
                }
                ShaderUniformDefinition(
                    name = name,
                    readableName = values["readableName"].orEmpty().ifBlank { name },
                    kind = kind,
                    defaults = values.vector("default", kind.components) ?: List(kind.components) { 0f },
                    minimums = values.vector("min", kind.components),
                    maximums = values.vector("max", kind.components),
                )
            }
    }

    private fun parseSections(manifest: String): Map<String, Map<String, String>> {
        val sections = linkedMapOf<String, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        manifest.lineSequence().forEach { sourceLine ->
            val line = sourceLine.trim()
            when {
                line.isBlank() || line.startsWith(";") || line.startsWith("#") -> Unit
                line.startsWith("[") && line.endsWith("]") -> {
                    current = sections.getOrPut(line.substring(1, line.lastIndex)) { linkedMapOf() }
                }
                else -> {
                    val equals = line.indexOf('=')
                    require(equals > 0 && current != null) { "Invalid manifest line: $sourceLine" }
                    val value = line.substring(equals + 1).trim().removeSurrounding("\"")
                    current!![line.substring(0, equals).trim()] = value
                }
            }
        }
        return sections
    }

    private fun Map<String, String>.vector(prefix: String, components: Int): List<Float>? {
        if (components == 1) return get(prefix)?.toFloatOrNull()?.let(::listOf)
        if ((0 until components).none { containsKey("$prefix[$it]") }) return null
        return List(components) { component -> get("$prefix[$component]")?.toFloatOrNull() ?: 0f }
    }

    private fun String?.toBooleanValue(): Boolean = this == "1" || equals("true", ignoreCase = true)

    private const val MAX_PASSES = 8
}
