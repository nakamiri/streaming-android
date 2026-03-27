package com.moblin.android.streaming

import com.moblin.android.streaming.StreamingEngine.ConnectionQuality
import com.moblin.android.streaming.StreamingEngine.StreamState
import org.junit.Assert.*
import org.junit.Test

class StreamingEngineTest {

    @Test
    fun `default stream state is not streaming`() {
        val state = StreamState()
        assertFalse(state.isStreaming)
        assertFalse(state.isConnecting)
        assertEquals(0, state.bitrateKbps)
        assertEquals(0, state.fps)
        assertEquals(0L, state.uptime)
        assertNull(state.error)
        assertEquals(ConnectionQuality.UNKNOWN, state.connectionQuality)
    }

    @Test
    fun `stream state copy preserves values`() {
        val state = StreamState(
            isStreaming = true,
            bitrateKbps = 5000,
            uptime = 120L,
            connectionQuality = ConnectionQuality.GOOD,
        )

        val updated = state.copy(bitrateKbps = 6000)

        assertTrue(updated.isStreaming)
        assertEquals(6000, updated.bitrateKbps)
        assertEquals(120L, updated.uptime)
        assertEquals(ConnectionQuality.GOOD, updated.connectionQuality)
    }

    @Test
    fun `initial engine state is idle`() {
        val engine = StreamingEngine()
        val state = engine.state.value
        assertFalse(state.isStreaming)
        assertFalse(state.isConnecting)
        engine.release()
    }

    @Test
    fun `connection quality enum values`() {
        assertEquals(4, ConnectionQuality.entries.size)
        assertNotNull(ConnectionQuality.valueOf("UNKNOWN"))
        assertNotNull(ConnectionQuality.valueOf("GOOD"))
        assertNotNull(ConnectionQuality.valueOf("FAIR"))
        assertNotNull(ConnectionQuality.valueOf("POOR"))
    }

    @Test
    fun `stream state with error`() {
        val state = StreamState(error = "Connection timeout")
        assertEquals("Connection timeout", state.error)
        assertFalse(state.isStreaming)
    }

    @Test
    fun `stopStreaming on idle engine does not crash`() {
        val engine = StreamingEngine()
        engine.stopStreaming()
        assertFalse(engine.state.value.isStreaming)
        engine.release()
    }

    @Test
    fun `release on idle engine does not crash`() {
        val engine = StreamingEngine()
        engine.release()
        // Should not throw
    }
}
