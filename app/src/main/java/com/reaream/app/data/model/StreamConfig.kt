package com.reaream.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamConfig(
    val name: String = "Default",
    val url: String = "",
    val streamKey: String = "",
    val protocol: StreamProtocol = StreamProtocol.RTMP,
    val videoBitrate: Int = 5000,
    val audioBitrate: Int = 128,
    val resolution: Resolution = Resolution.HD_1080,
    val fps: Int = 30,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val adaptiveBitrate: Boolean = false,
    val srtLatency: Int = 2000,
    val authType: AuthType = AuthType.STREAM_KEY,
    val youtubeChannelId: String = "",
    val youtubeChannelName: String = "",
    val youtubeBroadcastTitle: String = "",
    val youtubePrivacy: YouTubePrivacy = YouTubePrivacy.UNLISTED,
    val youtubeLatency: YouTubeLatency = YouTubeLatency.NORMAL,
    val youtubeAutoStart: Boolean = true,
    val youtubeAutoStop: Boolean = true,
)

@Serializable
enum class AuthType {
    STREAM_KEY,
    YOUTUBE_OAUTH,
}

@Serializable
enum class YouTubePrivacy(val apiValue: String, val displayName: String) {
    PUBLIC("public", "公開"),
    UNLISTED("unlisted", "限定公開"),
    PRIVATE("private", "非公開"),
}

@Serializable
enum class YouTubeLatency(val apiValue: String, val displayName: String, val description: String) {
    NORMAL("normal", "通常", "安定性重視・遅延15〜30秒"),
    LOW("low", "低遅延", "遅延7〜15秒"),
    ULTRA_LOW("ultraLow", "超低遅延", "遅延2〜7秒・高ビットレート非対応"),
}

@Serializable
enum class StreamProtocol(val displayName: String) {
    RTMP("RTMP"),
    RTMPS("RTMPS"),
    SRT("SRT"),
    RIST("RIST"),
}

@Serializable
enum class Resolution(val width: Int, val height: Int, val displayName: String) {
    HD_720(1280, 720, "720p"),
    HD_1080(1920, 1080, "1080p"),
    UHD_4K(3840, 2160, "4K"),
}

@Serializable
enum class VideoCodec(val displayName: String) {
    H264("H.264"),
    H265("H.265 (HEVC)"),
}
