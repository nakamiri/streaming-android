package com.reaream.app.streaming

import com.reaream.app.data.model.StreamConfig
import com.reaream.app.streaming.StreamingEngine.ConnectionQuality
import com.reaream.app.streaming.StreamingEngine.StreamState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
    }

    @Test
    fun `startStreaming with blank URL sets error`() {
        val engine = StreamingEngine()
        val config = StreamConfig(url = "", streamKey = "key123")

        engine.startStreaming(config)

        val state = engine.state.value
        assertFalse(state.isStreaming)
        assertFalse(state.isConnecting)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("URL"))
        engine.release()
    }

    @Test
    fun `startStreaming with whitespace-only URL sets error`() {
        val engine = StreamingEngine()
        val config = StreamConfig(url = "   ")

        engine.startStreaming(config)

        assertNotNull(engine.state.value.error)
        engine.release()
    }

    @Test
    fun `clearError removes error from state`() {
        val engine = StreamingEngine()
        val config = StreamConfig(url = "")

        engine.startStreaming(config) // Sets error
        assertNotNull(engine.state.value.error)

        engine.clearError()
        assertNull(engine.state.value.error)
        engine.release()
    }

    @Test
    fun `double startStreaming with blank URL does not duplicate error`() {
        val engine = StreamingEngine()
        val config = StreamConfig(url = "")

        engine.startStreaming(config)
        val error1 = engine.state.value.error

        engine.startStreaming(config)
        val error2 = engine.state.value.error

        assertEquals(error1, error2)
        engine.release()
    }

    @Test
    fun `onAudioData does nothing when not streaming`() {
        val engine = StreamingEngine()
        val data = ByteArray(1024)

        // Should not throw
        engine.onAudioData(data, 0L)
        engine.release()
    }

    @Test
    fun `showError updates state without starting stream`() {
        val engine = StreamingEngine()

        engine.showError("boom")

        assertEquals("boom", engine.state.value.error)
        assertFalse(engine.state.value.isStreaming)
        engine.release()
    }
}
