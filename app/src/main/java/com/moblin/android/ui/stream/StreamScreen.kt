package com.moblin.android.ui.stream

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.moblin.android.data.model.AppSettings
import com.moblin.android.streaming.StreamingEngine
import com.moblin.android.chat.ChatMessage

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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        CameraPreview(
            useFrontCamera = settings.camera.useFrontCamera,
            torchEnabled = torchEnabled,
            modifier = Modifier.fillMaxSize(),
        )

        // Stream info overlay
        if (settings.display.showStreamInfo && streamState.isStreaming) {
            StreamInfoOverlay(
                streamState = streamState,
                settings = settings,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding(),
            )
        }

        // Chat overlay
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

        // Control bar
        ControlBar(
            isStreaming = streamState.isStreaming,
            isConnecting = streamState.isConnecting,
            isMuted = settings.audio.muted,
            torchEnabled = torchEnabled,
            onToggleStreaming = onToggleStreaming,
            onToggleMute = onToggleMute,
            onToggleTorch = onToggleTorch,
            onSwitchCamera = onSwitchCamera,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding(),
        )
    }
}

@Composable
fun CameraPreview(
    useFrontCamera: Boolean,
    torchEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraSelector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                    )
                    camera.cameraControl.enableTorch(torchEnabled)
                } catch (e: Exception) {
                    // Camera binding failed
                }
            }, ContextCompat.getMainExecutor(context))
        },
        modifier = modifier,
    )
}
