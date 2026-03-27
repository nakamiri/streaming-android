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
)

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
