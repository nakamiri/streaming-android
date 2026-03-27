package com.moblin.android.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StreamConfigTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `default stream config has expected values`() {
        val config = StreamConfig()
        assertEquals("Default", config.name)
        assertEquals("", config.url)
        assertEquals("", config.streamKey)
        assertEquals(StreamProtocol.RTMP, config.protocol)
        assertEquals(5000, config.videoBitrate)
        assertEquals(128, config.audioBitrate)
        assertEquals(Resolution.HD_1080, config.resolution)
        assertEquals(30, config.fps)
        assertEquals(VideoCodec.H264, config.videoCodec)
        assertFalse(config.adaptiveBitrate)
        assertEquals(2000, config.srtLatency)
    }

    @Test
    fun `stream config serialization round trip`() {
        val config = StreamConfig(
            name = "Twitch Stream",
            url = "rtmp://live.twitch.tv/app",
            streamKey = "live_abc123",
            protocol = StreamProtocol.RTMP,
            videoBitrate = 6000,
            audioBitrate = 160,
            resolution = Resolution.HD_720,
            fps = 60,
            videoCodec = VideoCodec.H265,
            adaptiveBitrate = true,
            srtLatency = 3000,
        )

        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<StreamConfig>(encoded)

        assertEquals(config, decoded)
    }

    @Test
    fun `deserialization with unknown keys does not fail`() {
        val jsonString = """{"name":"Test","url":"rtmp://test","unknownField":"value"}"""
        val config = json.decodeFromString<StreamConfig>(jsonString)
        assertEquals("Test", config.name)
        assertEquals("rtmp://test", config.url)
    }

    @Test
    fun `resolution has correct dimensions`() {
        assertEquals(1280, Resolution.HD_720.width)
        assertEquals(720, Resolution.HD_720.height)
        assertEquals(1920, Resolution.HD_1080.width)
        assertEquals(1080, Resolution.HD_1080.height)
        assertEquals(3840, Resolution.UHD_4K.width)
        assertEquals(2160, Resolution.UHD_4K.height)
    }

    @Test
    fun `protocol display names are correct`() {
        assertEquals("RTMP", StreamProtocol.RTMP.displayName)
        assertEquals("RTMPS", StreamProtocol.RTMPS.displayName)
        assertEquals("SRT", StreamProtocol.SRT.displayName)
        assertEquals("RIST", StreamProtocol.RIST.displayName)
    }

    @Test
    fun `video codec display names are correct`() {
        assertEquals("H.264", VideoCodec.H264.displayName)
        assertEquals("H.265 (HEVC)", VideoCodec.H265.displayName)
    }
}
