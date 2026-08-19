/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.shader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderManifestParserTest {
    @Test
    fun `parses multipass shader and vector uniforms`() {
        val manifest = """
            [shader]
            name=Example
            author=Test
            description=Two passes
            passes=2

            [pass.0]
            fragmentShader=first.fs
            width=-3
            blend=1

            [pass.1]
            fragmentShader=second.fs

            [pass.1.uniform.offset]
            type=float2
            default[0]=0.25
            default[1]=0.5
            readableName=Offset
        """.trimIndent()

        val shader = ShaderManifestParser.parse("example", manifest) { "source:$it" }

        assertEquals("Example", shader.name)
        assertEquals(2, shader.passes.size)
        assertEquals(-3, shader.passes[0].width)
        assertTrue(shader.passes[0].blend)
        assertEquals(listOf(0.25f, 0.5f), shader.passes[1].uniforms.single().defaults)
    }

    @Test
    fun `all bundled shader manifests parse through the Android model`() {
        val rootUrl = requireNotNull(javaClass.classLoader?.getResource("shaders"))
        val root = File(rootUrl.toURI())
        val definitions = root.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.endsWith(".shader") }
            .map { directory ->
                ShaderManifestParser.parse(
                    directory.name.removeSuffix(".shader"),
                    File(directory, "manifest.ini").readText(),
                ) { fileName -> File(directory, fileName).readText() }
            }

        assertEquals(28, definitions.size)
        assertEquals(53, definitions.sumOf { shader -> shader.passes.sumOf { it.uniforms.size } })
        assertEquals(2, definitions.maxOf { it.passes.size })
    }
}
