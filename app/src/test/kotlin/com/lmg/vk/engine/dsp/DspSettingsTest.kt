package com.lmg.vk.engine.dsp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DspSettingsTest {
    @Test fun `settings and every filter survive persistence`() {
        val settings = DspSettings(enabled = true, mode = 2, preamp = -3f,
            bands = List(8) { DspBand(type = it % 6, frequency = 60f * (it + 1), q = 2f, gain = 4f) })
        assertEquals(settings, Json.decodeFromString<DspSettings>(Json.encodeToString(settings)))
    }
    @Test fun `old or malformed snapshots are bounded before reaching native code`() {
        val s = DspSettings(mode = 100, preamp = Float.NaN, graphic = listOf(100f),
            bands = listOf(DspBand(frequency = Float.POSITIVE_INFINITY, q = 0f)),
            threshold = -1f, ceiling = -8f).normalized()
        assertEquals(10, s.graphic.size); assertEquals(8, s.bands.size)
        assertEquals(18f, s.graphic[0], 0f); assertEquals(0f, s.preamp, 0f)
        assertTrue(s.bands.all { it.frequency.isFinite() && it.q in 0.1f..20f })
        assertTrue(s.threshold <= s.ceiling)
        assertTrue(s.toNativeParameters().all { it.isFinite() })
    }
    @Test fun `wire includes last graphic band and last parametric band`() {
        val s = DspSettings(enabled = true, graphic = List(10) { it.toFloat() },
            bands = List(8) { DspBand(type = 5, frequency = 12000f, gain = -4f, q = 3f, slope = 0.5f) })
        val wire = s.toNativeParameters()
        assertEquals(67, wire.size); assertEquals(1f, wire[0], 0f); assertEquals(9f, wire[18], 0f)
        assertEquals(5f, wire[62], 0f); assertEquals(12000f, wire[63], 0f); assertEquals(0.5f, wire[66], 0f)
    }
    @Test fun `reset defaults to transparent master bypass`() {
        val s = DspSettings()
        assertFalse(s.enabled);assertEquals(0f, s.toNativeParameters()[0], 0f)
        assertEquals(s, s.normalized())
    }
}
