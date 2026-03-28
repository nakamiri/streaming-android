package com.reaream.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reaream.app.chat.ChatManager
import com.reaream.app.data.LocationProvider
import com.reaream.app.data.MapTileProvider
import com.reaream.app.data.SettingsRepository
import com.reaream.app.data.model.*
import com.reaream.app.service.StreamingService
import com.reaream.app.streaming.AudioCapture
import com.reaream.app.streaming.StreamingEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    val streamingEngine = StreamingEngine()
    val chatManager = ChatManager()
    val locationProvider = LocationProvider(application)
    val mapTileProvider = MapTileProvider()
    val audioCapture = AudioCapture { data, timestamp ->
        streamingEngine.onAudioData(data, timestamp)
    }

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init {
        // Sync widget settings and location data to streaming engine
        viewModelScope.launch {
            settings.collect { s ->
                streamingEngine.widgetSettingsRef.set(s.widgets)

                // Start/stop location updates based on widget config
                if (s.widgets.locationWidget.enabled || s.widgets.speedWidget.enabled || s.widgets.mapWidget.enabled) {
                    locationProvider.startUpdates()
                } else {
                    locationProvider.stopUpdates()
                }
            }
        }
        viewModelScope.launch {
            locationProvider.location.collect { loc ->
                streamingEngine.widgetRenderer.currentLocation.set(loc)
                if (loc != null) {
                    val zoom = settings.value.widgets.mapWidget.zoom
                    mapTileProvider.updateLocation(loc, zoom)
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
    }

    val streamState: StateFlow<StreamingEngine.StreamState> = streamingEngine.state

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Stream)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun toggleStreaming() {
        val state = streamState.value
        if (state.isStreaming || state.isConnecting) {
            stopStreaming()
        } else {
            startStreaming()
        }
    }

    private fun startStreaming() {
        val config = settings.value.currentStream

        // Validate URL before starting anything
        if (config.url.isBlank()) {
            streamingEngine.startStreaming(config) // Will set error state
            return
        }

        val context = getApplication<Application>()

        // Start foreground service
        val intent = Intent(context, StreamingService::class.java).apply {
            action = StreamingService.ACTION_START
        }
        context.startForegroundService(intent)

        // Start audio capture
        audioCapture.isMuted = settings.value.audio.muted
        audioCapture.start(context)

        // Connect chat
        val chatSettings = settings.value.chat
        if (chatSettings.enabled && chatSettings.twitchChannelName.isNotBlank()) {
            chatManager.connectTwitch(chatSettings.twitchChannelName)
        }

        // Start streaming
        streamingEngine.startStreaming(config)
    }

    private fun stopStreaming() {
        val context = getApplication<Application>()

        streamingEngine.stopStreaming()
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

    fun switchCamera() {
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(camera = it.camera.copy(useFrontCamera = !it.camera.useFrontCamera))
            }
        }
    }

    fun navigate(screen: Screen) {
        _currentScreen.value = screen
    }

    fun updateStream(index: Int, config: StreamConfig) {
        viewModelScope.launch {
            settingsRepo.update {
                val streams = it.streams.toMutableList()
                if (index < streams.size) {
                    streams[index] = config
                } else {
                    streams.add(config)
                }
                it.copy(streams = streams)
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
            settingsRepo.update { it.copy(audio = audio) }
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

    override fun onCleared() {
        super.onCleared()
        streamingEngine.release()
        audioCapture.release()
        chatManager.release()
        locationProvider.release()
        mapTileProvider.release()
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
