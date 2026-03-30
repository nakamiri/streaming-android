package com.reaream.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reaream.app.chat.ChatManager
import com.reaream.app.data.LocationProvider
import com.reaream.app.data.MapTileProvider
import com.reaream.app.data.SettingsRepository
import com.reaream.app.data.YouTubeApiClient
import com.reaream.app.data.YouTubeAuthManager
import com.reaream.app.data.model.*
import com.reaream.app.service.StreamingService
import com.reaream.app.streaming.AudioCapture
import com.reaream.app.streaming.StreamingEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    val streamingEngine = StreamingEngine()
    val chatManager = ChatManager()
    val locationProvider = LocationProvider(application)
    val mapTileProvider = MapTileProvider()
    val audioCapture = AudioCapture { data, timestamp, inputLevel, outputLevel ->
        streamingEngine.onAudioData(data, timestamp, inputLevel, outputLevel)
    }

    val youtubeAuthManager = YouTubeAuthManager(application)
    val youtubeApiClient = YouTubeApiClient(youtubeAuthManager)

    // YouTube broadcast ID for the current session (to end broadcast on stop)
    private var currentYoutubeBroadcastId: String? = null
    private var streamingResourcesActive = false
    private var lastObservedSelectedStreamConfig: StreamConfig? = null

    private val _youtubeLiveUrl = MutableStateFlow<String?>(null)
    val youtubeLiveUrl: StateFlow<String?> = _youtubeLiveUrl.asStateFlow()

    // Broadcast picker dialog state
    data class BroadcastPickerState(
        val isLoading: Boolean = false,
        val isVisible: Boolean = false,
        val broadcasts: List<YouTubeApiClient.BroadcastInfo> = emptyList(),
        val config: StreamConfig? = null,
    )
    private val _broadcastPicker = MutableStateFlow(BroadcastPickerState())
    val broadcastPicker: StateFlow<BroadcastPickerState> = _broadcastPicker.asStateFlow()

    private val _youtubeSetupError = MutableStateFlow<String?>(null)
    val youtubeSetupError: StateFlow<String?> = _youtubeSetupError.asStateFlow()

    // OAuth callback state: emits the redirect URI when received from browser
    private val _oauthCallback = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val oauthCallback = _oauthCallback.asSharedFlow()

    fun clearYoutubeSetupError() {
        _youtubeSetupError.value = null
    }

    fun handleOAuthCallback(uri: Uri) {
        _oauthCallback.tryEmit(uri)
    }

    fun signOutYouTube() {
        youtubeAuthManager.signOut()
    }

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val streamState: StateFlow<StreamingEngine.StreamState> = streamingEngine.state

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _screenBlackoutEnabled = MutableStateFlow(false)
    val screenBlackoutEnabled: StateFlow<Boolean> = _screenBlackoutEnabled.asStateFlow()

    init {
        // Sync widget settings and location data to streaming engine
        viewModelScope.launch {
            settings.collect { s ->
                streamingEngine.widgetSettingsRef.set(s.widgets)
                audioCapture.isMuted = s.audio.muted
                audioCapture.gain = s.audio.gain
                audioCapture.inputMode = s.audio.inputMode
                audioCapture.toneFrequencyHz = s.audio.toneFrequencyHz

                val selectedStream = s.streams.getOrNull(s.selectedStreamIndex)
                if (selectedStream != null && selectedStream != lastObservedSelectedStreamConfig) {
                    lastObservedSelectedStreamConfig = selectedStream
                    if (streamState.value.isStreaming || streamState.value.isConnecting) {
                        streamingEngine.updateLiveStreamConfig(buildLiveStreamConfig(selectedStream))
                    }
                }

                // Start/stop location updates based on widget config
                if (s.widgets.locationWidget.enabled || s.widgets.speedWidget.enabled || s.widgets.mapWidget.enabled) {
                    locationProvider.startUpdates()
                } else {
                    locationProvider.stopUpdates()
                }

                // Re-fetch map when zoom or marker settings change
                val loc = locationProvider.location.value
                if (loc != null && s.widgets.mapWidget.enabled) {
                    mapTileProvider.updateLocation(loc, s.widgets.mapWidget.zoom, s.widgets.mapWidget.showMarker)
                }
            }
        }
        viewModelScope.launch {
            locationProvider.location.collect { loc ->
                streamingEngine.widgetRenderer.currentLocation.set(loc)
                if (loc != null) {
                    val mapConfig = settings.value.widgets.mapWidget
                    mapTileProvider.updateLocation(loc, mapConfig.zoom, mapConfig.showMarker)
                }
            }
        }
        viewModelScope.launch {
            mapTileProvider.mapBitmap.collect { bmp ->
                streamingEngine.widgetRenderer.currentMapBitmap.set(bmp)
            }
        }
        viewModelScope.launch {
            locationProvider.speedKmh.collect { speed ->
                streamingEngine.widgetRenderer.currentSpeedKmh.set(speed)
            }
        }
        viewModelScope.launch {
            locationProvider.address.collect { addr ->
                streamingEngine.widgetRenderer.currentAddress.set(addr)
            }
        }
        viewModelScope.launch {
            streamingEngine.state.collect { state ->
                updateStreamingNotification(state)
                if (!state.isStreaming && !state.isConnecting && streamingResourcesActive) {
                    stopStreamingResources()
                }
                if (!state.isStreaming && !state.isConnecting && _screenBlackoutEnabled.value) {
                    _screenBlackoutEnabled.value = false
                }
            }
        }
    }

    private val _screenStack = mutableListOf<Screen>(Screen.Stream)
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Stream)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Stop confirmation dialog state (shown for YouTube OAuth streams)
    private val _stopConfirmVisible = MutableStateFlow(false)
    val stopConfirmVisible: StateFlow<Boolean> = _stopConfirmVisible.asStateFlow()

    fun toggleStreaming() {
        val state = streamState.value
        if (state.isStreaming || state.isConnecting) {
            if (currentYoutubeBroadcastId != null) {
                _stopConfirmVisible.value = true
            } else {
                stopStreaming(endBroadcast = false)
            }
        } else {
            startStreaming()
        }
    }

    fun dismissStopConfirm() {
        _stopConfirmVisible.value = false
    }

    fun confirmStop(endBroadcast: Boolean) {
        _stopConfirmVisible.value = false
        stopStreaming(endBroadcast = endBroadcast)
    }

    private fun startStreaming() {
        val config = settings.value.currentStream

        if (config.authType == AuthType.YOUTUBE_OAUTH) {
            startYouTubeOAuthStreaming(config)
            return
        }

        // Validate URL before starting anything
        if (config.url.isBlank()) {
            streamingEngine.startStreaming(config) // Will set error state
            return
        }

        startStreamingWithConfig(config)
    }

    private fun startYouTubeOAuthStreaming(config: StreamConfig) {
        viewModelScope.launch {
            _youtubeSetupError.value = null
            _broadcastPicker.value = BroadcastPickerState(isLoading = true, isVisible = true, config = config)

            // Fetch existing broadcasts (upcoming + live)
            val upcoming = youtubeApiClient.listBroadcasts("upcoming").getOrDefault(emptyList())
            val live = youtubeApiClient.listBroadcasts("active").getOrDefault(emptyList())
            val all = live + upcoming

            _broadcastPicker.value = BroadcastPickerState(
                isVisible = true,
                broadcasts = all,
                config = config,
            )
        }
    }

    fun dismissBroadcastPicker() {
        _broadcastPicker.value = BroadcastPickerState()
    }

    fun startWithBroadcast(existingBroadcastId: String?) {
        val config = _broadcastPicker.value.config ?: return
        _broadcastPicker.value = BroadcastPickerState()

        viewModelScope.launch {
            _youtubeSetupError.value = null
            Log.d("MainViewModel", "Starting YouTube OAuth streaming, existingBroadcast=$existingBroadcastId")

            val resolutionStr = when (config.resolution) {
                Resolution.HD_720 -> "720p"
                Resolution.HD_1080 -> "1080p"
                Resolution.UHD_4K -> "2160p"
            }

            val title = config.youtubeBroadcastTitle.ifBlank { "Live Stream" }

            val result = youtubeApiClient.setupAndGetIngestion(
                title = title,
                privacyStatus = config.youtubePrivacy.apiValue,
                resolution = resolutionStr,
                fps = config.fps,
                existingBroadcastId = existingBroadcastId,
                latencyPreference = config.youtubeLatency.apiValue,
                enableAutoStart = config.youtubeAutoStart,
                enableAutoStop = config.youtubeAutoStop,
            )

            result.onSuccess { (broadcastId, ingestion) ->
                Log.d("MainViewModel", "YouTube setup success: broadcastId=$broadcastId, rtmpUrl=${ingestion.rtmpUrl}")
                currentYoutubeBroadcastId = broadcastId
                _youtubeLiveUrl.value = "https://youtube.com/watch?v=$broadcastId"
                val oauthConfig = config.copy(
                    url = ingestion.rtmpUrl,
                    streamKey = ingestion.streamKey,
                )
                startStreamingWithConfig(oauthConfig)
            }.onFailure { error ->
                Log.e("MainViewModel", "YouTube setup failed", error)
                _youtubeSetupError.value = error.message ?: "YouTube 配信のセットアップに失敗しました"
            }
        }
    }

    private fun startStreamingWithConfig(config: StreamConfig) {
        // Clear any stale YouTube broadcast ID when starting a non-OAuth stream,
        // to avoid the stop confirmation dialog appearing for unrelated sessions.
        if (config.authType != AuthType.YOUTUBE_OAUTH) {
            currentYoutubeBroadcastId = null
            _youtubeLiveUrl.value = null
        }

        config.startValidationError()?.let { error ->
            streamingEngine.showError(error)
            return
        }

        val context = getApplication<Application>()

        // Start audio capture
        audioCapture.isMuted = settings.value.audio.muted
        audioCapture.gain = settings.value.audio.gain
        audioCapture.inputMode = settings.value.audio.inputMode
        audioCapture.toneFrequencyHz = settings.value.audio.toneFrequencyHz
        if (!audioCapture.start(context)) {
            streamingEngine.showError("音声入力の初期化に失敗したため、配信を開始できません。")
            return
        }

        startStreamingService(context)
        streamingResourcesActive = true

        // Connect chat
        val chatSettings = settings.value.chat
        if (chatSettings.enabled && chatSettings.twitchChannelName.isNotBlank()) {
            chatManager.connectTwitch(chatSettings.twitchChannelName)
        }

        // Start streaming
        streamingEngine.startStreaming(config)
    }

    private fun stopStreaming(endBroadcast: Boolean) {
        val context = getApplication<Application>()

        streamingEngine.stopStreaming()
        stopStreamingResources(context)

        val broadcastId = currentYoutubeBroadcastId
        if (broadcastId != null) {
            if (endBroadcast) {
                viewModelScope.launch {
                    youtubeApiClient.transitionBroadcast(broadcastId, "complete")
                    currentYoutubeBroadcastId = null
                    _youtubeLiveUrl.value = null
                }
            }
            // If not ending, keep broadcastId so user can reconnect via the picker
        }

    }

    private fun startStreamingService(context: Application) {
        val intent = Intent(context, StreamingService::class.java).apply {
            action = StreamingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    private fun stopStreamingResources(context: Application = getApplication()) {
        if (!streamingResourcesActive) return
        streamingResourcesActive = false
        audioCapture.stop()
        chatManager.disconnect()

        val intent = Intent(context, StreamingService::class.java).apply {
            action = StreamingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun toggleMute() {
        viewModelScope.launch {
            settingsRepo.update { it.copy(audio = it.audio.copy(muted = !it.audio.muted)) }
            audioCapture.isMuted = !audioCapture.isMuted
        }
    }

    fun toggleTorch() {
        _torchEnabled.value = !_torchEnabled.value
    }

    fun toggleThermalMitigation() {
        streamingEngine.toggleThermalMitigation()
    }

    fun toggleScreenBlackout() {
        _screenBlackoutEnabled.value = !_screenBlackoutEnabled.value
    }

    fun switchCamera() {
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(camera = it.camera.copy(useFrontCamera = !it.camera.useFrontCamera))
            }
        }
    }

    fun toggleAudioSource() {
        viewModelScope.launch {
            val current = settings.value.audio.inputMode
            val next = when (current) {
                AudioInputMode.MICROPHONE -> AudioInputMode.TEST_TONE
                AudioInputMode.TEST_TONE -> AudioInputMode.MICROPHONE
            }
            settingsRepo.update { it.copy(audio = it.audio.copy(inputMode = next)) }
            audioCapture.inputMode = next
            if (streamState.value.isStreaming) {
                restartAudioCaptureForStreaming()
            }
        }
    }

    fun cycleVideoBitrate() {
        updateSelectedStream { stream ->
            val next = cyclePreset(stream.videoBitrate, VIDEO_BITRATE_PRESETS_KBPS)
            stream.copy(videoBitrate = next)
        }
    }

    fun cycleAudioBitrate() {
        updateSelectedStream { stream ->
            val next = cyclePreset(stream.audioBitrate, AUDIO_BITRATE_PRESETS_KBPS)
            stream.copy(audioBitrate = next)
        }
    }

    fun setVideoBitrate(videoBitrateKbps: Int) {
        updateSelectedStream { stream -> stream.copy(videoBitrate = videoBitrateKbps) }
    }

    fun setAudioBitrate(audioBitrateKbps: Int) {
        updateSelectedStream { stream -> stream.copy(audioBitrate = audioBitrateKbps) }
    }

    fun setAudioInputMode(mode: AudioInputMode) {
        viewModelScope.launch {
            val updated = settings.value.audio.copy(inputMode = mode)
            settingsRepo.update { it.copy(audio = updated) }
            audioCapture.inputMode = mode
            if (streamState.value.isStreaming) {
                restartAudioCaptureForStreaming()
            }
        }
    }

    fun setAudioGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 4f)
        viewModelScope.launch {
            val updated = settings.value.audio.copy(gain = clamped)
            settingsRepo.update { it.copy(audio = updated) }
            audioCapture.gain = clamped
        }
    }

    fun navigate(screen: Screen) {
        if (screen != _currentScreen.value) {
            _screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (_screenStack.isNotEmpty()) {
            _currentScreen.value = _screenStack.removeAt(_screenStack.lastIndex)
            return true
        }
        return false
    }

    fun updateStream(index: Int, config: StreamConfig) {
        viewModelScope.launch {
            val shouldApplyLive = index == settings.value.selectedStreamIndex && streamState.value.isStreaming
            settingsRepo.update {
                val streams = it.streams.toMutableList()
                if (index < streams.size) {
                    streams[index] = config
                } else {
                    streams.add(config)
                }
                it.copy(streams = streams)
            }

            if (shouldApplyLive) {
                val liveConfig = buildLiveStreamConfig(config)
                streamingEngine.updateLiveStreamConfig(liveConfig)
            }
        }
    }

    fun selectStream(index: Int) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(selectedStreamIndex = index) }
        }
    }

    fun addStream() {
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(streams = it.streams + StreamConfig(name = "Stream ${it.streams.size + 1}"))
            }
        }
    }

    fun addStreamFromWizard(config: StreamConfig) {
        viewModelScope.launch {
            settingsRepo.update {
                val newStreams = it.streams + config
                it.copy(streams = newStreams, selectedStreamIndex = newStreams.size - 1)
            }
        }
    }

    fun deleteStream(index: Int) {
        viewModelScope.launch {
            settingsRepo.update {
                if (it.streams.size <= 1) return@update it
                val streams = it.streams.toMutableList()
                streams.removeAt(index)
                val selectedIndex = if (it.selectedStreamIndex >= streams.size) {
                    streams.size - 1
                } else {
                    it.selectedStreamIndex
                }
                it.copy(streams = streams, selectedStreamIndex = selectedIndex)
            }
        }
    }

    fun updateCameraSettings(camera: CameraSettings) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(camera = camera) }
        }
    }

    fun updateAudioSettings(audio: AudioSettings) {
        viewModelScope.launch {
            val previous = settings.value.audio
            settingsRepo.update { it.copy(audio = audio) }

            audioCapture.isMuted = audio.muted
            audioCapture.gain = audio.gain
            audioCapture.inputMode = audio.inputMode
            audioCapture.toneFrequencyHz = audio.toneFrequencyHz

            if (streamState.value.isStreaming && previous.inputMode != audio.inputMode) {
                restartAudioCaptureForStreaming()
            }
        }
    }

    fun updateDisplaySettings(display: DisplaySettings) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(display = display) }
        }
    }

    fun updateChatSettings(chat: ChatSettings) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(chat = chat) }
        }
    }

    fun updateWidgetSettings(widgets: WidgetSettings) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(widgets = widgets) }
        }
    }

    fun previewWidgetSettings(widgets: WidgetSettings) {
        streamingEngine.widgetSettingsRef.set(widgets)
        val loc = locationProvider.location.value
        if (loc != null && widgets.mapWidget.enabled) {
            mapTileProvider.updateLocation(loc, widgets.mapWidget.zoom, widgets.mapWidget.showMarker)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStreamingResources()
        streamingEngine.release()
        audioCapture.release()
        chatManager.release()
        locationProvider.release()
        mapTileProvider.release()
    }

    private fun restartAudioCaptureForStreaming() {
        val context = getApplication<Application>()
        if (!audioCapture.start(context)) {
            streamingEngine.showError("音声入力の切り替えに失敗したため、配信を継続できません。")
            stopStreaming(endBroadcast = false)
        }
    }

    private fun updateSelectedStream(transform: (StreamConfig) -> StreamConfig) {
        viewModelScope.launch {
            val currentSettings = settings.value
            val currentIndex = currentSettings.selectedStreamIndex
            val currentStream = currentSettings.streams.getOrNull(currentIndex) ?: return@launch
            val updatedStream = transform(currentStream)
            settingsRepo.update {
                val streams = it.streams.toMutableList()
                if (currentIndex < streams.size) {
                    streams[currentIndex] = updatedStream
                }
                it.copy(streams = streams)
            }
            if (streamState.value.isStreaming || streamState.value.isConnecting) {
                streamingEngine.updateLiveStreamConfig(buildLiveStreamConfig(updatedStream))
            }
        }
    }

    private fun updateStreamingNotification(state: StreamingEngine.StreamState) {
        if (!streamingResourcesActive && !state.isStreaming && !state.isConnecting) return
        val context = getApplication<Application>()
        val intent = Intent(context, StreamingService::class.java).apply {
            action = StreamingService.ACTION_UPDATE_INFO
            putExtra(StreamingService.EXTRA_STATUS_TEXT, buildNotificationStatusText(state, settings.value))
        }
        context.startService(intent)
    }

    private fun buildLiveStreamConfig(savedConfig: StreamConfig): StreamConfig {
        val active = streamingEngine.getCurrentConfig() ?: return savedConfig
        return savedConfig.copy(
            url = active.url,
            streamKey = active.streamKey,
            authType = active.authType,
            youtubeChannelId = active.youtubeChannelId,
            youtubeChannelName = active.youtubeChannelName,
            youtubeBroadcastTitle = active.youtubeBroadcastTitle,
            youtubePrivacy = active.youtubePrivacy,
            youtubeLatency = active.youtubeLatency,
            youtubeAutoStart = active.youtubeAutoStart,
            youtubeAutoStop = active.youtubeAutoStop,
        )
    }
}

private val VIDEO_BITRATE_PRESETS_KBPS = listOf(4000, 5000, 6000, 8000)
private val AUDIO_BITRATE_PRESETS_KBPS = listOf(96, 128, 160, 192)

private fun cyclePreset(currentValue: Int, presets: List<Int>): Int {
    if (presets.isEmpty()) return currentValue
    val currentIndex = presets.indexOf(currentValue).takeIf { it >= 0 }
        ?: presets.indices.minBy { kotlin.math.abs(presets[it] - currentValue) }
    return presets[(currentIndex + 1) % presets.size]
}

private fun buildNotificationStatusText(
    state: StreamingEngine.StreamState,
    settings: AppSettings,
): String {
    val source = when (settings.audio.inputMode) {
        AudioInputMode.MICROPHONE -> "Mic"
        AudioInputMode.TEST_TONE -> "Tone"
    }
    return when {
        state.reconnectAttempt > 0 -> {
            val maxAttempts = state.reconnectMaxAttempts
                .takeIf { it > 0 }
                ?: settings.currentStream.autoReconnectAttempts.clampAutoReconnectAttempts()
            "Reconnecting (${state.reconnectAttempt}/$maxAttempts) | $source"
        }
        state.isConnecting -> "Connecting | $source"
        state.isStreaming -> {
            val fpsText = if (state.fps > 0) String.format(Locale.US, "%dfps", state.fps) else "--fps"
            val resolutionText = if (state.videoWidth > 0) {
                "${state.videoWidth}x${state.videoHeight}"
            } else {
                settings.currentStream.resolution.displayName
            }
            "${state.bitrateKbps}kbps | $fpsText | $resolutionText | $source"
        }
        else -> "Idle | $source"
    }
}

sealed class Screen {
    data object Stream : Screen()
    data object Settings : Screen()
    data object StreamSettings : Screen()
    data object CameraSettings : Screen()
    data object AudioSettings : Screen()
    data object DisplaySettings : Screen()
    data object ChatSettings : Screen()
    data class StreamEdit(val index: Int) : Screen()
    data object StreamWizard : Screen()
    data object WidgetSettings : Screen()
}
