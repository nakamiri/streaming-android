package com.reaream.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reaream.app.data.model.AppSettings
import com.reaream.app.streaming.StreamingEngine

@Composable
fun StreamInfoOverlay(
    streamState: StreamingEngine.StreamState,
    settings: AppSettings,
    youtubeLiveUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(12.dp)
            .background(
                Color.Black.copy(alpha = 0.6f),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Live indicator
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "LIVE",
                color = Color(0xFFFF4444),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x44FF4444), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            val qualityColor = when (streamState.connectionQuality) {
                StreamingEngine.ConnectionQuality.GOOD -> Color(0xFF4CAF50)
                StreamingEngine.ConnectionQuality.FAIR -> Color(0xFFFFC107)
                StreamingEngine.ConnectionQuality.POOR -> Color(0xFFFF4444)
                StreamingEngine.ConnectionQuality.UNKNOWN -> Color.Gray
            }
            Text(
                text = "\u25CF",
                color = qualityColor,
                fontSize = 12.sp,
            )
        }

        if (settings.display.showBitrate) {
            InfoText("${streamState.bitrateKbps} kbps")
        }

        if (settings.display.showFps && streamState.fps > 0) {
            InfoText("${streamState.fps} fps")
        }

        if (streamState.videoWidth > 0) {
            InfoText("${streamState.videoWidth}x${streamState.videoHeight}")
        }

        if (settings.display.showUptime) {
            val hours = streamState.uptime / 3600
            val minutes = (streamState.uptime % 3600) / 60
            val seconds = streamState.uptime % 60
            InfoText(String.format("%02d:%02d:%02d", hours, minutes, seconds))
        }

        if (youtubeLiveUrl != null) {
            val context = LocalContext.current
            var copied by remember { mutableStateOf(false) }
            Icon(
                imageVector = if (copied) Icons.Filled.Check else Icons.Filled.Link,
                contentDescription = "Copy YouTube URL",
                tint = if (copied) Color(0xFF4CAF50) else Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .clickable {
                        val clipboardManager = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboardManager?.setPrimaryClip(
                            android.content.ClipData.newPlainText("YouTube URL", youtubeLiveUrl)
                        )
                        copied = true
                    },
            )
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )
}
