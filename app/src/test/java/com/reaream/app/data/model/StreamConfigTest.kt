package com.reaream.app.data.model

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

    @Test
    fun `default auth type is stream key`() {
        val config = StreamConfig()
        assertEquals(AuthType.STREAM_KEY, config.authType)
        assertEquals("", config.youtubeChannelId)
        assertEquals("", config.youtubeChannelName)
        assertEquals("", config.youtubeBroadcastTitle)
        assertEquals(YouTubePrivacy.UNLISTED, config.youtubePrivacy)
    }

    @Test
    fun `youtube oauth config serialization round trip`() {
        val config = StreamConfig(
            name = "YouTube (TestChannel)",
            authType = AuthType.YOUTUBE_OAUTH,
            youtubeChannelId = "UC123456",
            youtubeChannelName = "TestChannel",
            youtubeBroadcastTitle = "My Stream",
            youtubePrivacy = YouTubePrivacy.PUBLIC,
        )

        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<StreamConfig>(encoded)

        assertEquals(config, decoded)
        assertEquals(AuthType.YOUTUBE_OAUTH, decoded.authType)
        assertEquals("UC123456", decoded.youtubeChannelId)
        assertEquals(YouTubePrivacy.PUBLIC, decoded.youtubePrivacy)
    }

    @Test
    fun `deserialization without auth fields uses defaults`() {
        val old = """{"name":"Old Stream","url":"rtmp://test","streamKey":"key"}"""
        val config = json.decodeFromString<StreamConfig>(old)
        assertEquals(AuthType.STREAM_KEY, config.authType)
        assertEquals("", config.youtubeChannelId)
        assertEquals(YouTubePrivacy.UNLISTED, config.youtubePrivacy)
    }

    @Test
    fun `youtube privacy enum values`() {
        assertEquals("public", YouTubePrivacy.PUBLIC.apiValue)
        assertEquals("unlisted", YouTubePrivacy.UNLISTED.apiValue)
        assertEquals("private", YouTubePrivacy.PRIVATE.apiValue)
        assertEquals(3, YouTubePrivacy.entries.size)
    }

    @Test
    fun `auth type enum values`() {
        assertEquals(2, AuthType.entries.size)
    }

    @Test
    fun `startValidationError rejects blank url`() {
        val config = StreamConfig(url = "")
        assertNotNull(config.startValidationError())
    }

    @Test
    fun `startValidationError rejects rist`() {
        val config = StreamConfig(url = "rist://example", protocol = StreamProtocol.RIST)
        assertTrue(config.startValidationError()!!.contains("RIST"))
    }

    @Test
    fun `startValidationError rejects h265 over rtmp`() {
        val config = StreamConfig(
            url = "rtmp://example/live",
            protocol = StreamProtocol.RTMP,
            videoCodec = VideoCodec.H265,
        )

        assertTrue(config.startValidationError()!!.contains("H.264"))
    }
}
