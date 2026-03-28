package com.reaream.app.data.model

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
    val widgets: WidgetSettings = WidgetSettings(),
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

@Serializable
data class WidgetSettings(
    val clockWidget: ClockWidgetConfig = ClockWidgetConfig(),
    val locationWidget: LocationWidgetConfig = LocationWidgetConfig(),
    val speedWidget: SpeedWidgetConfig = SpeedWidgetConfig(),
)

@Serializable
data class ClockWidgetConfig(
    val enabled: Boolean = false,
    val format: ClockFormat = ClockFormat.HH_MM_SS,
    val x: Float = 1.0f,
    val y: Float = 0.01f,
    val fontSize: Int = 14,
)

@Serializable
data class LocationWidgetConfig(
    val enabled: Boolean = false,
    val x: Float = 1.0f,
    val y: Float = 0.10f,
    val fontSize: Int = 12,
)

@Serializable
data class SpeedWidgetConfig(
    val enabled: Boolean = false,
    val x: Float = 1.0f,
    val y: Float = 0.055f,
    val fontSize: Int = 14,
    val unit: SpeedUnit = SpeedUnit.KMH,
)

@Serializable
enum class SpeedUnit(val displayName: String, val label: String) {
    KMH("km/h", "km/h"),
    MPH("mph", "mph"),
}

@Serializable
enum class ClockFormat(val pattern: String, val displayName: String) {
    HH_MM("HH:mm", "24時間 (HH:mm)"),
    HH_MM_SS("HH:mm:ss", "24時間 (HH:mm:ss)"),
    TWELVE_HOUR("hh:mm a", "12時間 (hh:mm a)"),
}
