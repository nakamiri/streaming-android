package com.reaream.app.streaming

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCaptureTest {

    @Test
    fun `applyGain scales pcm16 samples`() {
        val pcm = byteArrayOf(
            0xE8.toByte(), 0x03, // 1000
            0x18, 0xFC.toByte(), // -1000
        )

        val result = applyGain(pcm, pcm.size, 0.5f)

        assertArrayEquals(
            byteArrayOf(
                0xF4.toByte(), 0x01, // 500
                0x0C, 0xFE.toByte(), // -500
            ),
            result,
        )
    }

    @Test
    fun `applyGain clamps pcm16 overflow`() {
        val pcm = byteArrayOf(
            0xFF.toByte(), 0x7F, // 32767
            0x00, 0x80.toByte(), // -32768
        )

        val result = applyGain(pcm, pcm.size, 2f)

        assertArrayEquals(pcm, result)
    }

    @Test
    fun `generateTonePcm16 produces non-silent pcm and advances phase`() {
        val generated = generateTonePcm16(
            size = 16,
            gain = 1f,
            angularStep = Math.PI / 4,
            phase = 0.0,
        )

        assertEquals(16, generated.pcm.size)
        assertFalse(generated.pcm.all { it == 0.toByte() })
        assertEquals(0.0, generated.nextPhase, 0.0001)
    }

    @Test
    fun `calculatePcm16Level returns zero for silence`() {
        assertEquals(0f, calculatePcm16Level(ByteArray(8), 8), 0.0001f)
    }

    @Test
    fun `calculatePcm16Level detects non-zero pcm`() {
        val pcm = byteArrayOf(
            0xE8.toByte(), 0x03,
            0x18, 0xFC.toByte(),
        )

        val level = calculatePcm16Level(pcm, pcm.size)

        assertTrue(level > 0f)
    }
}
