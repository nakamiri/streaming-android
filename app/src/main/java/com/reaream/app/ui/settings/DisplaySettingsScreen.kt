package com.reaream.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reaream.app.data.model.DisplaySettings
import com.reaream.app.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
    display: DisplaySettings,
    onBack: () -> Unit,
    onUpdate: (DisplaySettings) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Display") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchItem(
                title = "Show Chat",
                subtitle = "Show chat overlay on stream view",
                checked = display.showChat,
                onCheckedChange = { onUpdate(display.copy(showChat = it)) },
            )
            SwitchItem(
                title = "Show Stream Info",
                subtitle = "Show bitrate, uptime, and connection info",
                checked = display.showStreamInfo,
                onCheckedChange = { onUpdate(display.copy(showStreamInfo = it)) },
            )
            SwitchItem(
                title = "Show Audio Level",
                subtitle = "Show audio level meter",
                checked = display.showAudioLevel,
                onCheckedChange = { onUpdate(display.copy(showAudioLevel = it)) },
            )
            SwitchItem(
                title = "Show Bitrate",
                subtitle = "Show current bitrate in overlay",
                checked = display.showBitrate,
                onCheckedChange = { onUpdate(display.copy(showBitrate = it)) },
            )
            SwitchItem(
                title = "Show FPS",
                subtitle = "Show frame rate in overlay",
                checked = display.showFps,
                onCheckedChange = { onUpdate(display.copy(showFps = it)) },
            )
            SwitchItem(
                title = "Show Uptime",
                subtitle = "Show stream duration in overlay",
                checked = display.showUptime,
                onCheckedChange = { onUpdate(display.copy(showUptime = it)) },
            )
        }
    }
}
