package com.moblin.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moblin.android.data.model.AudioSettings
import com.moblin.android.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    audio: AudioSettings,
    onNavigate: (Screen) -> Unit,
    onUpdate: (AudioSettings) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio") },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Screen.Settings) }) {
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
                title = "Mute Microphone",
                subtitle = "Mute audio input",
                checked = audio.muted,
                onCheckedChange = { onUpdate(audio.copy(muted = it)) },
            )

            ListItem(
                headlineContent = { Text("Audio Gain") },
                supportingContent = {
                    Column {
                        Text("${String.format("%.1f", audio.gain)}x")
                        Slider(
                            value = audio.gain,
                            onValueChange = { onUpdate(audio.copy(gain = it)) },
                            valueRange = 0f..3f,
                        )
                    }
                },
            )
        }
    }
}
