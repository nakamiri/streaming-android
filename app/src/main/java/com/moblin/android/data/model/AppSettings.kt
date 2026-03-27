package com.moblin.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val streams: List<StreamConfig> = listOf(StreamConfig()),
    val selectedStreamIndex: Int = 0,
    val camera: CameraSettings = CameraSettings(),
    val audio: AudioSettings = AudioSettings(),
    val display: DisplaySettings = DisplaySettings(),
    val chat: ChatSettings = ChatSettings(),
    val recording: RecordingSettings = RecordingSettings(),
) {
    val currentStream: StreamConfig
        get() = streams.getOrElse(selectedStreamIndex) { StreamConfig() }
}

@Serializable
data class CameraSettings(
    val useFrontCamera: Boolean = false,
    val mirrorFrontCamera: Boolean = true,
    val videoStabilization: Boolean = false,
    val autoFocus: Boolean = true,
    val zoomLevel: Float = 1f,
)

@Serializable
data class AudioSettings(
    val muted: Boolean = false,
    val gain: Float = 1f,
)

@Serializable
data class DisplaySettings(
    val showChat: Boolean = true,
    val showStreamInfo: Boolean = true,
    val showAudioLevel: Boolean = true,
    val showBitrate: Boolean = true,
    val showFps: Boolean = true,
    val showUptime: Boolean = true,
)

@Serializable
data class ChatSettings(
    val enabled: Boolean = true,
    val fontSize: Int = 14,
    val twitchChannelName: String = "",
    val twitchAccessToken: String = "",
    val youtubeVideoId: String = "",
)

@Serializable
data class RecordingSettings(
    val enabled: Boolean = false,
    val videoBitrate: Int = 10000,
)
