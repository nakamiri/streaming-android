package com.reaream.app.streaming

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.reaream.app.data.model.StreamConfig
import com.reaream.app.data.model.StreamProtocol
import com.reaream.app.data.model.VideoCodec
import com.reaream.app.data.model.WidgetSettings
import com.reaream.app.streaming.gl.GlStreamPipeline
import com.reaream.app.streaming.protocol.RtmpConnection
import com.reaream.app.streaming.protocol.SrtConnection
import com.reaream.app.streaming.protocol.StreamConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class StreamingEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state.asStateFlow()

    val widgetRenderer = WidgetRenderer()
    val widgetSettingsRef = AtomicReference(WidgetSettings())

    /** GL pipeline — created at construction, alive for the app lifetime. */
    val glPipeline = GlStreamPipeline(widgetRenderer, widgetSettingsRef)

    @Volatile private var videoEncoder: MediaCodec? = null
    @Volatile private var videoEncoderSurface: Surface? = null
    private var audioEncoder: MediaCodec? = null
    private var connection: StreamConnection? = null
    private var currentConfig: StreamConfig? = null
    private var startTimeNanos: Long = 0L
    private var baseAudioTimestampUs: Long = -1L
    private var totalBytesSent: Long = 0L

    // Adaptive bitrate
    private var poorQualityStreak: Int = 0
    private var goodQualityStreak: Int = 0
    private var configuredBitrateKbps: Int = 0
    private var currentAdaptiveBitrateKbps: Int = 0

    // Per-second frame counters
    private val framesSubmittedThisSecond = AtomicInteger(0) // frames dequeued from encoder output

    // Signals when the video encoder surface has been set up (lazy — waits for first camera frame)
    private var videoEncoderReadySignal = CompletableDeferred<Unit>()

    data class StreamState(
        val isStreaming: Boolean = false,
        val isConnecting: Boolean = false,
        val bitrateKbps: Int = 0,
        val fps: Int = 0,
        val uptime: Long = 0L,
        val error: String? = null,
        val connectionQuality: ConnectionQuality = ConnectionQuality.UNKNOWN,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val adaptiveBitrateKbps: Int = 0,
        val droppedFramesPerSec: Int = 0,
    )

    enum class ConnectionQuality { UNKNOWN, GOOD, FAIR, POOR }

    fun startStreaming(config: StreamConfig) {
        if (_state.value.isStreaming || _state.value.isConnecting) return

        if (config.url.isBlank()) {
            _state.value = _state.value.copy(
                error = "配信URLが設定されていません。設定画面でURLを入力してください。",
            )
            return
        }

        _state.value = _state.value.copy(isConnecting = true, error = null)
        currentConfig = config
        videoEncoderReadySignal = CompletableDeferred()

        scope.launch {
            try {
                // Wire the GL pipeline to call back when the first camera frame arrives,
                // so we can lazy-init the video encoder with the correct orientation.
                glPipeline.onFirstFrameReady = { rotationDegrees ->
                    scope.launch { setupVideoEncoderForSurface(config, rotationDegrees) }
                }

                setupAudioEncoder(config)
                connection = createConnection(config)
                withTimeout(10_000L) { connection?.connect() }

                startTimeNanos = System.nanoTime()
                baseAudioTimestampUs = -1L
                totalBytesSent = 0
                _state.value = _state.value.copy(isStreaming = true, isConnecting = false)
                launchStatsUpdater()

                // Start the video output loop only after isStreaming = true.
                // The encoder may have been set up while connecting (first frame arrived early)
                // or may still be pending — wait for the signal either way.
                scope.launch {
                    try {
                        withTimeout(15_000L) { videoEncoderReadySignal.await() }
                        if (_state.value.isStreaming) launchVideoOutputLoop()
                    } catch (_: Exception) {
                        Log.d(TAG, "Video encoder ready signal cancelled or timed out")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Connection timed out", e)
                _state.value = _state.value.copy(
                    isStreaming = false, isConnecting = false,
                    error = "接続がタイムアウトしました",
                )
                releaseEncoders()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start streaming", e)
                _state.value = _state.value.copy(
                    isStreaming = false, isConnecting = false,
                    error = e.message?.takeIf { it.isNotBlank() }
                        ?: "接続に失敗しました: ${e.javaClass.simpleName}",
                )
                releaseEncoders()
            }
        }
    }

    private fun setupVideoEncoderForSurface(config: StreamConfig, rotationDegrees: Int) {
        if (videoEncoder != null) return // already set up
        if (!_state.value.isConnecting && !_state.value.isStreaming) return // streaming cancelled
        val isPortrait = (rotationDegrees == 90 || rotationDegrees == 270)
        val encW = if (isPortrait) minOf(config.resolution.width, config.resolution.height)
                   else maxOf(config.resolution.width, config.resolution.height)
        val encH = if (isPortrait) maxOf(config.resolution.width, config.resolution.height)
                   else minOf(config.resolution.width, config.resolution.height)

        val videoMime = when (config.videoCodec) {
            VideoCodec.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
            VideoCodec.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
        }
        val videoFormat = MediaFormat.createVideoFormat(videoMime, encW, encH).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate * 1000)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }

        try {
            val encoder = MediaCodec.createEncoderByType(videoMime)
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encSurface = encoder.createInputSurface()
            encoder.start()
            videoEncoder = encoder
            videoEncoderSurface = encSurface

            configuredBitrateKbps = config.videoBitrate
            currentAdaptiveBitrateKbps = 0
            glPipeline.attachEncoderSurface(encSurface, encW, encH)

            _state.value = _state.value.copy(videoWidth = encW, videoHeight = encH)
            Log.i(TAG, "Video encoder started (Surface input): ${encW}x${encH}, ${config.videoBitrate}kbps")

            // Signal that the encoder is ready. The output loop will be started by startStreaming()
            // after isStreaming = true, to guarantee the loop condition is met on first check.
            videoEncoderReadySignal.complete(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup video encoder", e)
        }
    }

    private fun launchVideoOutputLoop() {
        scope.launch {
            val info = MediaCodec.BufferInfo()
            while (_state.value.isStreaming) {
                val enc = videoEncoder ?: break
                try {
                    val outputIndex = enc.dequeueOutputBuffer(info, 10_000)
                    when {
                        outputIndex >= 0 -> {
                            val buffer = enc.getOutputBuffer(outputIndex)
                            if (buffer != null && info.size > 0) {
                                val data = ByteArray(info.size)
                                buffer.get(data)
                                connection?.sendVideo(data, info.presentationTimeUs, info.flags)
                                totalBytesSent += data.size
                            }
                            enc.releaseOutputBuffer(outputIndex, false)
                            framesSubmittedThisSecond.incrementAndGet()
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            Log.d(TAG, "Encoder output format changed")
                        }
                    }
                } catch (e: Exception) {
                    if (_state.value.isStreaming) Log.e(TAG, "Video output error", e)
                    break
                }
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun stopStreaming() {
        scope.launch {
            videoEncoderReadySignal.cancel()
            try { connection?.disconnect() } catch (e: Exception) { Log.e(TAG, "Disconnect error", e) }
            connection = null
            releaseEncoders()
            currentConfig = null
            _state.value = StreamState()
        }
    }

    fun onAudioData(buffer: ByteArray, presentationTimeUs: Long) {
        if (!_state.value.isStreaming) return
        if (baseAudioTimestampUs < 0) baseAudioTimestampUs = presentationTimeUs
        val relativeUs = presentationTimeUs - baseAudioTimestampUs
        try {
            audioEncoder?.let { encoder ->
                val inputIndex = encoder.dequeueInputBuffer(0)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex) ?: return
                    inputBuffer.clear()
                    val size = minOf(buffer.size, inputBuffer.remaining())
                    inputBuffer.put(buffer, 0, size)
                    encoder.queueInputBuffer(inputIndex, 0, size, relativeUs, 0)
                }
                val info = MediaCodec.BufferInfo()
                var outputIndex = encoder.dequeueOutputBuffer(info, 0)
                while (outputIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        val data = ByteArray(info.size)
                        outputBuffer.get(data)
                        connection?.sendAudio(data, info.presentationTimeUs, info.flags)
                        totalBytesSent += data.size
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(info, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding audio", e)
        }
    }

    private fun setupAudioEncoder(config: StreamConfig) {
        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate * 1000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    private fun createConnection(config: StreamConfig): StreamConnection {
        val url = buildStreamUrl(config)
        return when (config.protocol) {
            StreamProtocol.RTMP, StreamProtocol.RTMPS -> RtmpConnection(
                url = url,
                videoWidth = config.resolution.width,
                videoHeight = config.resolution.height,
                sampleRate = 44100,
            )
            StreamProtocol.SRT -> SrtConnection(url, config.srtLatency)
            StreamProtocol.RIST -> RtmpConnection(url)
        }
    }

    private fun buildStreamUrl(config: StreamConfig): String {
        val base = config.url.trimEnd('/')
        return if (config.streamKey.isNotEmpty()) "$base/${config.streamKey}" else base
    }

    private fun releaseEncoders() {
        videoEncoderReadySignal.cancel()
        // Synchronously detach the encoder EGL surface on the GL thread before stopping the codec.
        // Without this, the GL thread may be mid-swapBuffers when we call stop(), causing a crash.
        glPipeline.detachEncoderSurfaceSync()

        try { videoEncoder?.stop(); videoEncoder?.release() } catch (_: Exception) {}
        videoEncoder = null
        videoEncoderSurface?.release()
        videoEncoderSurface = null

        try { audioEncoder?.stop(); audioEncoder?.release() } catch (_: Exception) {}
        audioEncoder = null

        baseAudioTimestampUs = -1L
        poorQualityStreak = 0
        goodQualityStreak = 0
        currentAdaptiveBitrateKbps = 0
        framesSubmittedThisSecond.set(0)
    }

    private fun launchStatsUpdater() {
        scope.launch {
            var lastBytes = 0L
            while (_state.value.isStreaming) {
                delay(1000)
                val elapsed = (System.nanoTime() - startTimeNanos) / 1_000_000_000L
                val bytesDelta = totalBytesSent - lastBytes
                lastBytes = totalBytesSent
                val bitrateKbps = ((bytesDelta * 8) / 1000).toInt()

                // framesRendered = frames submitted to encoder (GL swaps); framesSubmittedThisSecond = frames dequeued from encoder output
                val glFrames = glPipeline.framesRendered.getAndSet(0)
                val fps = framesSubmittedThisSecond.getAndSet(0)
                val dropped = maxOf(0, glFrames - fps)

                val ratio = if (configuredBitrateKbps > 0) bitrateKbps.toFloat() / configuredBitrateKbps else 1f
                val quality = when {
                    bitrateKbps <= 0 || ratio < 0.4f -> ConnectionQuality.POOR
                    ratio < 0.75f -> ConnectionQuality.FAIR
                    else -> ConnectionQuality.GOOD
                }

                _state.value = _state.value.copy(
                    bitrateKbps = bitrateKbps,
                    fps = fps,
                    droppedFramesPerSec = dropped,
                    uptime = elapsed,
                    connectionQuality = quality,
                )

                // Adaptive bitrate
                val config = currentConfig
                if (config != null && config.adaptiveBitrate && configuredBitrateKbps > 0) {
                    when (quality) {
                        ConnectionQuality.POOR -> { poorQualityStreak++; goodQualityStreak = 0 }
                        ConnectionQuality.GOOD -> { goodQualityStreak++; poorQualityStreak = 0 }
                        else -> { poorQualityStreak = 0; goodQualityStreak = 0 }
                    }
                    val floor = (configuredBitrateKbps * 0.4).toInt()
                    if (poorQualityStreak >= 3) {
                        poorQualityStreak = 0
                        val current = if (currentAdaptiveBitrateKbps > 0) currentAdaptiveBitrateKbps else configuredBitrateKbps
                        val reduced = maxOf((current * 0.75).toInt(), floor)
                        if (reduced < current) {
                            currentAdaptiveBitrateKbps = reduced
                            Log.i(TAG, "Adaptive: reducing bitrate to ${reduced}kbps")
                            videoEncoder?.setParameters(Bundle().apply {
                                putInt(MediaFormat.KEY_BIT_RATE, reduced * 1000)
                            })
                            _state.value = _state.value.copy(adaptiveBitrateKbps = reduced)
                        }
                    }
                    if (goodQualityStreak >= 10 && currentAdaptiveBitrateKbps > 0) {
                        goodQualityStreak = 0
                        val restored = minOf(configuredBitrateKbps, (currentAdaptiveBitrateKbps * 1.5).toInt())
                        currentAdaptiveBitrateKbps = if (restored >= configuredBitrateKbps) 0 else restored
                        val target = if (currentAdaptiveBitrateKbps == 0) configuredBitrateKbps else restored
                        Log.i(TAG, "Adaptive: restoring bitrate to ${target}kbps")
                        videoEncoder?.setParameters(Bundle().apply {
                            putInt(MediaFormat.KEY_BIT_RATE, target * 1000)
                        })
                        _state.value = _state.value.copy(adaptiveBitrateKbps = currentAdaptiveBitrateKbps)
                    }
                }
            }
        }
    }

    fun release() {
        stopStreaming()
        widgetRenderer.release()
        glPipeline.release()
        scope.cancel()
    }

    companion object {
        private const val TAG = "StreamingEngine"
    }
}
