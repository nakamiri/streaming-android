package com.reaream.app.ui.stream

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ControlBar(
    isStreaming: Boolean,
    isConnecting: Boolean,
    isMuted: Boolean,
    torchEnabled: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    zoomRatio: Float = 1.0f,
    minZoomRatio: Float = 1.0f,
    maxZoomRatio: Float = 10.0f,
    hasWidgets: Boolean = false,
    onToggleStreaming: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onZoomChange: (Float) -> Unit = {},
    onEditWidgets: () -> Unit = {},
) {
    val streamButtonColor by animateColorAsState(
        targetValue = when {
            isStreaming -> Color(0xFFFF4444)
            isConnecting -> Color(0xFFFFC107)
            else -> Color(0xFF4CAF50)
        },
        label = "streamColor",
    )

    val streamButton: @Composable () -> Unit = {
        IconButton(
            onClick = onToggleStreaming,
            enabled = !isConnecting,
            modifier = Modifier
                .size(56.dp)
                .background(streamButtonColor, CircleShape),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (isStreaming) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isStreaming) "Stop" else "Go Live",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    if (isLandscape) {
        // Two-column layout: zoom on left, controls on right
        Row(
            modifier = modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left column: zoom
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ZoomSelector(zoomRatio = zoomRatio, minZoomRatio = minZoomRatio, maxZoomRatio = maxZoomRatio, onZoomChange = onZoomChange, isLandscape = true)
            }

            // Right column: controls (scrollable)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                streamButton()
                ControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    activeColor = Color(0xFFFF6B6B),
                    onClick = onToggleMute,
                )
                ControlButton(
                    icon = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    label = "Torch",
                    isActive = torchEnabled,
                    activeColor = Color(0xFFFFC107),
                    onClick = onToggleTorch,
                )
                ControlButton(
                    icon = Icons.Filled.Cameraswitch,
                    label = "Flip",
                    onClick = onSwitchCamera,
                )
                ControlButton(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    onClick = onOpenSettings,
                )
                if (hasWidgets) {
                    ControlButton(
                        icon = Icons.Filled.Edit,
                        label = "Widgets",
                        onClick = onEditWidgets,
                    )
                }
            }
        }
    } else {
        // Horizontal layout (bottom)
        Column(
            modifier = modifier
                .padding(12.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp),
                )
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ZoomSelector(zoomRatio = zoomRatio, minZoomRatio = minZoomRatio, maxZoomRatio = maxZoomRatio, onZoomChange = onZoomChange, isLandscape = false)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    activeColor = Color(0xFFFF6B6B),
                    onClick = onToggleMute,
                )
                ControlButton(
                    icon = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    label = "Torch",
                    isActive = torchEnabled,
                    activeColor = Color(0xFFFFC107),
                    onClick = onToggleTorch,
                )
                streamButton()
                ControlButton(
                    icon = Icons.Filled.Cameraswitch,
                    label = "Flip",
                    onClick = onSwitchCamera,
                )
                ControlButton(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    onClick = onOpenSettings,
                )
                if (hasWidgets) {
                    ControlButton(
                        icon = Icons.Filled.Edit,
                        label = "Widgets",
                        onClick = onEditWidgets,
                    )
                }
            }
        }
    }
}

private fun buildZoomPresets(minZoom: Float, maxZoom: Float): List<Float> {
    val presets = mutableListOf<Float>()
    // Include ultra-wide if the camera supports below 1x
    if (minZoom < 0.95f) presets.add(minZoom)
    presets.add(1.0f)
    // Add telephoto steps up to maxZoom
    for (tele in listOf(2f, 3f, 5f, 10f)) {
        if (tele <= maxZoom + 0.5f) presets.add(tele)
    }
    return presets
}

@Composable
private fun ZoomSelector(
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    isLandscape: Boolean,
) {
    val presets = buildZoomPresets(minZoomRatio, maxZoomRatio)

    if (isLandscape) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            presets.forEach { preset ->
                ZoomChip(preset = preset, isSelected = kotlin.math.abs(zoomRatio - preset) < 0.05f, onClick = { onZoomChange(preset) })
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presets.forEach { preset ->
                ZoomChip(preset = preset, isSelected = kotlin.math.abs(zoomRatio - preset) < 0.05f, onClick = { onZoomChange(preset) })
            }
        }
    }
}

@Composable
private fun ZoomChip(
    preset: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val label = if (preset >= 1f && preset == preset.toLong().toFloat()) {
        "${preset.toLong()}x"
    } else {
        // Round to 1 decimal place for wide lens values like 0.6x
        "${"%.1f".format(preset)}x"
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFFFFC107) else Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = Color(0xFF9C7CF4),
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.3f) else Color.Transparent,
                    CircleShape,
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
        )
    }
}
