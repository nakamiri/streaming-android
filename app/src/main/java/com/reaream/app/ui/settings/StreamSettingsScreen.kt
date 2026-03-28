package com.reaream.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
                            if (settings.streams.size > 1) {
                                IconButton(onClick = { onDeleteStream(index) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { onSelectStream(index) },
                )
            }
        }
    }
}
