package com.reaream.app.ui.settings

import android.net.Uri
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
import com.reaream.app.data.YouTubeApiClient
import com.reaream.app.data.YouTubeAuthManager
import com.reaream.app.data.model.*
import com.reaream.app.ui.Screen
import kotlinx.coroutines.launch

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
    youtubeAuthManager: YouTubeAuthManager? = null,
    youtubeApiClient: YouTubeApiClient? = null,
    oauthCallback: kotlinx.coroutines.flow.SharedFlow<Uri>? = null,
) {
    var step by remember { mutableIntStateOf(0) }
    var platform by remember { mutableStateOf<Platform?>(null) }
    var url by remember { mutableStateOf("") }
    var streamKey by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(QualityPreset.STANDARD) }

    // YouTube OAuth state
    var useOAuth by remember { mutableStateOf(false) }
    var isSignedIn by remember { mutableStateOf(youtubeAuthManager?.isSignedIn() == true) }
    var channelName by remember { mutableStateOf(youtubeAuthManager?.getChannelName() ?: "") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    // YouTube channel info (fetched after sign-in)
    var channelId by remember { mutableStateOf("") }
    var isLoadingChannel by remember { mutableStateOf(false) }

    // YouTube broadcast settings
    var broadcastTitle by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(YouTubePrivacy.UNLISTED) }
    var useExistingBroadcast by remember { mutableStateOf(false) }
    var existingBroadcasts by remember { mutableStateOf<List<YouTubeApiClient.BroadcastInfo>>(emptyList()) }
    var selectedBroadcastId by remember { mutableStateOf<String?>(null) }
    var isLoadingBroadcasts by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val isYouTubeOAuth = platform == Platform.YOUTUBE && useOAuth

    // OAuth steps: Platform(0) → Auth(1) → Quality(2) → Broadcast(3)
    // Normal steps: Platform(0) → Connection(1) → Quality(2)
    val stepTitles = if (isYouTubeOAuth) {
        listOf("Platform", "Auth", "Quality", "Broadcast")
    } else {
        listOf("Platform", "Connection", "Quality")
    }
    val totalSteps = stepTitles.size

    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    // Listen for OAuth callback from browser redirect
    LaunchedEffect(oauthCallback) {
        oauthCallback?.collect { uri ->
            isAuthenticating = true
            val success = youtubeAuthManager?.handleRedirect(uri) == true
            if (success) {
                fetchChannelInfo(youtubeAuthManager, youtubeApiClient) { id, name ->
                    channelId = id
                    channelName = name
                }
                isSignedIn = true
                authError = null
            } else {
                authError = "認証に失敗しました。再試行してください。"
            }
            isAuthenticating = false
        }
    }

    fun canProceed(currentStep: Int): Boolean = if (isYouTubeOAuth) {
        when (currentStep) {
            0 -> platform != null
            1 -> isSignedIn                              // Auth
            2 -> true                                     // Quality
            3 -> if (useExistingBroadcast) selectedBroadcastId != null else broadcastTitle.isNotBlank()
            else -> true
        }
    } else {
        when (currentStep) {
            0 -> platform != null
            1 -> platform == Platform.CUSTOM || url.isNotBlank()
            2 -> true
            else -> true
        }
    }

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

                    if (step < totalSteps - 1) {
                        Button(
                            onClick = {
                                step++
                                // Load existing broadcasts when entering broadcast step
                                if (isYouTubeOAuth && step == 3 && existingBroadcasts.isEmpty()) {
                                    isLoadingBroadcasts = true
                                    scope.launch {
                                        youtubeApiClient?.listUpcomingBroadcasts()
                                            ?.onSuccess { existingBroadcasts = it }
                                        isLoadingBroadcasts = false
                                    }
                                }
                            },
                            enabled = canProceed(step),
                        ) {
                            Text("Next")
                        }
                    } else {
                        Button(onClick = {
                            val p = platform ?: return@Button
                            if (isYouTubeOAuth) {
                                val displayName = channelName.ifBlank { "OAuth" }
                                onSave(
                                    StreamConfig(
                                        name = "YouTube ($displayName)",
                                        url = "", // Will be set at stream start
                                        streamKey = "",
                                        protocol = p.protocol,
                                        videoBitrate = quality.videoBitrate,
                                        audioBitrate = p.defaultAudioBitrate,
                                        resolution = quality.resolution,
                                        fps = quality.fps,
                                        videoCodec = p.videoCodec,
                                        authType = AuthType.YOUTUBE_OAUTH,
                                        youtubeChannelId = channelId,
                                        youtubeChannelName = displayName,
                                        youtubeBroadcastTitle = if (useExistingBroadcast) "" else broadcastTitle,
                                        youtubePrivacy = privacy,
                                    )
                                )
                            } else {
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
                            }
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
                            // Reset OAuth state when switching platforms
                            if (p != Platform.YOUTUBE) useOAuth = false
                        },
                    )
                    1 -> {
                        if (isYouTubeOAuth) {
                            YouTubeAuthStep(
                                isSignedIn = isSignedIn,
                                channelName = channelName,
                                authError = authError,
                                isAuthenticating = isAuthenticating,
                                onSignIn = {
                                    activity?.let { youtubeAuthManager?.launchAuthFlow(it) }
                                },
                                onSignOut = {
                                    youtubeAuthManager?.signOut()
                                    isSignedIn = false
                                    channelName = ""
                                    channelId = ""
                                },
                            )
                        } else if (platform == Platform.YOUTUBE) {
                            YouTubeConnectionStep(
                                useOAuth = useOAuth,
                                onUseOAuthChange = { useOAuth = it },
                                hasOAuth = youtubeAuthManager != null,
                                url = url,
                                streamKey = streamKey,
                                onUrlChange = { url = it },
                                onStreamKeyChange = { streamKey = it },
                            )
                        } else {
                            ConnectionStep(
                                platform = platform,
                                url = url,
                                streamKey = streamKey,
                                onUrlChange = { url = it },
                                onStreamKeyChange = { streamKey = it },
                            )
                        }
                    }
                    2 -> QualityStep(
                        selected = quality,
                        onSelect = { quality = it },
                    )
                    3 -> if (isYouTubeOAuth) {
                        BroadcastStep(
                            useExisting = useExistingBroadcast,
                            onUseExistingChange = { useExistingBroadcast = it },
                            broadcastTitle = broadcastTitle,
                            onBroadcastTitleChange = { broadcastTitle = it },
                            privacy = privacy,
                            onPrivacyChange = { privacy = it },
                            existingBroadcasts = existingBroadcasts,
                            selectedBroadcastId = selectedBroadcastId,
                            onSelectBroadcast = { selectedBroadcastId = it },
                            isLoading = isLoadingBroadcasts,
                        )
                    }
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
private fun YouTubeConnectionStep(
    useOAuth: Boolean,
    onUseOAuthChange: (Boolean) -> Unit,
    hasOAuth: Boolean,
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
            "Connection Method",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "YouTube への接続方法を選択してください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (hasOAuth) {
            AuthMethodCard(
                title = "アカウント連携",
                description = "Google アカウントでログインして自動設定（個人チャンネル向け）",
                icon = Icons.Filled.AccountCircle,
                isSelected = useOAuth,
                onClick = { onUseOAuthChange(true) },
            )
        }

        AuthMethodCard(
            title = "ストリームキー",
            description = "YouTube Studio からキーをコピーして入力（ブランドアカウント対応）",
            icon = Icons.Filled.Key,
            isSelected = !useOAuth,
            onClick = { onUseOAuthChange(false) },
        )

        if (!useOAuth) {
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
}

@Composable
private fun AuthMethodCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
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
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
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

@Composable
private fun YouTubeAuthStep(
    isSignedIn: Boolean,
    channelName: String,
    authError: String?,
    isAuthenticating: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Google アカウント認証",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "YouTube にアクセスするため Google アカウントでログインしてください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        if (isSignedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ログイン済み",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (channelName.isNotBlank()) {
                            Text(
                                channelName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    TextButton(onClick = onSignOut) {
                        Text("アカウント切替")
                    }
                }
            }
        } else {
            if (isAuthenticating) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Google アカウントでログイン")
                }
            }

            if (authError != null) {
                Text(
                    authError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BroadcastStep(
    useExisting: Boolean,
    onUseExistingChange: (Boolean) -> Unit,
    broadcastTitle: String,
    onBroadcastTitleChange: (String) -> Unit,
    privacy: YouTubePrivacy,
    onPrivacyChange: (YouTubePrivacy) -> Unit,
    existingBroadcasts: List<YouTubeApiClient.BroadcastInfo>,
    selectedBroadcastId: String?,
    onSelectBroadcast: (String) -> Unit,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "配信設定",
            style = MaterialTheme.typography.titleMedium,
        )

        // Toggle: New vs Existing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !useExisting,
                onClick = { onUseExistingChange(false) },
                label = { Text("新規作成") },
                leadingIcon = if (!useExisting) {
                    { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
            FilterChip(
                selected = useExisting,
                onClick = { onUseExistingChange(true) },
                label = { Text("既存の配信枠") },
                leadingIcon = if (useExisting) {
                    { Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
        }

        if (useExisting) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (existingBroadcasts.isEmpty()) {
                Text(
                    "配信予定の枠がありません。新規作成を選択してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                existingBroadcasts.forEach { broadcast ->
                    val isSelected = selectedBroadcastId == broadcast.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectBroadcast(broadcast.id) },
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
                                onClick = { onSelectBroadcast(broadcast.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    broadcast.title,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    broadcast.status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = broadcastTitle,
                onValueChange = onBroadcastTitleChange,
                label = { Text("配信タイトル") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                "公開設定",
                style = MaterialTheme.typography.labelLarge,
            )
            YouTubePrivacy.entries.forEach { p ->
                val isSelected = privacy == p
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPrivacyChange(p) },
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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onPrivacyChange(p) },
                        )
                        Text(
                            p.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun fetchChannelInfo(
    authManager: YouTubeAuthManager?,
    apiClient: YouTubeApiClient?,
    onResult: (id: String, name: String) -> Unit,
) {
    apiClient?.listMyChannels()?.onSuccess { list ->
        if (list.isNotEmpty()) {
            authManager?.setChannelName(list[0].title)
            onResult(list[0].id, list[0].title)
        } else {
            onResult("", "YouTube")
        }
    }?.onFailure {
        onResult("", "YouTube")
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
