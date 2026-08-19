/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import io.mgba.android.settings.EmulatorSettings
import io.mgba.android.shader.ShaderDefinition
import io.mgba.android.R
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@SuppressLint("ViewConstructor")
class FrameView(
    context: Context,
    shaderCatalog: List<ShaderDefinition>,
    onShaderError: (String) -> Unit,
) : GLSurfaceView(context) {
    private val frameRenderer = FrameRenderer(context.applicationContext, shaderCatalog, onShaderError)

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(frameRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun applySettings(settings: EmulatorSettings) {
        frameRenderer.applySettings(settings)
        requestRender()
    }

    fun render(pixels: IntArray, width: Int, height: Int) {
        frameRenderer.submit(pixels, width, height)
        requestRender()
    }
}

private class FrameRenderer(
    private val context: Context,
    private val shaderCatalog: List<ShaderDefinition>,
    private val onShaderError: (String) -> Unit,
) : GLSurfaceView.Renderer {
    private val lock = Any()
    private var pendingPixels: IntArray? = null
    private var previousPixels: IntArray? = null
    private var pendingWidth = 0
    private var pendingHeight = 0
    private var lockAspectRatio = true
    private var integerScaling = true
    private var interframeBlending = false
    private var linearFiltering = false
    private var shaderId = ""
    private var shaderParameters = emptyMap<String, Float>()
    private var pipeline: ShaderPipeline? = null
    private var failedShaderId: String? = null
    private var appliedShaderId: String? = null

    fun applySettings(settings: EmulatorSettings) {
        synchronized(lock) {
            lockAspectRatio = settings.lockAspectRatio
            integerScaling = settings.integerScaling
            interframeBlending = settings.interframeBlending
            linearFiltering = settings.linearFiltering
            if (shaderId != settings.shaderId) failedShaderId = null
            shaderId = settings.shaderId
            shaderParameters = settings.shaderParameters
        }
    }

    fun submit(pixels: IntArray, width: Int, height: Int) {
        val incoming = pixels.copyOf(width * height)
        synchronized(lock) {
            pendingPixels = if (interframeBlending && previousPixels?.size == incoming.size) {
                blend(previousPixels!!, incoming)
            } else {
                incoming
            }
            previousPixels = incoming
            pendingWidth = width
            pendingHeight = height
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        pipeline = ShaderPipeline(shaderCatalog)
        failedShaderId = null
        appliedShaderId = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        pipeline?.surfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val frame: IntArray?
        val width: Int
        val height: Int
        val aspect: Boolean
        val integer: Boolean
        val filtering: Boolean
        val selectedShader: String
        val parameters: Map<String, Float>
        synchronized(lock) {
            frame = pendingPixels
            pendingPixels = null
            width = pendingWidth
            height = pendingHeight
            aspect = lockAspectRatio
            integer = integerScaling
            filtering = linearFiltering
            selectedShader = shaderId
            parameters = shaderParameters
        }
        val renderer = pipeline ?: return
        if (selectedShader != appliedShaderId) {
            val error = renderer.selectShader(selectedShader)
            appliedShaderId = selectedShader
            if (error != null) {
                failedShaderId = selectedShader
                Log.e(LOG_TAG, error)
                onShaderError(error)
            } else if (selectedShader.isNotBlank()) {
                Log.i(LOG_TAG, "Loaded shader $selectedShader")
            }
        }
        runCatching {
            renderer.draw(frame, width, height, aspect, integer, filtering, parameters)
        }.onFailure { error ->
            if (failedShaderId != selectedShader) {
                failedShaderId = selectedShader
                appliedShaderId = selectedShader
                renderer.selectShader("")
                val message = context.getString(
                    R.string.message_shader_failed,
                    error.message ?: error.javaClass.simpleName,
                )
                Log.e(LOG_TAG, message, error)
                onShaderError(message)
            }
        }
    }

    private fun blend(previous: IntArray, current: IntArray): IntArray =
        IntArray(current.size) { index ->
            val before = previous[index]
            val after = current[index]
            val red = (((before shr 16) and 0xFF) + ((after shr 16) and 0xFF)) / 2
            val green = (((before shr 8) and 0xFF) + ((after shr 8) and 0xFF)) / 2
            val blue = ((before and 0xFF) + (after and 0xFF)) / 2
            (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }

    private companion object {
        const val LOG_TAG = "mGBA-Shaders"
    }
}
