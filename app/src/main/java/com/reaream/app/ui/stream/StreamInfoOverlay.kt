package com.reaream.app.ui.stream

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reaream.app.data.model.AppSettings
import com.reaream.app.streaming.StreamingEngine
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun StreamInfoOverlay(
    streamState: StreamingEngine.StreamState,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    youtubeLiveUrl: String? = null,
    onToggleThermalMitigation: (() -> Unit)? = null,
    onToggleScreenBlackout: (() -> Unit)? = null,
    screenBlackoutEnabled: Boolean = false,
) {
    val batteryTemperatureC = rememberBatteryTemperatureCelsius()
    val showStreamInfo = settings.display.showStreamInfo && streamState.isStreaming
    val showDeviceTemperature = settings.display.showDeviceTemperature
    var showThermalDetails by remember { mutableStateOf(false) }

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
        if (showStreamInfo) {
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
        }

        if (showStreamInfo && onToggleThermalMitigation != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThermalMitigationChip(
                    enabled = streamState.thermalMitigationEnabled,
                    onClick = onToggleThermalMitigation,
                )
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Thermal mitigation details",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showThermalDetails = true },
                )
            }
        }

        if (showStreamInfo && settings.display.showBitrate) {
            InfoText("${streamState.bitrateKbps} kbps")
        }

        if (showDeviceTemperature) {
            batteryTemperatureC?.let { temp ->
                val tempColor = when {
                    temp >= 44f -> Color(0xFFFF6B6B)
                    temp >= 40f -> Color(0xFFFFC107)
                    else -> Color.White
                }
                Text(
                    text = String.format(Locale.getDefault(), "%.1f°C", temp),
                    color = tempColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (showStreamInfo && settings.display.showFps && streamState.fps > 0) {
            val fpsColor = when {
                streamState.droppedFramesPerSec > 5 -> Color(0xFFFF4444)
                streamState.droppedFramesPerSec > 0 -> Color(0xFFFFC107)
                else -> Color.White
            }
            Text(
                text = buildString {
                    append("${streamState.fps} fps")
                    if (streamState.droppedFramesPerSec > 0) {
                        append(" ▼${streamState.droppedFramesPerSec}drop")
                    }
                },
                color = fpsColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (showStreamInfo && streamState.videoWidth > 0) {
            val resLabel = "${streamState.videoWidth}x${streamState.videoHeight}"
            InfoText(resLabel)
        }

        if (showStreamInfo && streamState.adaptiveBitrateKbps > 0) {
            Text(
                text = "▼ ${streamState.adaptiveBitrateKbps}kbps",
                color = Color(0xFFFFC107),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (showStreamInfo && settings.display.showUptime) {
            val hours = streamState.uptime / 3600
            val minutes = (streamState.uptime % 3600) / 60
            val seconds = streamState.uptime % 60
            InfoText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds))
        }

        if (showStreamInfo && youtubeLiveUrl != null) {
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

    if (showThermalDetails) {
        ThermalDetailsDialog(
            enabled = streamState.thermalMitigationEnabled,
            screenBlackoutEnabled = screenBlackoutEnabled,
            batteryTemperatureC = batteryTemperatureC,
            onDismiss = { showThermalDetails = false },
            onToggleThermalMitigation = onToggleThermalMitigation,
            onToggleScreenBlackout = onToggleScreenBlackout,
        )
    }
}

@Composable
private fun ThermalMitigationChip(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (enabled) Color(0x334FC3F7) else Color(0x22FFFFFF),
            contentColor = if (enabled) Color(0xFFB3E5FC) else Color.White,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.Thermostat,
            contentDescription = if (enabled) "Disable thermal mitigation" else "Enable thermal mitigation",
            tint = if (enabled) Color(0xFF4FC3F7) else Color.White,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (enabled) "熱対策 ON" else "熱対策",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ThermalDetailsDialog(
    enabled: Boolean,
    screenBlackoutEnabled: Boolean,
    batteryTemperatureC: Float?,
    onDismiss: () -> Unit,
    onToggleThermalMitigation: (() -> Unit)?,
    onToggleScreenBlackout: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("熱対策の内容") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("現在の熱対策では、配信を止めずに次の負荷を下げます。")
                Text("・端末プレビューの更新頻度を下げて GPU 負荷を削減")
                Text("・動画 bitrate を抑えて encoder と通信負荷を削減")
                Text(
                    batteryTemperatureC?.let {
                        String.format(Locale.getDefault(), "端末温度の目安: %.1f°C", it)
                    } ?: "端末温度は取得できませんでした"
                )
                Text(
                    "完全なバックライト OFF は通常アプリ権限ではできないため、黒画面モードは『最小輝度 + プレビュー黒塗り』で近い状態を作ります。",
                    fontSize = 13.sp,
                    color = Color.Gray,
                )

                if (onToggleThermalMitigation != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("熱対策を有効化")
                        Switch(
                            checked = enabled,
                            onCheckedChange = { onToggleThermalMitigation() },
                        )
                    }
                }

                if (onToggleScreenBlackout != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("黒画面モード")
                        Switch(
                            checked = screenBlackoutEnabled,
                            onCheckedChange = { onToggleScreenBlackout() },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        },
    )
}

@Composable
private fun rememberBatteryTemperatureCelsius(): Float? {
    val context = LocalContext.current
    return produceState<Float?>(initialValue = readBatteryTemperatureCelsius(context), context) {
        while (true) {
            value = readBatteryTemperatureCelsius(context)
            delay(5_000)
        }
    }.value
}

private fun readBatteryTemperatureCelsius(context: android.content.Context): Float? {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    return if (tempTenths == Int.MIN_VALUE) null else tempTenths / 10f
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
