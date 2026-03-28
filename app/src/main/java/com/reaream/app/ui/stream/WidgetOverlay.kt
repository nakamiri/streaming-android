package com.reaream.app.ui.stream

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reaream.app.data.model.ClockWidgetConfig
import com.reaream.app.data.model.LocationWidgetConfig
import com.reaream.app.data.model.WidgetSettings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun WidgetOverlay(
    widgetSettings: WidgetSettings,
    currentLocation: Location?,
    currentAddress: String?,
    speedKmh: Float = 0f,
    isEditMode: Boolean = false,
    onUpdateWidgets: ((WidgetSettings) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        if (containerSize.width == 0 || containerSize.height == 0) return@Box

        if (widgetSettings.clockWidget.enabled) {
            val config = widgetSettings.clockWidget
            DraggableWidget(
                x = config.x,
                y = config.y,
                fontSize = config.fontSize,
                containerSize = containerSize,
                isEditMode = isEditMode,
                onPositionChange = { newX, newY ->
                    onUpdateWidgets?.invoke(
                        widgetSettings.copy(clockWidget = config.copy(x = newX, y = newY))
                    )
                },
                onFontSizeChange = { newSize ->
                    onUpdateWidgets?.invoke(
                        widgetSettings.copy(clockWidget = config.copy(fontSize = newSize))
                    )
                },
            ) {
                ClockContent(format = config.format.pattern, fontSize = config.fontSize)
            }
        }

        if (widgetSettings.locationWidget.enabled) {
            val config = widgetSettings.locationWidget
            val text = when {
                currentAddress != null -> currentAddress
                currentLocation != null -> String.format(
                    Locale.US, "%.4f, %.4f", currentLocation.latitude, currentLocation.longitude
                )
                else -> null
            }

            if (text != null) {
                DraggableWidget(
                    x = config.x,
                    y = config.y,
                    fontSize = config.fontSize,
                    containerSize = containerSize,
                    isEditMode = isEditMode,
                    onPositionChange = { newX, newY ->
                        onUpdateWidgets?.invoke(
                            widgetSettings.copy(locationWidget = config.copy(x = newX, y = newY))
                        )
                    },
                    onFontSizeChange = { newSize ->
                        onUpdateWidgets?.invoke(
                            widgetSettings.copy(locationWidget = config.copy(fontSize = newSize))
                        )
                    },
                ) {
                    WidgetBadge(text = text, fontSize = config.fontSize)
                }
            }
        }

        if (widgetSettings.speedWidget.enabled && speedKmh >= 0f) {
            val config = widgetSettings.speedWidget
            val value = if (config.unit == com.reaream.app.data.model.SpeedUnit.MPH) speedKmh * 0.621371f else speedKmh
            val text = String.format(Locale.US, "%.0f %s", value, config.unit.label)

            DraggableWidget(
                x = config.x,
                y = config.y,
                fontSize = config.fontSize,
                containerSize = containerSize,
                isEditMode = isEditMode,
                onPositionChange = { newX, newY ->
                    onUpdateWidgets?.invoke(
                        widgetSettings.copy(speedWidget = config.copy(x = newX, y = newY))
                    )
                },
                onFontSizeChange = { newSize ->
                    onUpdateWidgets?.invoke(
                        widgetSettings.copy(speedWidget = config.copy(fontSize = newSize))
                    )
                },
            ) {
                WidgetBadge(text = text, fontSize = config.fontSize)
            }
        }
    }
}

@Composable
private fun DraggableWidget(
    x: Float,
    y: Float,
    fontSize: Int,
    containerSize: IntSize,
    isEditMode: Boolean,
    onPositionChange: (Float, Float) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    var widgetSize by remember { mutableStateOf(IntSize.Zero) }
    var offsetX by remember(x, containerSize) { mutableFloatStateOf(x * containerSize.width) }
    var offsetY by remember(y, containerSize) { mutableFloatStateOf(y * containerSize.height) }

    // Clamp to prevent overflow on initial layout
    val clampedX = offsetX.coerceIn(0f, (containerSize.width - widgetSize.width).coerceAtLeast(0).toFloat())
    val clampedY = offsetY.coerceIn(0f, (containerSize.height - widgetSize.height).coerceAtLeast(0).toFloat())

    Box(
        modifier = Modifier
            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
            .onSizeChanged { widgetSize = it }
            .then(
                if (isEditMode) {
                    Modifier
                        .border(1.dp, Color(0xFFFFC107), RoundedCornerShape(6.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val maxX = (containerSize.width - widgetSize.width).coerceAtLeast(0).toFloat()
                                val maxY = (containerSize.height - widgetSize.height).coerceAtLeast(0).toFloat()
                                offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxX)
                                offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxY)
                                onPositionChange(
                                    offsetX / containerSize.width,
                                    offsetY / containerSize.height,
                                )
                            }
                        }
                } else Modifier
            ),
    ) {
        Column {
            content()
            if (isEditMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
                ) {
                    SizeButton(icon = Icons.Filled.Remove) {
                        if (fontSize > 8) onFontSizeChange(fontSize - 2)
                    }
                    Text(
                        text = "${fontSize}",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(Color(0x80000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    SizeButton(icon = Icons.Filled.Add) {
                        if (fontSize < 40) onFontSizeChange(fontSize + 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(24.dp)
            .background(Color(0xCC000000), CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ClockContent(format: String, fontSize: Int) {
    var time by remember { mutableStateOf("") }

    LaunchedEffect(format) {
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        while (true) {
            time = sdf.format(Date())
            delay(1000)
        }
    }

    WidgetBadge(text = time, fontSize = fontSize)
}

@Composable
private fun WidgetBadge(text: String, fontSize: Int) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(Color(0xA0000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
