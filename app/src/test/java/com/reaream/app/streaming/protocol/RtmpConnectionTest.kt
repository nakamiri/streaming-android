package com.reaream.app.streaming.protocol

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class RtmpConnectionTest {

    private val connection = RtmpConnection("rtmp://localhost/live")

    // --- parseSpsPps tests ---

    @Test
    fun `parseSpsPps extracts SPS and PPS with 4-byte start codes`() {
        // SPS NAL type = 0x67 (type 7), PPS NAL type = 0x68 (type 8)
        val data = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1e, // SPS: start code + NAL
            0, 0, 0, 1, 0x68, 0xce.toByte(), 0x38, // PPS: start code + NAL
        )

        val result = connection.parseSpsPps(data)
        assertNotNull(result)
        val (sps, pps) = result!!

        assertEquals(4, sps.remaining()) // 0x67, 0x42, 0x00, 0x1e
        assertEquals(0x67.toByte(), sps.get(0))

        assertEquals(3, pps.remaining()) // 0x68, 0xce, 0x38
        assertEquals(0x68.toByte(), pps.get(0))
    }

    @Test
    fun `parseSpsPps works with 3-byte start codes`() {
        val data = byteArrayOf(
            0, 0, 1, 0x67, 0x01, 0x02, // SPS
            0, 0, 1, 0x68, 0x03,       // PPS
        )

        val result = connection.parseSpsPps(data)
        assertNotNull(result)
        val (sps, pps) = result!!

        assertEquals(0x67.toByte(), sps.get(0))
        assertEquals(0x68.toByte(), pps.get(0))
    }

    @Test
    fun `parseSpsPps returns null when SPS is missing`() {
        // Only PPS, no SPS
        val data = byteArrayOf(
            0, 0, 0, 1, 0x68, 0x01, 0x02,
        )

        val result = connection.parseSpsPps(data)
        assertNull(result)
    }

    @Test
    fun `parseSpsPps returns null when PPS is missing`() {
        // Only SPS, no PPS
        val data = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x01, 0x02,
        )

        val result = connection.parseSpsPps(data)
        assertNull(result)
    }

    @Test
    fun `parseSpsPps returns null for empty data`() {
        val result = connection.parseSpsPps(byteArrayOf())
        assertNull(result)
    }

    @Test
    fun `parseSpsPps returns null for data without start codes`() {
        val data = byteArrayOf(0x67, 0x42, 0x00, 0x1e, 0x68, 0xce.toByte())
        val result = connection.parseSpsPps(data)
        assertNull(result)
    }

    @Test
    fun `parseSpsPps handles mixed 3 and 4 byte start codes`() {
        val data = byteArrayOf(
            0, 0, 0, 1, 0x67, 0xAA.toByte(), 0xBB.toByte(), // SPS with 4-byte start
            0, 0, 1, 0x68, 0xCC.toByte(),                    // PPS with 3-byte start
        )

        val result = connection.parseSpsPps(data)
        assertNotNull(result)
        val (sps, pps) = result!!

        assertEquals(3, sps.remaining())
        assertEquals(2, pps.remaining())
    }

    @Test
    fun `parseSpsPps identifies NAL types by lower 5 bits`() {
        // NAL type is byte & 0x1F. SPS=7, PPS=8
        // 0x67 = 0110_0111 → type 7 (SPS)
        // 0x28 = 0010_1000 → type 8 (PPS)
        val data = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x01, // SPS (nal_ref_idc=3, type=7)
            0, 0, 0, 1, 0x28, 0x02, // PPS (nal_ref_idc=1, type=8)
        )

        val result = connection.parseSpsPps(data)
        assertNotNull(result)
    }

    // --- Initial state tests ---

    @Test
    fun `initial state is not connected`() {
        assertFalse(connection.isConnected)
    }
}
