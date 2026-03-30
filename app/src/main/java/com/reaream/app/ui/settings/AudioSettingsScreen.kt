package com.reaream.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reaream.app.data.model.AudioSettings
import com.reaream.app.data.model.AudioInputMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    audio: AudioSettings,
    isStreaming: Boolean,
    onBack: () -> Unit,
    onUpdate: (AudioSettings) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio") },
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
            if (isStreaming) {
                ListItem(
                    headlineContent = { Text("Live Update") },
                    supportingContent = { Text("Mute / Gain は即時反映されます。Audio Source を変えると入力を切り替えます。") },
                )
            }

            SwitchItem(
                title = "Mute Audio",
                subtitle = "Mute the current audio source",
                checked = audio.muted,
                onCheckedChange = { onUpdate(audio.copy(muted = it)) },
            )

            ListItem(
                headlineContent = { Text("Audio Source") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(audio.inputMode.displayName)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AudioInputMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = audio.inputMode == mode,
                                    onClick = { onUpdate(audio.copy(inputMode = mode)) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AudioInputMode.entries.size,
                                    ),
                                ) {
                                    Text(mode.displayName)
                                }
                            }
                        }
                    }
                },
            )

            if (audio.inputMode == AudioInputMode.TEST_TONE) {
                ListItem(
                    headlineContent = { Text("Test Tone Frequency") },
                    supportingContent = {
                        Column {
                            Text("${audio.toneFrequencyHz} Hz")
                            Slider(
                                value = audio.toneFrequencyHz.toFloat(),
                                onValueChange = {
                                    onUpdate(audio.copy(toneFrequencyHz = it.toInt()))
                                },
                                valueRange = 220f..2000f,
                            )
                        }
                    },
                )
            }

            ListItem(
                headlineContent = { Text("Audio Gain") },
                supportingContent = {
                    Column {
                        Text("${String.format(Locale.getDefault(), "%.1f", audio.gain)}x")
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
