package com.lmg.vk.ui.effects

import org.junit.Assert.*
import org.junit.Test

class DustParticleMeshTest {
    @Test fun beginsWithCompleteOriginalImage() {
        val mesh = DustParticleMesh(1080, 780, 3f)
        assertArrayEquals(mesh.textureCoordinates, mesh.vertices, .001f)
        assertTrue(mesh.colors.all { it ushr 24 == 255 })
    }

    @Test fun finishesCompletelyTransparent() {
        val mesh = DustParticleMesh(1080, 780, 3f)
        mesh.update(1f)
        assertTrue(mesh.colors.all { it ushr 24 == 0 })
        assertTrue(mesh.vertices.all { it.isFinite() })
    }

    @Test fun particlesFadeWithoutReappearingAndReuseBuffers() {
        val mesh = DustParticleMesh(1080, 180, 3f, 2200)
        val vertices = mesh.vertices
        val colors = mesh.colors
        val texture = mesh.textureCoordinates.copyOf()
        var previous = colors.map { it ushr 24 }
        for (step in 1..20) {
            mesh.update(step / 20f)
            val next = colors.map { it ushr 24 }
            assertTrue(next.indices.all { next[it] <= previous[it] })
            previous = next
        }
        assertSame(vertices, mesh.vertices)
        assertSame(colors, mesh.colors)
        assertArrayEquals(texture, mesh.textureCoordinates, 0f)
    }

    @Test fun boundsParticleCountForLargeAndNarrowSurfaces() {
        listOf(1 to 1, 8000 to 4000, 10000 to 1, 1 to 10000).forEach { (w, h) ->
            val mesh = DustParticleMesh(w, h, 1f, 2200)
            assertTrue(mesh.count in 1..2200)
            assertEquals(w.toFloat(), mesh.textureCoordinates.filterIndexed { i, _ -> i % 2 == 0 }.maxOrNull()!!, .001f)
            assertEquals(h.toFloat(), mesh.textureCoordinates.filterIndexed { i, _ -> i % 2 == 1 }.maxOrNull()!!, .001f)
        }
    }

    @Test fun progressCanRestartAtOriginalImage() {
        val mesh = DustParticleMesh(100, 100, 1f)
        mesh.update(.8f)
        mesh.update(0f)
        assertArrayEquals(mesh.textureCoordinates, mesh.vertices, .001f)
        assertTrue(mesh.colors.all { it ushr 24 == 255 })
    }
}
