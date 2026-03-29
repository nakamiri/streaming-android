package com.reaream.app.ui.stream

import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.reaream.app.data.model.AppSettings
import com.reaream.app.data.model.Resolution
import com.reaream.app.streaming.StreamingEngine
import com.reaream.app.chat.ChatMessage

@Composable
fun StreamScreen(
    settings: AppSettings,
    streamState: StreamingEngine.StreamState,
    chatMessages: List<ChatMessage>,
    torchEnabled: Boolean,
    onToggleStreaming: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    engine: StreamingEngine,
    modifier: Modifier = Modifier,
    onClearError: () -> Unit = {},
    currentLocation: android.location.Location? = null,
    currentAddress: String? = null,
    speedKmh: Float = 0f,
    locationPermissionDenied: Boolean = false,
    mapBitmap: android.graphics.Bitmap? = null,
    onUpdateWidgets: ((com.reaream.app.data.model.WidgetSettings) -> Unit)? = null,
    onRecheckPermission: (() -> Unit)? = null,
    onSetDensity: ((Float) -> Unit)? = null,
    youtubeSetupError: String? = null,
    onClearYoutubeError: (() -> Unit)? = null,
    youtubeLiveUrl: String? = null,
    broadcastPicker: com.reaream.app.ui.MainViewModel.BroadcastPickerState = com.reaream.app.ui.MainViewModel.BroadcastPickerState(),
    onSelectBroadcast: ((String?) -> Unit)? = null,
    onDismissBroadcastPicker: (() -> Unit)? = null,
    stopConfirmVisible: Boolean = false,
    onConfirmStop: ((endBroadcast: Boolean) -> Unit)? = null,
    onDismissStopConfirm: (() -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10.0f) }
    var widgetEditMode by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    LaunchedEffect(density) { onSetDensity?.invoke(density) }

    val fallbackWidth = settings.currentStream.resolution.width
    val fallbackHeight = settings.currentStream.resolution.height
    val previewWidth = if (streamState.videoWidth > 0) streamState.videoWidth else fallbackWidth
    val previewHeight = if (streamState.videoHeight > 0) streamState.videoHeight else fallbackHeight
    val safeAspect = computeVideoAspectRatio(previewWidth, previewHeight, isLandscape)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(
                    if (isLandscape) Modifier.fillMaxHeight().aspectRatio(safeAspect)
                    else Modifier.fillMaxWidth().aspectRatio(safeAspect)
                ),
        ) {
            CameraPreview(
                engine = engine,
                useFrontCamera = settings.camera.useFrontCamera,
                torchEnabled = torchEnabled,
                zoomRatio = zoomRatio,
                fps = settings.currentStream.fps,
                resolution = settings.currentStream.resolution,
                onCameraZoomRange = { min, max ->
                    minZoomRatio = min
                    maxZoomRatio = max
                    if (zoomRatio < min) zoomRatio = 1.0f
                },
                modifier = Modifier.fillMaxSize(),
            )

            WidgetOverlay(
                widgetSettings = settings.widgets,
                currentLocation = currentLocation,
                currentAddress = currentAddress,
                speedKmh = speedKmh,
                mapBitmap = mapBitmap,
                locationPermissionDenied = locationPermissionDenied,
                isEditMode = widgetEditMode,
                onUpdateWidgets = onUpdateWidgets,
                onRecheckPermission = onRecheckPermission,
            )
        }

        if (!widgetEditMode) {
            if (isLandscape) {
                LandscapeOverlay(
                    settings = settings,
                    streamState = streamState,
                    chatMessages = chatMessages,
                    torchEnabled = torchEnabled,
                    zoomRatio = zoomRatio,
                    minZoomRatio = minZoomRatio,
                    maxZoomRatio = maxZoomRatio,
                    hasWidgets = settings.widgets.let { it.clockWidget.enabled || it.locationWidget.enabled || it.speedWidget.enabled || it.mapWidget.enabled },
                    youtubeLiveUrl = youtubeLiveUrl,
                    onToggleStreaming = onToggleStreaming,
                    onToggleMute = onToggleMute,
                    onToggleTorch = onToggleTorch,
                    onSwitchCamera = onSwitchCamera,
                    onOpenSettings = onOpenSettings,
                    onZoomChange = { zoomRatio = it },
                    onEditWidgets = { widgetEditMode = true },
                )
            } else {
                PortraitOverlay(
                    settings = settings,
                    streamState = streamState,
                    chatMessages = chatMessages,
                    torchEnabled = torchEnabled,
                    zoomRatio = zoomRatio,
                    minZoomRatio = minZoomRatio,
                    maxZoomRatio = maxZoomRatio,
                    hasWidgets = settings.widgets.let { it.clockWidget.enabled || it.locationWidget.enabled || it.speedWidget.enabled || it.mapWidget.enabled },
                    youtubeLiveUrl = youtubeLiveUrl,
                    onToggleStreaming = onToggleStreaming,
                    onToggleMute = onToggleMute,
                    onToggleTorch = onToggleTorch,
                    onSwitchCamera = onSwitchCamera,
                    onOpenSettings = onOpenSettings,
                    onZoomChange = { zoomRatio = it },
                    onEditWidgets = { widgetEditMode = true },
                )
            }
        }

        // Widget edit mode buttons (reset/done)
        if (widgetEditMode) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                androidx.compose.material3.Button(
                    onClick = {
                        onUpdateWidgets?.invoke(
                            settings.widgets.copy(
                                clockWidget = settings.widgets.clockWidget.copy(
                                    x = com.reaream.app.data.model.ClockWidgetConfig().x,
                                    y = com.reaream.app.data.model.ClockWidgetConfig().y,
                                    fontSize = com.reaream.app.data.model.ClockWidgetConfig().fontSize,
                                ),
                                locationWidget = settings.widgets.locationWidget.copy(
                                    x = com.reaream.app.data.model.LocationWidgetConfig().x,
                                    y = com.reaream.app.data.model.LocationWidgetConfig().y,
                                    fontSize = com.reaream.app.data.model.LocationWidgetConfig().fontSize,
                                ),
                                speedWidget = settings.widgets.speedWidget.copy(
                                    x = com.reaream.app.data.model.SpeedWidgetConfig().x,
                                    y = com.reaream.app.data.model.SpeedWidgetConfig().y,
                                    fontSize = com.reaream.app.data.model.SpeedWidgetConfig().fontSize,
                                ),
                                mapWidget = settings.widgets.mapWidget.copy(
                                    x = com.reaream.app.data.model.MapWidgetConfig().x,
                                    y = com.reaream.app.data.model.MapWidgetConfig().y,
                                    sizeDp = com.reaream.app.data.model.MapWidgetConfig().sizeDp,
                                ),
                            )
                        )
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF666666),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("リセット", fontSize = 14.sp)
                }
                androidx.compose.material3.Button(
                    onClick = { widgetEditMode = false },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC107),
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("完了", fontSize = 14.sp)
                }
            }
        }

        // Error message
        val displayError = youtubeSetupError ?: streamState.error
        displayError?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clickable {
                        if (youtubeSetupError != null) onClearYoutubeError?.invoke()
                        else onClearError()
                    }
                    .background(
                        Color(0xCC000000),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = error,
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                )
            }
        }

        // Stop confirmation dialog (YouTube OAuth only)
        if (stopConfirmVisible) {
            StopConfirmDialog(
                onPause = { onConfirmStop?.invoke(false) },
                onEnd = { onConfirmStop?.invoke(true) },
                onDismiss = { onDismissStopConfirm?.invoke() },
            )
        }

        // YouTube broadcast picker dialog
        if (broadcastPicker.isVisible) {
            BroadcastPickerDialog(
                isLoading = broadcastPicker.isLoading,
                broadcasts = broadcastPicker.broadcasts,
                onSelect = { onSelectBroadcast?.invoke(it) },
                onDismiss = { onDismissBroadcastPicker?.invoke() },
            )
        }
    }
}

@Composable
private fun StopConfirmDialog(
    onPause: () -> Unit,
    onEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("配信を停止しますか？") },
        text = {
            androidx.compose.material3.Text(
                "「一時停止」はRTMP接続のみ切断します。\nYouTube配信枠は維持され、再配信ボタンから再接続できます。"
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onEnd) {
                androidx.compose.material3.Text("配信終了", color = Color(0xFFFF6B6B))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onPause) {
                androidx.compose.material3.Text("一時停止")
            }
        },
    )
}

@Composable
private fun BroadcastPickerDialog(
    isLoading: Boolean,
    broadcasts: List<com.reaream.app.data.YouTubeApiClient.BroadcastInfo>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("配信枠を選択") },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Existing broadcasts
                    broadcasts.forEach { broadcast ->
                        val statusLabel = when (broadcast.status) {
                            "live" -> "LIVE"
                            "ready" -> "配信準備完了"
                            else -> broadcast.status
                        }
                        val statusColor = if (broadcast.status == "live") Color(0xFFFF4444) else Color.Gray
                        androidx.compose.material3.OutlinedCard(
                            onClick = { onSelect(broadcast.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        broadcast.title,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                    )
                                    Text(
                                        statusLabel,
                                        fontSize = 11.sp,
                                        color = statusColor,
                                    )
                                }
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSelect(null) }) {
                androidx.compose.material3.Text("新規作成")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("キャンセル")
            }
        },
    )
}

@Composable
private fun LandscapeOverlay(
    settings: AppSettings,
    streamState: StreamingEngine.StreamState,
    chatMessages: List<ChatMessage>,
    torchEnabled: Boolean,
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    hasWidgets: Boolean,
    youtubeLiveUrl: String?,
    onToggleStreaming: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onEditWidgets: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (settings.display.showStreamInfo && streamState.isStreaming) {
            StreamInfoOverlay(
                streamState = streamState,
                settings = settings,
                youtubeLiveUrl = youtubeLiveUrl,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding(),
            )
        }

        if (settings.display.showChat && chatMessages.isNotEmpty()) {
            ChatOverlay(
                messages = chatMessages,
                fontSize = settings.chat.fontSize,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight(0.5f),
            )
        }

        ControlBar(
            isStreaming = streamState.isStreaming,
            isConnecting = streamState.isConnecting,
            isMuted = settings.audio.muted,
            torchEnabled = torchEnabled,
            isLandscape = true,
            zoomRatio = zoomRatio,
            minZoomRatio = minZoomRatio,
            maxZoomRatio = maxZoomRatio,
            hasWidgets = hasWidgets,
            onToggleStreaming = onToggleStreaming,
            onToggleMute = onToggleMute,
            onToggleTorch = onToggleTorch,
            onSwitchCamera = onSwitchCamera,
            onOpenSettings = onOpenSettings,
            onZoomChange = onZoomChange,
            onEditWidgets = onEditWidgets,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .systemBarsPadding(),
        )
    }
}

@Composable
private fun PortraitOverlay(
    settings: AppSettings,
    streamState: StreamingEngine.StreamState,
    chatMessages: List<ChatMessage>,
    torchEnabled: Boolean,
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    hasWidgets: Boolean,
    youtubeLiveUrl: String?,
    onToggleStreaming: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onEditWidgets: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (settings.display.showStreamInfo && streamState.isStreaming) {
                StreamInfoOverlay(
                    streamState = streamState,
                    settings = settings,
                    youtubeLiveUrl = youtubeLiveUrl,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding(),
                )
            }

            if (settings.display.showChat && chatMessages.isNotEmpty()) {
                ChatOverlay(
                    messages = chatMessages,
                    fontSize = settings.chat.fontSize,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.7f)
                        .fillMaxHeight(0.4f),
                )
            }
        }

        ControlBar(
            isStreaming = streamState.isStreaming,
            isConnecting = streamState.isConnecting,
            isMuted = settings.audio.muted,
            torchEnabled = torchEnabled,
            isLandscape = false,
            zoomRatio = zoomRatio,
            minZoomRatio = minZoomRatio,
            maxZoomRatio = maxZoomRatio,
            hasWidgets = hasWidgets,
            onToggleStreaming = onToggleStreaming,
            onToggleMute = onToggleMute,
            onToggleTorch = onToggleTorch,
            onSwitchCamera = onSwitchCamera,
            onOpenSettings = onOpenSettings,
            onZoomChange = onZoomChange,
            onEditWidgets = onEditWidgets,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

@Composable
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
fun CameraPreview(
    engine: StreamingEngine,
    useFrontCamera: Boolean,
    torchEnabled: Boolean,
    modifier: Modifier = Modifier,
    zoomRatio: Float = 1.0f,
    fps: Int = 30,
    resolution: Resolution = Resolution.HD_720,
    onCameraZoomRange: ((minZoom: Float, maxZoom: Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val glPipeline = engine.glPipeline
    val cameraManager = remember(context) {
        context.getSystemService(CameraManager::class.java)
    }
    val displayRotation = view.display?.rotation ?: Surface.ROTATION_0

    val cameraSelector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    var cameraInstance by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(zoomRatio) {
        cameraInstance?.cameraControl?.setZoomRatio(zoomRatio)
    }

    LaunchedEffect(torchEnabled) {
        cameraInstance?.cameraControl?.enableTorch(torchEnabled)
    }

    LaunchedEffect(useFrontCamera) {
        glPipeline.setFrontCamera(useFrontCamera)
    }

    DisposableEffect(cameraSelector, fps, resolution, displayRotation) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbindAll()

                val longSide = maxOf(resolution.width, resolution.height)
                val shortSide = minOf(resolution.width, resolution.height)
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(longSide, shortSide),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .setAspectRatioStrategy(
                        AspectRatioStrategy(
                            AspectRatio.RATIO_16_9,
                            AspectRatioStrategy.FALLBACK_RULE_AUTO,
                        )
                    )
                    .build()
                val targetFpsRange = findTargetFpsRange(cameraManager, useFrontCamera, fps)
                val fallbackRotation = findCameraPreviewRotation(cameraManager, useFrontCamera, displayRotation)
                glPipeline.updateFallbackRotation(fallbackRotation)
                Log.i(
                    "CameraPreview",
                    "Binding camera with displayRotation=${surfaceRotationToDegrees(displayRotation)} fallbackRotation=$fallbackRotation",
                )

                val preview = Preview.Builder().also { builder ->
                    builder.setTargetRotation(displayRotation)
                    builder.setResolutionSelector(resolutionSelector)
                    if (targetFpsRange != null) {
                        Camera2Interop.Extender(builder)
                            .setCaptureRequestOption(
                                android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                targetFpsRange,
                            )
                    }
                }.build()

                preview.setSurfaceProvider(glPipeline.glExecutor) { request ->
                    val size = request.resolution
                    Log.i("CameraPreview", "Surface request resolution=${size.width}x${size.height}")
                    val surface = glPipeline.prepareCameraSurface(size.width, size.height)
                    if (surface != null) {
                        request.setTransformationInfoListener(glPipeline.glExecutor) { info ->
                            val resolvedRotation = when {
                                info.rotationDegrees != 0 -> info.rotationDegrees
                                glPipeline.fallbackRotation != 0 -> glPipeline.fallbackRotation
                                else -> 0
                            }
                            glPipeline.cameraRotation = resolvedRotation
                            Log.i(
                                "CameraPreview",
                                "TransformationInfo rotation=${info.rotationDegrees} resolved=$resolvedRotation fallback=${glPipeline.fallbackRotation}",
                            )
                        }
                        request.provideSurface(surface, glPipeline.glExecutor) { /* released */ }
                    } else {
                        request.willNotProvideSurface()
                    }
                }

                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                camera.cameraControl.enableTorch(torchEnabled)
                camera.cameraControl.setZoomRatio(zoomRatio)
                cameraInstance = camera
                camera.cameraInfo.zoomState.value?.let { zs ->
                    onCameraZoomRange?.invoke(zs.minZoomRatio, zs.maxZoomRatio)
                }
            } catch (e: Exception) {
                android.util.Log.e("CameraPreview", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) {}
        }
    }

    // SurfaceView for GL rendering output (live camera preview on screen)
    AndroidView(
        factory = { ctx ->
            android.view.SurfaceView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {}
                    override fun surfaceChanged(
                        holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int
                    ) {
                        glPipeline.attachDisplaySurface(holder.surface, width, height)
                    }
                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        glPipeline.detachDisplaySurface()
                    }
                })
            }
        },
        modifier = modifier,
    )
}

private fun computeVideoAspectRatio(width: Int, height: Int, isLandscape: Boolean): Float {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val aspect = if (isLandscape) {
        maxOf(w, h).toFloat() / minOf(w, h).toFloat()
    } else {
        minOf(w, h).toFloat() / maxOf(w, h).toFloat()
    }
    return if (aspect.isNaN() || aspect <= 0f) {
        if (isLandscape) 16f / 9f else 9f / 16f
    } else {
        aspect
    }
}

private fun findTargetFpsRange(
    cameraManager: CameraManager?,
    useFrontCamera: Boolean,
    targetFps: Int,
): Range<Int>? {
    if (cameraManager == null || targetFps <= 30) return null

    return try {
        val desiredFacing = if (useFrontCamera) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desiredFacing
        } ?: return null

        val ranges = cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.toList()
            .orEmpty()

        val exactMatch = ranges.firstOrNull { it.lower == targetFps && it.upper == targetFps }
        if (exactMatch != null) return exactMatch

        ranges
            .filter { it.upper >= targetFps }
            .minWithOrNull(
                compareBy<Range<Int>> { it.upper - targetFps }
                    .thenBy { targetFps - minOf(it.lower, targetFps) }
            )
    } catch (_: Exception) {
        null
    }
}

private fun findCameraPreviewRotation(
    cameraManager: CameraManager?,
    useFrontCamera: Boolean,
    displayRotation: Int,
): Int {
    if (cameraManager == null) return surfaceRotationToDegrees(displayRotation)

    return try {
        val desiredFacing = if (useFrontCamera) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desiredFacing
        } ?: return surfaceRotationToDegrees(displayRotation)

        val sensorOrientation = cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayDegrees = surfaceRotationToDegrees(displayRotation)

        if (useFrontCamera) {
            (sensorOrientation + displayDegrees) % 360
        } else {
            (sensorOrientation - displayDegrees + 360) % 360
        }
    } catch (_: Exception) {
        surfaceRotationToDegrees(displayRotation)
    }
}

private fun surfaceRotationToDegrees(rotation: Int): Int = when (rotation) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}
