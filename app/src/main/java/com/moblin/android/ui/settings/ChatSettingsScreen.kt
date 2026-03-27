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
import com.moblin.android.data.model.ChatSettings
import com.moblin.android.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    chat: ChatSettings,
    onNavigate: (Screen) -> Unit,
    onUpdate: (ChatSettings) -> Unit,
) {
    var twitchChannel by remember(chat) { mutableStateOf(chat.twitchChannelName) }
    var kickChannel by remember(chat) { mutableStateOf(chat.kickChannelName) }
    var youtubeVideoId by remember(chat) { mutableStateOf(chat.youtubeVideoId) }
    var fontSize by remember(chat) { mutableStateOf(chat.fontSize.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = {
                        onUpdate(
                            chat.copy(
                                twitchChannelName = twitchChannel,
                                kickChannelName = kickChannel,
                                youtubeVideoId = youtubeVideoId,
                                fontSize = fontSize.toIntOrNull() ?: 14,
                            )
                        )
                        onNavigate(Screen.Settings)
                    }) {
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwitchItem(
                title = "Enable Chat",
                subtitle = "Show chat overlay during streaming",
                checked = chat.enabled,
                onCheckedChange = { onUpdate(chat.copy(enabled = it)) },
            )

            Text("Twitch", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = twitchChannel,
                onValueChange = { twitchChannel = it },
                label = { Text("Channel Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider()

            Text("Kick", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = kickChannel,
                onValueChange = { kickChannel = it },
                label = { Text("Channel Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider()

            Text("YouTube", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = youtubeVideoId,
                onValueChange = { youtubeVideoId = it },
                label = { Text("Video ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider()

            OutlinedTextField(
                value = fontSize,
                onValueChange = { fontSize = it },
                label = { Text("Font Size") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
