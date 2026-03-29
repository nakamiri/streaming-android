package com.reaream.app.ui.stream

import android.Manifest
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
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
    modifier: Modifier = Modifier,
    speedKmh: Float = 0f,
    mapBitmap: android.graphics.Bitmap? = null,
    locationPermissionDenied: Boolean = false,
    isEditMode: Boolean = false,
    onUpdateWidgets: ((WidgetSettings) -> Unit)? = null,
    onRecheckPermission: (() -> Unit)? = null,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Use updatedState so lambdas always see latest widgetSettings
    val currentWidgets by rememberUpdatedState(widgetSettings)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        if (containerSize.width == 0 || containerSize.height == 0) return@Box

        if (widgetSettings.mapWidget.enabled && mapBitmap != null) {
            DraggableWidget(
                x = widgetSettings.mapWidget.x,
                y = widgetSettings.mapWidget.y,
                fontSize = widgetSettings.mapWidget.sizeDp,
                containerSize = containerSize,
                isEditMode = isEditMode,
                minSize = 60,
                maxSize = 400,
                sizeStep = 10,
                onPositionChange = { newX, newY ->
                    val w = currentWidgets
                    onUpdateWidgets?.invoke(w.copy(mapWidget = w.mapWidget.copy(x = newX, y = newY)))
                },
                onFontSizeChange = { newSize ->
                    val w = currentWidgets
                    onUpdateWidgets?.invoke(w.copy(mapWidget = w.mapWidget.copy(sizeDp = newSize.coerceIn(60, 400))))
                },
                extraEditContent = if (isEditMode) {
                    {
                        val mapConfig = currentWidgets.mapWidget
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            SizeButton(icon = Icons.Filled.ZoomOut) {
                                if (mapConfig.zoom > 10) {
                                    val w = currentWidgets
                                    onUpdateWidgets?.invoke(w.copy(mapWidget = w.mapWidget.copy(zoom = w.mapWidget.zoom - 1)))
                                }
                            }
                            Text(
                                text = "z${mapConfig.zoom}",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .background(Color(0x80000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            SizeButton(icon = Icons.Filled.ZoomIn) {
                                if (mapConfig.zoom < 18) {
                                    val w = currentWidgets
                                    onUpdateWidgets?.invoke(w.copy(mapWidget = w.mapWidget.copy(zoom = w.mapWidget.zoom + 1)))
                                }
                            }
                        }
                    }
                } else null,
            ) {
                MapWidgetView(bitmap = mapBitmap, sizeDp = widgetSettings.mapWidget.sizeDp)
            }
        }

        if (widgetSettings.clockWidget.enabled) {
            DraggableWidget(
                x = widgetSettings.clockWidget.x,
                y = widgetSettings.clockWidget.y,
                fontSize = widgetSettings.clockWidget.fontSize,
                containerSize = containerSize,
                isEditMode = isEditMode,
                onPositionChange = { newX, newY ->
                    val w = currentWidgets
                    onUpdateWidgets?.invoke(w.copy(clockWidget = w.clockWidget.copy(x = newX, y = newY)))
                },
                onFontSizeChange = { newSize ->
                    val w = currentWidgets
                    onUpdateWidgets?.invoke(w.copy(clockWidget = w.clockWidget.copy(fontSize = newSize)))
                },
            ) {
                ClockContent(format = widgetSettings.clockWidget.format.pattern, fontSize = widgetSettings.clockWidget.fontSize)
            }
        }

        if (widgetSettings.locationWidget.enabled) {
            val text = when {
                currentAddress != null -> currentAddress
                currentLocation != null -> String.format(
                    Locale.US, "%.4f, %.4f", currentLocation.latitude, currentLocation.longitude
                )
                else -> null
            }

            if (text != null) {
                DraggableWidget(
                    x = widgetSettings.locationWidget.x,
                    y = widgetSettings.locationWidget.y,
                    fontSize = widgetSettings.locationWidget.fontSize,
                    containerSize = containerSize,
                    isEditMode = isEditMode,
                    onPositionChange = { newX, newY ->
                        val w = currentWidgets
                        onUpdateWidgets?.invoke(w.copy(locationWidget = w.locationWidget.copy(x = newX, y = newY)))
                    },
                    onFontSizeChange = { newSize ->
                        val w = currentWidgets
                        onUpdateWidgets?.invoke(w.copy(locationWidget = w.locationWidget.copy(fontSize = newSize)))
                    },
                ) {
                    WidgetBadge(text = text, fontSize = widgetSettings.locationWidget.fontSize)
                }
            }
        }

        if (widgetSettings.speedWidget.enabled && speedKmh >= 0f) {
            val speedConfig = widgetSettings.speedWidget
            val value = if (speedConfig.unit == com.reaream.app.data.model.SpeedUnit.MPH) speedKmh * 0.621371f else speedKmh
            val text = String.format(Locale.US, "%.0f %s", value, speedConfig.unit.label)

            DraggableWidget(
                x = speedConfig.x,
                y = speedConfig.y,
                fontSize = speedConfig.fontSize,
                containerSize = containerSize,
                isEditMode = isEditMode,
                onPositionChange = { newX, newY ->
                    val w = currentWidgets
                    onUpdateWidgets?.invoke(w.copy(speedWidget = w.speedWidget.copy(x = newX, y = newY)))
                },
                onFontSizeChange = { newSize ->
                    val w = currentWidgets
                    onUpdateWidgets?.invoke(w.copy(speedWidget = w.speedWidget.copy(fontSize = newSize)))
                },
            ) {
                WidgetBadge(text = text, fontSize = speedConfig.fontSize)
            }
        }

        // Permission warning with tap to request
        val needsLocation = widgetSettings.locationWidget.enabled || widgetSettings.speedWidget.enabled || widgetSettings.mapWidget.enabled
        if (needsLocation && locationPermissionDenied && !isEditMode) {
            LocationPermissionBanner(
                onPermissionGranted = { onRecheckPermission?.invoke() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocationPermissionBanner(
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionsState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ) { results ->
        if (results.values.any { it }) {
            onPermissionGranted()
        }
    }

    Text(
        text = "⚠ タップして位置情報の権限を許可",
        color = Color(0xFFFFC107),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(bottom = 120.dp)
            .clickable { permissionsState.launchMultiplePermissionRequest() }
            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
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
    minSize: Int = 8,
    maxSize: Int = 60,
    sizeStep: Int = 2,
    extraEditContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var widgetSize by remember { mutableStateOf(IntSize.Zero) }
    var offsetX by remember { mutableFloatStateOf(x * containerSize.width) }
    var offsetY by remember { mutableFloatStateOf(y * containerSize.height) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync from external state changes (e.g. reset), but not during drag
    LaunchedEffect(x, y, containerSize) {
        if (!isDragging) {
            offsetX = x * containerSize.width
            offsetY = y * containerSize.height
        }
    }

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
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                            ) { change, dragAmount ->
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isEditMode) {
                extraEditContent?.invoke()
            }
            content()
            if (isEditMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    SizeButton(icon = Icons.Filled.Remove) {
                        if (fontSize > minSize) onFontSizeChange(fontSize - sizeStep)
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
                        if (fontSize < maxSize) onFontSizeChange(fontSize + sizeStep)
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

@Composable
private fun MapWidgetView(bitmap: android.graphics.Bitmap, sizeDp: Int) {
    val imageBitmap = remember(bitmap) {
        bitmap.asImageBitmap()
    }
    androidx.compose.foundation.Image(
        bitmap = imageBitmap,
        contentDescription = "Map",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
    )
}
