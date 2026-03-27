package com.moblin.android.ui

import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moblin.android.ui.settings.*
import com.moblin.android.ui.stream.StreamScreen

@Composable
fun MoblinApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val torchEnabled by viewModel.torchEnabled.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatManager.messages.collectAsStateWithLifecycle()

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
            )

            is Screen.Settings -> SettingsScreen(
                onNavigate = viewModel::navigate,
            )

            is Screen.StreamSettings -> StreamSettingsScreen(
                settings = settings,
                onNavigate = viewModel::navigate,
                onSelectStream = viewModel::selectStream,
                onAddStream = viewModel::addStream,
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
        }
    }
}
