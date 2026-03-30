package com.reaream.app.ui.settings

import android.hardware.camera2.CameraManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reaream.app.camera.getVideoStabilizationSupport
import com.reaream.app.data.model.CameraSettings
import com.reaream.app.data.model.VideoStabilizationMode
import com.reaream.app.ui.Screen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingsScreen(
    camera: CameraSettings,
    onBack: () -> Unit,
    onUpdate: (CameraSettings) -> Unit,
) {
    val context = LocalContext.current
    val cameraManager = remember(context) {
        context.getSystemService(CameraManager::class.java)
    }
    val stabilizationSupport = remember(cameraManager, camera.useFrontCamera) {
        getVideoStabilizationSupport(cameraManager, camera.useFrontCamera)
    }
    val hasElectronicStabilization = stabilizationSupport.electronicMode != null
    val hasOpticalStabilization = stabilizationSupport.hasOpticalStabilization
    val selectedCameraLabel = if (camera.useFrontCamera) "front" else "back"
    val stabilizationSubtitle = buildString {
        append("Selected camera: $selectedCameraLabel\n")
        append(if (hasOpticalStabilization) {
            "Optical stabilization is available"
        } else {
            "Optical stabilization is not available"
        })
        append("\n")
        append(
            when (stabilizationSupport.electronicMode) {
                android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION -> {
                    "Electronic stabilization is available as Preview stabilization"
                }
                null -> "Electronic stabilization is not available"
                else -> "Electronic stabilization is available as Video stabilization"
            }
        )
        append("\nAuto prefers optical, then falls back to electronic")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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

            ListItem(
                headlineContent = { Text("Stabilization Mode") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stabilizationSubtitle)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            VideoStabilizationMode.entries.forEachIndexed { index, mode ->
                                val enabled = when (mode) {
                                    VideoStabilizationMode.AUTO -> hasOpticalStabilization || hasElectronicStabilization
                                    VideoStabilizationMode.OPTICAL -> hasOpticalStabilization
                                    VideoStabilizationMode.ELECTRONIC -> hasElectronicStabilization
                                }
                                SegmentedButton(
                                    selected = camera.stabilizationMode == mode,
                                    enabled = enabled,
                                    onClick = {
                                        onUpdate(
                                            camera.copy(
                                                videoStabilization = mode == VideoStabilizationMode.ELECTRONIC,
                                                stabilizationMode = mode,
                                            )
                                        )
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = VideoStabilizationMode.entries.size,
                                    ),
                                ) {
                                    Text(mode.displayName)
                                }
                            }
                        }
                    }
                },
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
                        Text("${String.format(Locale.getDefault(), "%.1f", camera.zoomLevel)}x")
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
