package com.moblin.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moblin.android.data.model.*
import com.moblin.android.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamEditScreen(
    streamIndex: Int,
    settings: AppSettings,
    onNavigate: (Screen) -> Unit,
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
                    IconButton(onClick = { onNavigate(Screen.StreamSettings) }) {
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
                        onNavigate(Screen.StreamSettings)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = videoBitrate,
                    onValueChange = { videoBitrate = it },
                    label = { Text("Video Bitrate (kbps)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = audioBitrate,
                    onValueChange = { audioBitrate = it },
                    label = { Text("Audio Bitrate (kbps)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = fps,
                    onValueChange = { fps = it },
                    label = { Text("FPS") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )

                if (protocol == StreamProtocol.SRT) {
                    OutlinedTextField(
                        value = srtLatency,
                        onValueChange = { srtLatency = it },
                        label = { Text("SRT Latency (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
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
