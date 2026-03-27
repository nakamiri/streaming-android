package com.reaream.app.ui.stream

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ControlBar(
    isStreaming: Boolean,
    isConnecting: Boolean,
    isMuted: Boolean,
    torchEnabled: Boolean,
    onToggleStreaming: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(12.dp)
            .background(
                Color.Black.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp),
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Go Live / Stop button
        val streamButtonColor by animateColorAsState(
            targetValue = when {
                isStreaming -> Color(0xFFFF4444)
                isConnecting -> Color(0xFFFFC107)
                else -> Color(0xFF4CAF50)
            },
            label = "streamColor",
        )

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

        // Mute
        ControlButton(
            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            label = if (isMuted) "Unmute" else "Mute",
            isActive = isMuted,
            activeColor = Color(0xFFFF6B6B),
            onClick = onToggleMute,
        )

        // Torch
        ControlButton(
            icon = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            label = "Torch",
            isActive = torchEnabled,
            activeColor = Color(0xFFFFC107),
            onClick = onToggleTorch,
        )

        // Switch Camera
        ControlButton(
            icon = Icons.Filled.Cameraswitch,
            label = "Flip",
            onClick = onSwitchCamera,
        )

        // Settings
        ControlButton(
            icon = Icons.Filled.Settings,
            label = "Settings",
            onClick = onOpenSettings,
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
