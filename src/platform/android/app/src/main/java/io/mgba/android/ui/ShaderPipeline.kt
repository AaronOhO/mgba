/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.ui

import android.opengl.GLES20
import io.mgba.android.shader.ShaderDefinition
import io.mgba.android.shader.ShaderPassDefinition
import io.mgba.android.shader.ShaderUniformDefinition
import io.mgba.android.shader.ShaderUniformKind
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

internal class ShaderPipeline(
    definitions: List<ShaderDefinition>,
) {
    private val definitions = definitions.associateBy(ShaderDefinition::id)
    private val vertices = floatBufferOf(-1f, -1f, -1f, 1f, 1f, 1f, 1f, -1f)
    private val sourceTexture = createTexture()
    private val preprocessTarget = RenderTarget()
    private val preprocessProgram = GlProgram(PREPROCESS_VERTEX, PREPROCESS_FRAGMENT)
    private val finalProgram = GlProgram(DEFAULT_VERTEX, PASSTHROUGH_FRAGMENT)
    private var uploadBuffer: IntBuffer? = null
    private var configuredShaderId: String? = null
    private var activeShader: ShaderDefinition? = null
    private var activePasses = emptyList<GlPass>()
    private var textureWidth = 0
    private var textureHeight = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var maximumTextureSize = 0

    init {
        IntArray(1).also {
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, it, 0)
            maximumTextureSize = it[0]
        }
    }

    fun surfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    fun selectShader(shaderId: String): String? {
        if (configuredShaderId == shaderId) return null
        activePasses.forEach(GlPass::release)
        activePasses = emptyList()
        activeShader = null
        configuredShaderId = shaderId
        if (shaderId.isBlank()) return null
        val definition = definitions[shaderId] ?: return "Shader not found: $shaderId"
        return runCatching {
            val passes = mutableListOf<GlPass>()
            try {
                definition.passes.forEach { passes += GlPass(it) }
            } catch (error: Throwable) {
                passes.forEach(GlPass::release)
                throw error
            }
            activeShader = definition
            activePasses = passes
        }.exceptionOrNull()?.let { error ->
            activePasses.forEach(GlPass::release)
            activePasses = emptyList()
            activeShader = null
            "${definition.name} failed to compile: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun draw(
        frame: IntArray?,
        width: Int,
        height: Int,
        lockAspectRatio: Boolean,
        integerScaling: Boolean,
        linearFiltering: Boolean,
        parameterValues: Map<String, Float>,
    ) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        if (frame != null) upload(frame, width, height)
        if (textureWidth <= 0 || textureHeight <= 0) {
            clearScreen()
            return
        }

        val viewport = contentViewport(lockAspectRatio, integerScaling)
        preprocessTarget.ensureSize(textureWidth, textureHeight, maximumTextureSize)
        drawProgram(
            program = preprocessProgram,
            inputTexture = sourceTexture,
            target = preprocessTarget,
            viewport = Viewport(0, 0, textureWidth, textureHeight),
            filter = false,
            blend = false,
            clear = true,
            parameterValues = emptyMap(),
            passIndex = -1,
            uniforms = emptyList(),
        )

        var inputTexture = preprocessTarget.texture
        activePasses.forEachIndexed { passIndex, pass ->
            val outputWidth = passOutputSize(
                pass.definition.width,
                viewport.width,
                textureWidth,
                pass.definition.integerScaling,
            )
            val outputHeight = passOutputSize(
                pass.definition.height,
                viewport.height,
                textureHeight,
                pass.definition.integerScaling,
            )
            pass.target.ensureSize(outputWidth, outputHeight, maximumTextureSize)
            drawProgram(
                program = pass.program,
                inputTexture = inputTexture,
                target = pass.target,
                viewport = Viewport(0, 0, outputWidth, outputHeight),
                filter = pass.definition.filter,
                blend = pass.definition.blend,
                clear = !pass.definition.blend,
                parameterValues = parameterValues,
                passIndex = passIndex,
                uniforms = pass.definition.uniforms,
            )
            inputTexture = pass.target.texture
        }

        clearScreen()
        drawProgram(
            program = finalProgram,
            inputTexture = inputTexture,
            target = null,
            viewport = viewport,
            filter = linearFiltering,
            blend = false,
            clear = false,
            parameterValues = emptyMap(),
            passIndex = -1,
            uniforms = emptyList(),
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glUseProgram(0)
    }

    private fun upload(frame: IntArray, width: Int, height: Int) {
        val buffer = uploadBuffer?.takeIf { it.capacity() >= frame.size }
            ?: ByteBuffer.allocateDirect(frame.size * Int.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
                .also { uploadBuffer = it }
        buffer.clear()
        buffer.put(frame)
        buffer.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTexture)
        if (width != textureWidth || height != textureHeight) {
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer,
            )
            textureWidth = width
            textureHeight = height
        } else {
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                width,
                height,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer,
            )
        }
    }

    private fun drawProgram(
        program: GlProgram,
        inputTexture: Int,
        target: RenderTarget?,
        viewport: Viewport,
        filter: Boolean,
        blend: Boolean,
        clear: Boolean,
        parameterValues: Map<String, Float>,
        passIndex: Int,
        uniforms: List<ShaderUniformDefinition>,
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target?.framebuffer ?: 0)
        GLES20.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
        if (clear) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        if (blend) {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            GLES20.glDisable(GLES20.GL_BLEND)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture)
        val textureFilter = if (filter) GLES20.GL_LINEAR else GLES20.GL_NEAREST
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, textureFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, textureFilter)
        GLES20.glUseProgram(program.program)
        GLES20.glUniform1i(program.uniform("tex"), 0)
        GLES20.glUniform2f(program.uniform("texSize"), textureWidth.toFloat(), textureHeight.toFloat())
        GLES20.glUniform2f(program.uniform("outputSize"), viewport.width.toFloat(), viewport.height.toFloat())
        uniforms.forEach { uniform ->
            val values = List(uniform.kind.components) { component ->
                parameterValues[uniform.parameterKey(activeShader!!.id, passIndex, component)]
                    ?: uniform.defaults[component]
            }
            program.setUniform(uniform, values)
        }
        val position = program.attribute("position")
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun contentViewport(lockAspectRatio: Boolean, integerScaling: Boolean): Viewport {
        if (!lockAspectRatio) return Viewport(0, 0, surfaceWidth, surfaceHeight)
        val availableScale = min(
            surfaceWidth.toFloat() / textureWidth,
            surfaceHeight.toFloat() / textureHeight,
        )
        val scale = if (integerScaling && availableScale >= 2f) floor(availableScale) else availableScale
        val width = (textureWidth * scale).toInt()
        val height = (textureHeight * scale).toInt()
        return Viewport((surfaceWidth - width) / 2, (surfaceHeight - height) / 2, width, height)
    }

    private fun passOutputSize(configured: Int, viewport: Int, source: Int, integerScaling: Boolean): Int {
        var size = when {
            configured > 0 -> configured
            configured < 0 -> source * -configured
            else -> viewport
        }
        if (integerScaling) {
            size -= size % source
            return size.coerceAtLeast(source)
        }
        return size.coerceAtLeast(1)
    }

    private fun clearScreen() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private inner class GlPass(val definition: ShaderPassDefinition) {
        val target = RenderTarget()
        val program = GlProgram(
            definition.vertexSource ?: DEFAULT_VERTEX,
            definition.fragmentSource ?: PASSTHROUGH_FRAGMENT,
        )

        fun release() {
            target.release()
            program.release()
        }
    }

    private class RenderTarget {
        val texture = createTexture()
        val framebuffer = IntArray(1).also { GLES20.glGenFramebuffers(1, it, 0) }[0]
        private var width = 0
        private var height = 0

        fun ensureSize(requiredWidth: Int, requiredHeight: Int, maximumTextureSize: Int) {
            require(requiredWidth <= maximumTextureSize && requiredHeight <= maximumTextureSize) {
                "Shader output ${requiredWidth}×$requiredHeight exceeds the device texture limit " +
                    maximumTextureSize
            }
            if (width == requiredWidth && height == requiredHeight) return
            width = requiredWidth
            height = requiredHeight
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null,
            )
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                texture,
                0,
            )
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
                "OpenGL framebuffer is incomplete"
            }
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        fun release() {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        }
    }

    private class GlProgram(vertexSource: String, fragmentSource: String) {
        val program = GLES20.glCreateProgram()

        init {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            check(status[0] == GLES20.GL_TRUE) { "OpenGL program link failed: $log" }
        }

        fun attribute(name: String): Int = GLES20.glGetAttribLocation(program, name).also {
            check(it >= 0) { "Shader attribute $name does not exist" }
        }

        fun uniform(name: String): Int = GLES20.glGetUniformLocation(program, name)

        fun setUniform(definition: ShaderUniformDefinition, values: List<Float>) {
            val location = uniform(definition.name)
            if (location < 0) return
            when (definition.kind) {
                ShaderUniformKind.FLOAT -> GLES20.glUniform1f(location, values[0])
                ShaderUniformKind.FLOAT2 -> GLES20.glUniform2f(location, values[0], values[1])
                ShaderUniformKind.FLOAT3 -> GLES20.glUniform3f(location, values[0], values[1], values[2])
                ShaderUniformKind.INT -> GLES20.glUniform1i(location, values[0].roundToInt())
            }
        }

        fun release() {
            GLES20.glDeleteProgram(program)
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            val header = when {
                type == GLES20.GL_VERTEX_SHADER -> VERTEX_HEADER
                source.contains("out vec4 FragColor;") -> FRAGMENT_HEADER_WITH_OWN_OUTPUT
                else -> FRAGMENT_HEADER
            }
            GLES20.glShaderSource(shader, header + normalizeForGles(source))
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            val log = GLES20.glGetShaderInfoLog(shader)
            check(status[0] == GLES20.GL_TRUE) { "OpenGL shader compile failed: $log" }
            return shader
        }
    }

    private data class Viewport(val x: Int, val y: Int, val width: Int, val height: Int)

    private companion object {
        const val VERTEX_HEADER = """#version 300 es
            precision highp float;
            #define attribute in
            #define varying out
        """
        const val FRAGMENT_HEADER = """#version 300 es
            precision highp float;
            #define varying in
            #define texture2D texture
            out vec4 FragColor;
            #define gl_FragColor FragColor
        """
        const val FRAGMENT_HEADER_WITH_OWN_OUTPUT = """#version 300 es
            precision highp float;
            #define varying in
            #define texture2D texture
        """
        const val DEFAULT_VERTEX = """
            attribute vec4 position;
            varying vec2 texCoord;
            void main() {
                gl_Position = position;
                texCoord = (position.st + vec2(1.0, 1.0)) * vec2(0.5, 0.5);
            }
        """
        const val PREPROCESS_VERTEX = """
            attribute vec4 position;
            varying vec2 texCoord;
            void main() {
                gl_Position = position;
                texCoord = (position.st + vec2(1.0, -1.0)) * vec2(0.5, -0.5);
            }
        """
        const val PASSTHROUGH_FRAGMENT = """
            varying vec2 texCoord;
            uniform sampler2D tex;
            void main() {
                gl_FragColor = texture2D(tex, texCoord);
            }
        """
        const val PREPROCESS_FRAGMENT = """
            varying vec2 texCoord;
            uniform sampler2D tex;
            void main() {
                gl_FragColor = texture2D(tex, texCoord).bgra;
            }
        """

        fun createTexture(): Int {
            val texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            return texture
        }

        fun floatBufferOf(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(values)
                .apply { position(0) }

        fun normalizeForGles(source: String): String = source
            .replace("2 * vec2(c)", "2.0 * vec2(c)")
            .replace("2 * texSize", "2.0 * texSize")
            .replace("texCoord * texSize * 2", "texCoord * texSize * 2.0")
            .replace("mod(pixel_coords2.x,2) == 0", "(pixel_coords2.x % 2) == 0")
            .replace("mod(pixel_coords2.y,2) == 0", "(pixel_coords2.y % 2) == 0")
            .replace("c == 0 ?", "c == 0.0 ?")
            .replace("lineLengthSq <= 0", "lineLengthSq <= 0.0")
            .replace("SubpixelLightGlow <= 0", "SubpixelLightGlow <= 0.0")
            .replace("pixel_size / 2", "pixel_size / 2.0")
            .replace("2 * delta", "2.0 * delta")
    }
}
