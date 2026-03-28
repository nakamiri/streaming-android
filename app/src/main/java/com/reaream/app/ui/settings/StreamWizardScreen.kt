package com.reaream.app.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.reaream.app.data.model.*
import com.reaream.app.ui.Screen

private enum class Platform(
    val displayName: String,
    val icon: ImageVector,
    val protocol: StreamProtocol,
    val defaultUrl: String,
    val defaultAudioBitrate: Int,
    val videoCodec: VideoCodec,
) {
    YOUTUBE(
        displayName = "YouTube",
        icon = Icons.Filled.PlayCircle,
        protocol = StreamProtocol.RTMP,
        defaultUrl = "rtmp://a.rtmp.youtube.com/live2",
        defaultAudioBitrate = 128,
        videoCodec = VideoCodec.H264,
    ),
    TWITCH(
        displayName = "Twitch",
        icon = Icons.Filled.Gamepad,
        protocol = StreamProtocol.RTMPS,
        defaultUrl = "rtmps://live.twitch.tv/app",
        defaultAudioBitrate = 160,
        videoCodec = VideoCodec.H264,
    ),
    CUSTOM(
        displayName = "Custom",
        icon = Icons.Filled.Tune,
        protocol = StreamProtocol.RTMP,
        defaultUrl = "",
        defaultAudioBitrate = 128,
        videoCodec = VideoCodec.H264,
    ),
}

private enum class QualityPreset(
    val displayName: String,
    val description: String,
    val resolution: Resolution,
    val videoBitrate: Int,
    val fps: Int,
) {
    LOW("Low", "720p / 2,500 kbps / 30fps", Resolution.HD_720, 2500, 30),
    STANDARD("Standard", "1080p / 4,500 kbps / 30fps", Resolution.HD_1080, 4500, 30),
    HIGH("High", "1080p / 6,000 kbps / 60fps", Resolution.HD_1080, 6000, 60),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamWizardScreen(
    onNavigate: (Screen) -> Unit,
    onSave: (StreamConfig) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var platform by remember { mutableStateOf<Platform?>(null) }
    var url by remember { mutableStateOf("") }
    var streamKey by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(QualityPreset.STANDARD) }

    val stepTitles = listOf("Platform", "Connection", "Quality")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Stream") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step > 0) step-- else onNavigate(Screen.StreamSettings)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (step > 0) {
                        OutlinedButton(onClick = { step-- }) {
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (step < 2) {
                        Button(
                            onClick = { step++ },
                            enabled = when (step) {
                                0 -> platform != null
                                1 -> platform == Platform.CUSTOM || url.isNotBlank()
                                else -> true
                            },
                        ) {
                            Text("Next")
                        }
                    } else {
                        Button(onClick = {
                            val p = platform ?: return@Button
                            onSave(
                                StreamConfig(
                                    name = if (p == Platform.CUSTOM) "Custom" else p.displayName,
                                    url = url,
                                    streamKey = streamKey,
                                    protocol = p.protocol,
                                    videoBitrate = quality.videoBitrate,
                                    audioBitrate = p.defaultAudioBitrate,
                                    resolution = quality.resolution,
                                    fps = quality.fps,
                                    videoCodec = p.videoCodec,
                                )
                            )
                            onNavigate(Screen.StreamSettings)
                        }) {
                            Text("Done")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Step indicator
            StepIndicator(
                currentStep = step,
                stepTitles = stepTitles,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )

            // Step content
            AnimatedContent(
                targetState = step,
                label = "wizardStep",
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    }
                },
            ) { currentStep ->
                when (currentStep) {
                    0 -> PlatformStep(
                        selected = platform,
                        onSelect = { p ->
                            platform = p
                            url = p.defaultUrl
                        },
                    )
                    1 -> ConnectionStep(
                        platform = platform,
                        url = url,
                        streamKey = streamKey,
                        onUrlChange = { url = it },
                        onStreamKeyChange = { streamKey = it },
                    )
                    2 -> QualityStep(
                        selected = quality,
                        onSelect = { quality = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    stepTitles: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        stepTitles.forEachIndexed { index, title ->
            val isActive = index <= currentStep
            val color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (isActive) color else Color.Transparent,
                            CircleShape,
                        )
                        .border(2.dp, color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (index < currentStep) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                color
                            },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }

            if (index < stepTitles.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    thickness = 2.dp,
                    color = if (index < currentStep) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun PlatformStep(
    selected: Platform?,
    onSelect: (Platform) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Select Platform",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Choose your streaming platform to auto-fill recommended settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Platform.entries.forEach { platform ->
            val isSelected = selected == platform
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(platform) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        platform.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            platform.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (platform != Platform.CUSTOM) {
                            Text(
                                "${platform.protocol.displayName} - ${platform.defaultUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Manual configuration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStep(
    platform: Platform?,
    url: String,
    streamKey: String,
    onUrlChange: (String) -> Unit,
    onStreamKeyChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Connection Details",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (platform == Platform.CUSTOM) {
                "Enter your streaming server URL and stream key."
            } else {
                "Your ${platform?.displayName} URL is pre-filled. Enter your stream key from your ${platform?.displayName} dashboard."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = streamKey,
            onValueChange = onStreamKeyChange,
            label = { Text("Stream Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default,
        )
    }
}

@Composable
private fun QualityStep(
    selected: QualityPreset,
    onSelect: (QualityPreset) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Stream Quality",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Choose a quality preset. You can fine-tune these settings later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        QualityPreset.entries.forEach { preset ->
            val isSelected = selected == preset
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(preset) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(preset) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            preset.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
