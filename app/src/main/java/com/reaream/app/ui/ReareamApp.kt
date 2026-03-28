package com.reaream.app.ui

import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reaream.app.data.model.StreamConfig
import com.reaream.app.ui.settings.*
import com.reaream.app.ui.stream.StreamScreen

@Composable
fun ReareamApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val torchEnabled by viewModel.torchEnabled.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatManager.messages.collectAsStateWithLifecycle()
    val currentLocation by viewModel.locationProvider.location.collectAsStateWithLifecycle()
    val currentAddress by viewModel.locationProvider.address.collectAsStateWithLifecycle()
    val speedKmh by viewModel.locationProvider.speedKmh.collectAsStateWithLifecycle()
    val locationPermissionDenied by viewModel.locationProvider.permissionDenied.collectAsStateWithLifecycle()
    val mapBitmap by viewModel.mapTileProvider.mapBitmap.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = currentScreen,
        label = "screenTransition",
        modifier = modifier,
    ) { screen ->
        when (screen) {
            is Screen.Stream -> StreamScreen(
                settings = settings,
                streamState = streamState,
                chatMessages = chatMessages,
                torchEnabled = torchEnabled,
                onToggleStreaming = viewModel::toggleStreaming,
                onToggleMute = viewModel::toggleMute,
                onToggleTorch = viewModel::toggleTorch,
                onSwitchCamera = viewModel::switchCamera,
                onOpenSettings = { viewModel.navigate(Screen.Settings) },
                onClearError = { viewModel.streamingEngine.clearError() },
                onVideoFrame = viewModel.streamingEngine::onVideoFrame,
                videoWidth = settings.currentStream.resolution.width,
                videoHeight = settings.currentStream.resolution.height,
                currentLocation = currentLocation,
                currentAddress = currentAddress,
                speedKmh = speedKmh,
                locationPermissionDenied = locationPermissionDenied,
                mapBitmap = mapBitmap,
                onUpdateWidgets = viewModel::updateWidgetSettings,
                onRecheckPermission = { viewModel.locationProvider.recheckPermission() },
            )

            is Screen.Settings -> SettingsScreen(
                onNavigate = viewModel::navigate,
            )

            is Screen.StreamSettings -> StreamSettingsScreen(
                settings = settings,
                onNavigate = viewModel::navigate,
                onSelectStream = viewModel::selectStream,
                onDeleteStream = viewModel::deleteStream,
            )

            is Screen.StreamEdit -> StreamEditScreen(
                streamIndex = screen.index,
                settings = settings,
                onNavigate = viewModel::navigate,
                onSave = viewModel::updateStream,
            )

            is Screen.CameraSettings -> CameraSettingsScreen(
                camera = settings.camera,
                onNavigate = viewModel::navigate,
                onUpdate = viewModel::updateCameraSettings,
            )

            is Screen.AudioSettings -> AudioSettingsScreen(
                audio = settings.audio,
                onNavigate = viewModel::navigate,
                onUpdate = viewModel::updateAudioSettings,
            )

            is Screen.DisplaySettings -> DisplaySettingsScreen(
                display = settings.display,
                onNavigate = viewModel::navigate,
                onUpdate = viewModel::updateDisplaySettings,
            )

            is Screen.ChatSettings -> ChatSettingsScreen(
                chat = settings.chat,
                onNavigate = viewModel::navigate,
                onUpdate = viewModel::updateChatSettings,
            )

            is Screen.WidgetSettings -> WidgetSettingsScreen(
                widgets = settings.widgets,
                onNavigate = viewModel::navigate,
                onUpdate = viewModel::updateWidgetSettings,
            )

            is Screen.StreamWizard -> StreamWizardScreen(
                onNavigate = viewModel::navigate,
                onSave = { config: StreamConfig ->
                    viewModel.addStreamFromWizard(config)
                },
            )
        }
    }
}
