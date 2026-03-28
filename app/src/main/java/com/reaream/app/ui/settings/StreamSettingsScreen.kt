package com.reaream.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reaream.app.data.model.AppSettings
import com.reaream.app.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSettingsScreen(
    settings: AppSettings,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
    onSelectStream: (Int) -> Unit,
    onDeleteStream: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Streams") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(Screen.StreamWizard) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Stream")
                    }
                },
            )
        },
    ) { padding ->
        var deleteTargetIndex by remember { mutableStateOf<Int?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            settings.streams.forEachIndexed { index, stream ->
                ListItem(
                    headlineContent = { Text(stream.name) },
                    supportingContent = {
                        Text("${stream.protocol.displayName} - ${stream.resolution.displayName} @ ${stream.fps}fps")
                    },
                    leadingContent = {
                        RadioButton(
                            selected = index == settings.selectedStreamIndex,
                            onClick = { onSelectStream(index) },
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onNavigate(Screen.StreamEdit(index)) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                            IconButton(
                                onClick = { deleteTargetIndex = index },
                                enabled = settings.streams.size > 1,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = if (settings.streams.size > 1)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onSelectStream(index) },
                )
            }
        }

        // Delete confirmation dialog
        deleteTargetIndex?.let { index ->
            val streamName = settings.streams.getOrNull(index)?.name ?: ""
            AlertDialog(
                onDismissRequest = { deleteTargetIndex = null },
                title = { Text("ストリームを削除") },
                text = { Text("「$streamName」を削除しますか？この操作は取り消せません。") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleteStream(index)
                        deleteTargetIndex = null
                    }) {
                        Text("削除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTargetIndex = null }) {
                        Text("キャンセル")
                    }
                },
            )
        }
    }
}
