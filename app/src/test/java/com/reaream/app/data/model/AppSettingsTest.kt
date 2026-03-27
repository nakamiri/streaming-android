package com.reaream.app.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `default settings has one stream`() {
        val settings = AppSettings()
        assertEquals(1, settings.streams.size)
        assertEquals(0, settings.selectedStreamIndex)
    }

    @Test
    fun `currentStream returns selected stream`() {
        val streams = listOf(
            StreamConfig(name = "Stream 1"),
            StreamConfig(name = "Stream 2"),
            StreamConfig(name = "Stream 3"),
        )
        val settings = AppSettings(streams = streams, selectedStreamIndex = 1)
        assertEquals("Stream 2", settings.currentStream.name)
    }

    @Test
    fun `currentStream falls back to default when index out of bounds`() {
        val settings = AppSettings(
            streams = listOf(StreamConfig(name = "Only")),
            selectedStreamIndex = 5,
        )
        assertEquals("Default", settings.currentStream.name)
    }

    @Test
    fun `settings serialization round trip`() {
        val settings = AppSettings(
            streams = listOf(
                StreamConfig(name = "S1", url = "rtmp://a"),
                StreamConfig(name = "S2", url = "srt://b", protocol = StreamProtocol.SRT),
            ),
            selectedStreamIndex = 1,
            camera = CameraSettings(useFrontCamera = true, zoomLevel = 2.5f),
            audio = AudioSettings(muted = true, gain = 0.5f),
            display = DisplaySettings(showChat = false, showFps = false),
            chat = ChatSettings(twitchChannelName = "testchannel"),
            recording = RecordingSettings(enabled = true, videoBitrate = 8000),
        )

        val encoded = json.encodeToString(settings)
        val decoded = json.decodeFromString<AppSettings>(encoded)

        assertEquals(settings, decoded)
    }

    @Test
    fun `camera settings defaults`() {
        val camera = CameraSettings()
        assertFalse(camera.useFrontCamera)
        assertTrue(camera.mirrorFrontCamera)
        assertFalse(camera.videoStabilization)
        assertTrue(camera.autoFocus)
        assertEquals(1f, camera.zoomLevel, 0.001f)
    }

    @Test
    fun `audio settings defaults`() {
        val audio = AudioSettings()
        assertFalse(audio.muted)
        assertEquals(1f, audio.gain, 0.001f)
    }

    @Test
    fun `display settings defaults are all true`() {
        val display = DisplaySettings()
        assertTrue(display.showChat)
        assertTrue(display.showStreamInfo)
        assertTrue(display.showAudioLevel)
        assertTrue(display.showBitrate)
        assertTrue(display.showFps)
        assertTrue(display.showUptime)
    }

    @Test
    fun `chat settings defaults`() {
        val chat = ChatSettings()
        assertTrue(chat.enabled)
        assertEquals(14, chat.fontSize)
        assertEquals("", chat.twitchChannelName)
        assertEquals("", chat.youtubeVideoId)
    }
}
