package com.reaream.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reaream.app.data.model.*
import com.reaream.app.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamEditScreen(
    streamIndex: Int,
    settings: AppSettings,
    onBack: () -> Unit,
    onSave: (Int, StreamConfig) -> Unit,
) {
    val stream = settings.streams.getOrElse(streamIndex) { StreamConfig() }

    var name by remember(stream) { mutableStateOf(stream.name) }
    var url by remember(stream) { mutableStateOf(stream.url) }
    var streamKey by remember(stream) { mutableStateOf(stream.streamKey) }
    var protocol by remember(stream) { mutableStateOf(stream.protocol) }
    var videoBitrate by remember(stream) { mutableStateOf(stream.videoBitrate.toString()) }
    var audioBitrate by remember(stream) { mutableStateOf(stream.audioBitrate.toString()) }
    var resolution by remember(stream) { mutableStateOf(stream.resolution) }
    var fps by remember(stream) { mutableStateOf(stream.fps.toString()) }
    var videoCodec by remember(stream) { mutableStateOf(stream.videoCodec) }
    var srtLatency by remember(stream) { mutableStateOf(stream.srtLatency.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Stream") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onSave(
                            streamIndex,
                            StreamConfig(
                                name = name,
                                url = url,
                                streamKey = streamKey,
                                protocol = protocol,
                                videoBitrate = videoBitrate.toIntOrNull() ?: 5000,
                                audioBitrate = audioBitrate.toIntOrNull() ?: 128,
                                resolution = resolution,
                                fps = fps.toIntOrNull() ?: 30,
                                videoCodec = videoCodec,
                                srtLatency = srtLatency.toIntOrNull() ?: 2000,
                            )
                        )
                        onBack()
                    }) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Protocol selector
            Text("Protocol", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                StreamProtocol.entries.forEachIndexed { index, proto ->
                    SegmentedButton(
                        selected = protocol == proto,
                        onClick = { protocol = proto },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = StreamProtocol.entries.size,
                        ),
                    ) {
                        Text(proto.displayName)
                    }
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = {
                    Text(
                        when (protocol) {
                            StreamProtocol.RTMP -> "rtmp://live.twitch.tv/app"
                            StreamProtocol.RTMPS -> "rtmps://live.twitch.tv/app"
                            StreamProtocol.SRT -> "srt://server:9000"
                            StreamProtocol.RIST -> "rist://server:5000"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = streamKey,
                onValueChange = { streamKey = it },
                label = { Text("Stream Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            // Resolution
            Text("Resolution", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Resolution.entries.forEachIndexed { index, res ->
                    SegmentedButton(
                        selected = resolution == res,
                        onClick = { resolution = res },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Resolution.entries.size,
                        ),
                    ) {
                        Text(res.displayName)
                    }
                }
            }

            // Video bitrate field + presets
            OutlinedTextField(
                value = videoBitrate,
                onValueChange = { videoBitrate = it },
                label = { Text("Video Bitrate (kbps)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            // Presets based on current resolution (30fps / 60fps)
            val fpsInt = fps.toIntOrNull() ?: 30
            val videoPresets = videoBitratePresets(resolution, fpsInt)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("推奨:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                videoPresets.forEach { preset ->
                    val selected = videoBitrate == preset.toString()
                    FilterChip(
                        selected = selected,
                        onClick = { videoBitrate = preset.toString() },
                        label = { Text("${preset / 1000}k", fontSize = 11.sp) },
                    )
                }
            }

            // FPS field + presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = fps,
                    onValueChange = { fps = it },
                    label = { Text("FPS") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(30, 60).forEach { preset ->
                        FilterChip(
                            selected = fps == preset.toString(),
                            onClick = { fps = preset.toString() },
                            label = { Text("${preset}fps", fontSize = 11.sp) },
                        )
                    }
                }
            }

            // Audio bitrate field + presets
            OutlinedTextField(
                value = audioBitrate,
                onValueChange = { audioBitrate = it },
                label = { Text("Audio Bitrate (kbps)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("推奨:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(128, 160, 192).forEach { preset ->
                    FilterChip(
                        selected = audioBitrate == preset.toString(),
                        onClick = { audioBitrate = preset.toString() },
                        label = { Text("${preset}k", fontSize = 11.sp) },
                    )
                }
            }

            if (protocol == StreamProtocol.SRT) {
                OutlinedTextField(
                    value = srtLatency,
                    onValueChange = { srtLatency = it },
                    label = { Text("SRT Latency (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // Video Codec
            Text("Video Codec", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                VideoCodec.entries.forEachIndexed { index, codec ->
                    SegmentedButton(
                        selected = videoCodec == codec,
                        onClick = { videoCodec = codec },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = VideoCodec.entries.size,
                        ),
                    ) {
                        Text(codec.displayName)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Recommended video bitrate presets (kbps) for a given resolution and fps.
 * Based on YouTube / Twitch encoder recommendations.
 */
private fun videoBitratePresets(resolution: Resolution, fps: Int): List<Int> = when (resolution) {
    Resolution.HD_720  -> if (fps >= 60) listOf(4500, 6000) else listOf(2500, 4000)
    Resolution.HD_1080 -> if (fps >= 60) listOf(6000, 9000) else listOf(4000, 6000)
    Resolution.UHD_4K  -> if (fps >= 60) listOf(20000, 30000) else listOf(13000, 20000)
}
