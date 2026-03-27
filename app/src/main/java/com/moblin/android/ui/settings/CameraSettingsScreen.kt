package com.moblin.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moblin.android.data.model.CameraSettings
import com.moblin.android.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingsScreen(
    camera: CameraSettings,
    onNavigate: (Screen) -> Unit,
    onUpdate: (CameraSettings) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera") },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Screen.Settings) }) {
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
            SwitchItem(
                title = "Use Front Camera",
                subtitle = "Use the front-facing camera by default",
                checked = camera.useFrontCamera,
                onCheckedChange = { onUpdate(camera.copy(useFrontCamera = it)) },
            )

            SwitchItem(
                title = "Mirror Front Camera",
                subtitle = "Mirror the preview when using front camera",
                checked = camera.mirrorFrontCamera,
                onCheckedChange = { onUpdate(camera.copy(mirrorFrontCamera = it)) },
            )

            SwitchItem(
                title = "Video Stabilization",
                subtitle = "Enable electronic video stabilization",
                checked = camera.videoStabilization,
                onCheckedChange = { onUpdate(camera.copy(videoStabilization = it)) },
            )

            SwitchItem(
                title = "Auto Focus",
                subtitle = "Enable continuous auto focus",
                checked = camera.autoFocus,
                onCheckedChange = { onUpdate(camera.copy(autoFocus = it)) },
            )

            ListItem(
                headlineContent = { Text("Zoom Level") },
                supportingContent = {
                    Column {
                        Text("${String.format("%.1f", camera.zoomLevel)}x")
                        Slider(
                            value = camera.zoomLevel,
                            onValueChange = { onUpdate(camera.copy(zoomLevel = it)) },
                            valueRange = 1f..10f,
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun SwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
