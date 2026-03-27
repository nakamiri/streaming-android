package com.moblin.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moblin.android.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Screen.Stream) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection("Streaming") {
                SettingsItem(
                    icon = Icons.Filled.Videocam,
                    title = "Streams",
                    subtitle = "Configure stream destinations",
                    onClick = { onNavigate(Screen.StreamSettings) },
                )
            }

            SettingsSection("Media") {
                SettingsItem(
                    icon = Icons.Filled.CameraAlt,
                    title = "Camera",
                    subtitle = "Camera selection, zoom, stabilization",
                    onClick = { onNavigate(Screen.CameraSettings) },
                )
                SettingsItem(
                    icon = Icons.Filled.Mic,
                    title = "Audio",
                    subtitle = "Microphone and audio settings",
                    onClick = { onNavigate(Screen.AudioSettings) },
                )
            }

            SettingsSection("Interface") {
                SettingsItem(
                    icon = Icons.Filled.Visibility,
                    title = "Display",
                    subtitle = "Overlay and HUD settings",
                    onClick = { onNavigate(Screen.DisplaySettings) },
                )
                SettingsItem(
                    icon = Icons.Filled.Chat,
                    title = "Chat",
                    subtitle = "Chat integration settings",
                    onClick = { onNavigate(Screen.ChatSettings) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
