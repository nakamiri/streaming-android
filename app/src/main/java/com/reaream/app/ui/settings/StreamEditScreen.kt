package com.reaream.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.reaream.app.data.YouTubeAuthManager
import com.reaream.app.data.model.*
import com.reaream.app.ui.Screen
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamEditScreen(
    streamIndex: Int,
    settings: AppSettings,
    isStreaming: Boolean,
    onBack: () -> Unit,
    onSave: (Int, StreamConfig) -> Unit,
    youtubeAuthManager: YouTubeAuthManager? = null,
    oauthCallback: SharedFlow<Uri>? = null,
    onSignOutYouTube: (() -> Unit)? = null,
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
    var adaptiveBitrate by remember(stream) { mutableStateOf(stream.adaptiveBitrate) }
    var autoReconnect by remember(stream) { mutableStateOf(stream.autoReconnect) }
    var autoReconnectAttempts by remember(stream) {
        mutableStateOf(stream.autoReconnectAttempts.toString())
    }
    var autoReconnectDelaySeconds by remember(stream) {
        mutableStateOf(stream.autoReconnectDelaySeconds.toString())
    }
    // YouTube Live settings
    var youtubeLatency by remember(stream) { mutableStateOf(stream.youtubeLatency) }
    var youtubeAutoStart by remember(stream) { mutableStateOf(stream.youtubeAutoStart) }
    var youtubeAutoStop by remember(stream) { mutableStateOf(stream.youtubeAutoStop) }

    // YouTube OAuth state (only relevant when authType == YOUTUBE_OAUTH)
    var channelName by remember { mutableStateOf(youtubeAuthManager?.getChannelName() ?: stream.youtubeChannelName) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    LaunchedEffect(oauthCallback) {
        oauthCallback?.collect { uri ->
            isAuthenticating = true
            val success = youtubeAuthManager?.handleRedirect(uri) == true
            if (success) {
                channelName = youtubeAuthManager?.getChannelName() ?: ""
                authError = null
            } else {
                authError = "認証に失敗しました"
            }
            isAuthenticating = false
        }
    }

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
                            stream.copy(
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
                                adaptiveBitrate = adaptiveBitrate,
                                autoReconnect = autoReconnect,
                                autoReconnectAttempts =
                                    (autoReconnectAttempts.toIntOrNull()
                                        ?: DEFAULT_AUTO_RECONNECT_ATTEMPTS)
                                        .clampAutoReconnectAttempts(),
                                autoReconnectDelaySeconds =
                                    (autoReconnectDelaySeconds.toIntOrNull()
                                        ?: DEFAULT_AUTO_RECONNECT_DELAY_SECONDS)
                                        .clampAutoReconnectDelaySeconds(),
                                authType = stream.authType,
                                youtubeChannelName = channelName,
                                youtubeBroadcastTitle = stream.youtubeBroadcastTitle,
                                youtubePrivacy = stream.youtubePrivacy,
                                youtubeLatency = youtubeLatency,
                                youtubeAutoStart = youtubeAutoStart,
                                youtubeAutoStop = youtubeAutoStop,
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
            if (isStreaming && streamIndex == settings.selectedStreamIndex) {
                Text(
                    text = "配信中です。Video Bitrate / Audio Bitrate / アダプティブ品質は即時反映されます。URL・解像度・FPS・Codec は次回開始時に反映されます。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

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
                        onClick = {
                            protocol = proto
                            if (proto.usesRtmpTransport() && videoCodec != VideoCodec.H264) {
                                videoCodec = VideoCodec.H264
                            }
                        },
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

            // Adaptive quality
            ListItem(
                headlineContent = { Text("アダプティブ品質") },
                supportingContent = { Text("品質低下時にビットレートを自動で下げて切断や大きなコマ落ちを抑える") },
                trailingContent = {
                    Switch(
                        checked = adaptiveBitrate,
                        onCheckedChange = { adaptiveBitrate = it },
                    )
                },
            )

            ListItem(
                headlineContent = { Text("自動再接続") },
                supportingContent = {
                    Text("配信中に接続が切れた場合、すぐに再接続を開始し、その後は指定間隔で指定回数まで試します")
                },
                trailingContent = {
                    Switch(
                        checked = autoReconnect,
                        onCheckedChange = { autoReconnect = it },
                    )
                },
            )

            OutlinedTextField(
                value = autoReconnectAttempts,
                onValueChange = { autoReconnectAttempts = it },
                label = { Text("Reconnect Attempts") },
                supportingText = {
                    Text("${MIN_AUTO_RECONNECT_ATTEMPTS}-${MAX_AUTO_RECONNECT_ATTEMPTS} 回。初回即時試行を含みます")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = autoReconnect,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("推奨:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(3, 5, 10).forEach { preset ->
                    FilterChip(
                        selected = autoReconnectAttempts == preset.toString(),
                        onClick = { autoReconnectAttempts = preset.toString() },
                        label = { Text("${preset}回", fontSize = 11.sp) },
                        enabled = autoReconnect,
                    )
                }
            }
            OutlinedTextField(
                value = autoReconnectDelaySeconds,
                onValueChange = { autoReconnectDelaySeconds = it },
                label = { Text("Reconnect Interval (sec)") },
                supportingText = {
                    Text("${MIN_AUTO_RECONNECT_DELAY_SECONDS}-${MAX_AUTO_RECONNECT_DELAY_SECONDS} 秒。2回目以降の待機時間です")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = autoReconnect,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("推奨:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(1, 3, 5).forEach { preset ->
                    FilterChip(
                        selected = autoReconnectDelaySeconds == preset.toString(),
                        onClick = { autoReconnectDelaySeconds = preset.toString() },
                        label = { Text("${preset}秒", fontSize = 11.sp) },
                        enabled = autoReconnect,
                    )
                }
            }

            // Video Codec
            Text("Video Codec", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                VideoCodec.entries.forEachIndexed { index, codec ->
                    val supported = !(protocol.usesRtmpTransport() && codec == VideoCodec.H265)
                    SegmentedButton(
                        selected = videoCodec == codec,
                        onClick = { if (supported) videoCodec = codec },
                        enabled = supported,
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = VideoCodec.entries.size,
                        ),
                    ) {
                        Text(codec.displayName)
                    }
                }
            }

            when {
                protocol == StreamProtocol.RIST -> {
                    Text(
                        text = "RIST はまだ実装されていません。保存はできますが、開始時にエラーになります。",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                protocol.usesRtmpTransport() -> {
                    Text(
                        text = "RTMP/RTMPS は現在 H.264 のみサポートしています。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }

            // YouTube account section (OAuth streams only)
            if (stream.authType == AuthType.YOUTUBE_OAUTH && youtubeAuthManager != null) {
                HorizontalDivider()
                Text("YouTubeアカウント", style = MaterialTheme.typography.labelLarge)

                if (isAuthenticating) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("認証中...", fontSize = 13.sp)
                    }
                } else if (youtubeAuthManager.isSignedIn()) {
                    ListItem(
                        headlineContent = { Text(channelName.ifBlank { "YouTube" }) },
                        supportingContent = { Text("ログイン済み") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFFFF0000),
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch {
                                    onSignOutYouTube?.invoke()
                                    if (activity != null) {
                                        youtubeAuthManager.launchAuthFlow(activity)
                                    }
                                }
                            }) {
                                Text("切り替え")
                            }
                        },
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            if (activity != null) {
                                youtubeAuthManager.launchAuthFlow(activity)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Googleアカウントでログイン")
                    }
                }

                if (authError != null) {
                    Text(
                        text = authError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }

            // YouTube Live settings (OAuth streams only)
            if (stream.authType == AuthType.YOUTUBE_OAUTH) {
                HorizontalDivider()

                Text("YouTube Live 設定", style = MaterialTheme.typography.labelLarge)

                Text("配信遅延", style = MaterialTheme.typography.labelMedium)
                YouTubeLatency.entries.forEach { mode ->
                    val isSelected = youtubeLatency == mode
                    Card(
                        onClick = { youtubeLatency = mode },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = isSelected, onClick = { youtubeLatency = mode })
                            Column {
                                Text(mode.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                ListItem(
                    headlineContent = { Text("自動スタート") },
                    supportingContent = { Text("接続後に配信を自動で開始する") },
                    trailingContent = {
                        Switch(checked = youtubeAutoStart, onCheckedChange = { youtubeAutoStart = it })
                    },
                )
                ListItem(
                    headlineContent = { Text("自動ストップ") },
                    supportingContent = { Text("接続が切れたら配信を自動で終了する") },
                    trailingContent = {
                        Switch(checked = youtubeAutoStop, onCheckedChange = { youtubeAutoStop = it })
                    },
                )
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
