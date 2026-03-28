package com.reaream.app.ui.stream

import android.content.res.Configuration
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.reaream.app.data.model.AppSettings
import com.reaream.app.streaming.StreamingEngine
import com.reaream.app.chat.ChatMessage
import java.nio.ByteBuffer
import java.util.concurrent.Executors

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
    onClearError: () -> Unit = {},
    onVideoFrame: ((ByteBuffer, Int, Int, Long) -> Unit)? = null,
    videoWidth: Int = 1280,
    videoHeight: Int = 720,
    currentLocation: android.location.Location? = null,
    currentAddress: String? = null,
    speedKmh: Float = 0f,
    locationPermissionDenied: Boolean = false,
    onUpdateWidgets: ((com.reaream.app.data.model.WidgetSettings) -> Unit)? = null,
    onRecheckPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var widgetEditMode by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        CameraPreview(
            useFrontCamera = settings.camera.useFrontCamera,
            torchEnabled = torchEnabled,
            zoomRatio = zoomRatio,
            onVideoFrame = onVideoFrame,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            modifier = Modifier.fillMaxSize(),
        )

        if (!widgetEditMode) {
            if (isLandscape) {
                LandscapeOverlay(
                    settings = settings,
                    streamState = streamState,
                    chatMessages = chatMessages,
                    torchEnabled = torchEnabled,
                    zoomRatio = zoomRatio,
                    hasWidgets = settings.widgets.clockWidget.enabled || settings.widgets.locationWidget.enabled || settings.widgets.speedWidget.enabled,
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
                    hasWidgets = settings.widgets.clockWidget.enabled || settings.widgets.locationWidget.enabled || settings.widgets.speedWidget.enabled,
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

        // Widget overlay
        WidgetOverlay(
            widgetSettings = settings.widgets,
            currentLocation = currentLocation,
            currentAddress = currentAddress,
            speedKmh = speedKmh,
            locationPermissionDenied = locationPermissionDenied,
            isEditMode = widgetEditMode,
            onUpdateWidgets = onUpdateWidgets,
            onRecheckPermission = onRecheckPermission,
        )

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
        streamState.error?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clickable { onClearError() }
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
    }
}

@Composable
private fun LandscapeOverlay(
    settings: AppSettings,
    streamState: StreamingEngine.StreamState,
    chatMessages: List<ChatMessage>,
    torchEnabled: Boolean,
    zoomRatio: Float,
    hasWidgets: Boolean,
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
            hasWidgets = hasWidgets,
            onToggleStreaming = onToggleStreaming,
            onToggleMute = onToggleMute,
            onToggleTorch = onToggleTorch,
            onSwitchCamera = onSwitchCamera,
            onOpenSettings = onOpenSettings,
            onZoomChange = onZoomChange,
            onEditWidgets = onEditWidgets,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding(),
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
    hasWidgets: Boolean,
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
fun CameraPreview(
    useFrontCamera: Boolean,
    torchEnabled: Boolean,
    zoomRatio: Float = 1.0f,
    onVideoFrame: ((ByteBuffer, Int, Int, Long) -> Unit)? = null,
    videoWidth: Int = 1280,
    videoHeight: Int = 720,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraSelector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var cameraInstance by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(zoomRatio) {
        cameraInstance?.cameraControl?.setZoomRatio(zoomRatio)
    }

    DisposableEffect(cameraSelector, torchEnabled) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            try {
                cameraProvider.unbindAll()

                if (onVideoFrame != null) {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(videoWidth, videoHeight))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val srcW = imageProxy.width
                                val srcH = imageProxy.height
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val yuv = imageProxyToYuv420(imageProxy)
                                val timestampUs = imageProxy.imageInfo.timestamp / 1000
                                imageProxy.close()

                                val frame = com.reaream.app.streaming.YuvUtils.rotateI420(yuv, srcW, srcH, rotation)
                                onVideoFrame(ByteBuffer.wrap(frame.data), frame.width, frame.height, timestampUs)
                            }
                        }

                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                    )
                    camera.cameraControl.enableTorch(torchEnabled)
                    camera.cameraControl.setZoomRatio(zoomRatio)
                    cameraInstance = camera
                } else {
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                    )
                    camera.cameraControl.enableTorch(torchEnabled)
                    camera.cameraControl.setZoomRatio(zoomRatio)
                    cameraInstance = camera
                }
            } catch (e: Exception) {
                // Camera binding failed
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            val cameraProvider = try {
                ProcessCameraProvider.getInstance(context).get()
            } catch (e: Exception) {
                null
            }
            cameraProvider?.unbindAll()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

private fun imageProxyToYuv420(image: ImageProxy): ByteArray {
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]
    val width = image.width
    val height = image.height
    val uvHeight = height / 2
    val uvWidth = width / 2

    // I420 format: Y plane, then U plane, then V plane (all separate)
    val yuv = ByteArray(width * height * 3 / 2)

    // Copy Y plane
    val yBuffer = yPlane.buffer.duplicate()
    val yRowStride = yPlane.rowStride
    var offset = 0
    for (row in 0 until height) {
        yBuffer.position(row * yRowStride)
        yBuffer.get(yuv, offset, width)
        offset += width
    }

    // Copy U plane
    val uBuffer = uPlane.buffer.duplicate()
    val uvPixelStride = uPlane.pixelStride
    val uvRowStride = uPlane.rowStride
    for (row in 0 until uvHeight) {
        for (col in 0 until uvWidth) {
            yuv[offset++] = uBuffer.get(row * uvRowStride + col * uvPixelStride)
        }
    }

    // Copy V plane
    val vBuffer = vPlane.buffer.duplicate()
    for (row in 0 until uvHeight) {
        for (col in 0 until uvWidth) {
            yuv[offset++] = vBuffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
        }
    }

    return yuv
}

