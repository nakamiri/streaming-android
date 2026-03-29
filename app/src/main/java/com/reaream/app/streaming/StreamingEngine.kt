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
import com.reaream.app.data.model.startValidationError
import com.reaream.app.streaming.gl.GlStreamPipeline
import com.reaream.app.streaming.protocol.RtmpConnection
import com.reaream.app.streaming.protocol.SrtConnection
import com.reaream.app.streaming.protocol.StreamConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class StreamingEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val shutdownMutex = Mutex()

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
    private var videoOutputJob: Job? = null
    private var statsJob: Job? = null

    private var startTimeNanos: Long = 0L
    private var baseAudioTimestampUs: Long = -1L
    private val totalBytesSent = AtomicLong(0L)

    private var poorQualityStreak: Int = 0
    private var goodQualityStreak: Int = 0
    private var configuredBitrateKbps: Int = 0
    private var currentAdaptiveBitrateKbps: Int = 0

    private val videoFramesSentThisSecond = AtomicInteger(0)
    private val videoFramesDroppedThisSecond = AtomicInteger(0)
    private val sessionVersion = AtomicInteger(0)

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

        config.startValidationError()?.let {
            showError(it)
            return
        }

        val session = sessionVersion.incrementAndGet()
        currentConfig = config
        configuredBitrateKbps = config.videoBitrate
        currentAdaptiveBitrateKbps = 0
        poorQualityStreak = 0
        goodQualityStreak = 0
        totalBytesSent.set(0L)
        baseAudioTimestampUs = -1L
        videoFramesSentThisSecond.set(0)
        videoFramesDroppedThisSecond.set(0)
        glPipeline.framesRendered.set(0)
        glPipeline.framesDropped.set(0)
        glPipeline.onFirstFrameReady = { rotationDegrees ->
            if (isSessionActive(session)) {
                scope.launch { setupVideoEncoderForSurface(session, config, rotationDegrees) }
            }
        }

        _state.value = StreamState(isConnecting = true)

        scope.launch {
            try {
                setupAudioEncoder(config)
                val createdConnection = createConnection(config)
                connection = createdConnection
                withTimeout(10_000L) { createdConnection.connect() }

                if (!isSessionActive(session)) {
                    try {
                        createdConnection.disconnect()
                    } catch (_: Exception) {
                    }
                    return@launch
                }

                startTimeNanos = System.nanoTime()
                baseAudioTimestampUs = -1L
                totalBytesSent.set(0L)
                _state.value = _state.value.copy(isStreaming = true, isConnecting = false, error = null)

                val currentState = _state.value
                if (currentState.videoWidth > 0 && currentState.videoHeight > 0) {
                    createdConnection.updateVideoParameters(
                        currentState.videoWidth,
                        currentState.videoHeight,
                        config.videoCodec,
                    )
                }

                ensureVideoOutputLoop(session)
                launchStatsUpdater(session)
            } catch (e: TimeoutCancellationException) {
                handleStartFailure(session, "接続がタイムアウトしました", e)
            } catch (e: Exception) {
                handleStartFailure(
                    session,
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "接続に失敗しました: ${e.javaClass.simpleName}",
                    e,
                )
            }
        }
    }

    fun showError(message: String) {
        _state.value = if (_state.value.isStreaming || _state.value.isConnecting) {
            _state.value.copy(isConnecting = false, error = message)
        } else {
            StreamState(error = message)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun stopStreaming() {
        if (!_state.value.isStreaming && !_state.value.isConnecting && currentConfig == null) return
        scope.launch { shutdownStreaming(null) }
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
                        val data = copyOutputBuffer(outputBuffer, info)
                        if (connection?.sendAudio(data, info.presentationTimeUs, info.flags) == true) {
                            totalBytesSent.addAndGet(data.size.toLong())
                        }
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(info, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding audio", e)
            if (_state.value.isStreaming) {
                failStreaming(sessionVersion.get(), "音声送信中にエラーが発生しました", e)
            }
        }
    }

    fun release() {
        runBlocking {
            shutdownStreaming(null)
        }
        glPipeline.release()
        widgetRenderer.release()
        scope.cancel()
    }

    private fun isSessionActive(session: Int): Boolean = sessionVersion.get() == session

    private fun handleStartFailure(session: Int, message: String, cause: Throwable? = null) {
        if (!isSessionActive(session)) return
        if (cause != null) {
            Log.e(TAG, message, cause)
        } else {
            Log.e(TAG, message)
        }
        scope.launch { shutdownStreaming(message) }
    }

    private fun failStreaming(session: Int, message: String, cause: Throwable? = null) {
        if (!isSessionActive(session)) return
        if (cause != null) {
            Log.e(TAG, message, cause)
        } else {
            Log.e(TAG, message)
        }
        scope.launch { shutdownStreaming(message) }
    }

    private suspend fun setupVideoEncoderForSurface(
        session: Int,
        config: StreamConfig,
        rotationDegrees: Int,
    ) {
        if (!isSessionActive(session) || videoEncoder != null) return

        val isPortrait = rotationDegrees == 90 || rotationDegrees == 270
        val encW = if (isPortrait) {
            minOf(config.resolution.width, config.resolution.height)
        } else {
            maxOf(config.resolution.width, config.resolution.height)
        }
        val encH = if (isPortrait) {
            maxOf(config.resolution.width, config.resolution.height)
        } else {
            minOf(config.resolution.width, config.resolution.height)
        }

        val videoMime = when (config.videoCodec) {
            VideoCodec.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
            VideoCodec.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
        }

        val videoFormat = MediaFormat.createVideoFormat(videoMime, encW, encH).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate * 1000)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(
                MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
            )
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
            setInteger(MediaFormat.KEY_OPERATING_RATE, maxOf(config.fps, 30))
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }

        try {
            val encoder = MediaCodec.createEncoderByType(videoMime)
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encSurface = encoder.createInputSurface()
            encoder.start()

            if (!isSessionActive(session)) {
                try {
                    encoder.stop()
                } catch (_: Exception) {
                }
                try {
                    encoder.release()
                } catch (_: Exception) {
                }
                try {
                    encSurface.release()
                } catch (_: Exception) {
                }
                return
            }

            videoEncoder = encoder
            videoEncoderSurface = encSurface
            glPipeline.attachEncoderSurfaceSync(encSurface, encW, encH)
            connection?.updateVideoParameters(encW, encH, config.videoCodec)
            _state.value = _state.value.copy(videoWidth = encW, videoHeight = encH)

            Log.i(TAG, "Video encoder started (Surface input): ${encW}x${encH}, ${config.videoBitrate}kbps")

            if (_state.value.isStreaming) {
                ensureVideoOutputLoop(session)
            }
        } catch (e: Exception) {
            if (_state.value.isConnecting) {
                handleStartFailure(session, "映像エンコーダの初期化に失敗しました", e)
            } else {
                failStreaming(session, "映像エンコーダの初期化に失敗しました", e)
            }
        }
    }

    private fun ensureVideoOutputLoop(session: Int) {
        if (!isSessionActive(session) || !_state.value.isStreaming || videoEncoder == null) return
        if (videoOutputJob?.isActive == true) return

        videoOutputJob = scope.launch {
            val info = MediaCodec.BufferInfo()
            while (isSessionActive(session) && _state.value.isStreaming) {
                val encoder = videoEncoder ?: break
                try {
                    when (val outputIndex = encoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            sendVideoCodecConfigFromFormat(encoder.outputFormat)
                        }
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = encoder.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && info.size > 0) {
                                val data = copyOutputBuffer(outputBuffer, info)
                                val isCodecConfig =
                                    info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                                val sent = connection?.sendVideo(data, info.presentationTimeUs, info.flags) == true
                                if (!isCodecConfig) {
                                    if (sent) {
                                        totalBytesSent.addAndGet(data.size.toLong())
                                        videoFramesSentThisSecond.incrementAndGet()
                                    } else {
                                        videoFramesDroppedThisSecond.incrementAndGet()
                                    }
                                }
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isSessionActive(session) && _state.value.isStreaming) {
                        failStreaming(session, "映像送信中にエラーが発生しました", e)
                    }
                    break
                }
            }
        }
    }

    private fun sendVideoCodecConfigFromFormat(format: MediaFormat) {
        val codecConfig = buildCodecConfigBuffer(format) ?: run {
            Log.w(TAG, "Encoder output format did not expose codec config buffers")
            return
        }
        if (connection?.sendVideo(codecConfig, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != true) {
            Log.w(TAG, "Failed to send codec config from output format")
        }
    }

    private fun buildCodecConfigBuffer(format: MediaFormat): ByteArray? {
        val csdBuffers = listOfNotNull(
            format.getByteBuffer("csd-0")?.duplicate(),
            format.getByteBuffer("csd-1")?.duplicate(),
            format.getByteBuffer("csd-2")?.duplicate(),
        )
        if (csdBuffers.isEmpty()) return null

        val totalSize = csdBuffers.sumOf { 4 + it.remaining() }
        val out = ByteArray(totalSize)
        var offset = 0
        for (buffer in csdBuffers) {
            out[offset++] = 0
            out[offset++] = 0
            out[offset++] = 0
            out[offset++] = 1
            val size = buffer.remaining()
            buffer.get(out, offset, size)
            offset += size
        }
        return out
    }

    private fun copyOutputBuffer(buffer: ByteBuffer, info: MediaCodec.BufferInfo): ByteArray {
        val dup = buffer.duplicate()
        dup.position(info.offset)
        dup.limit(info.offset + info.size)
        return ByteArray(info.size).also { dup.get(it) }
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
            StreamProtocol.RIST -> throw UnsupportedOperationException("RIST 配信はまだ実装されていません。")
        }
    }

    private fun buildStreamUrl(config: StreamConfig): String {
        val base = config.url.trimEnd('/')
        return if (config.streamKey.isNotEmpty()) "$base/${config.streamKey}" else base
    }

    private fun releaseEncoders() {
        glPipeline.detachEncoderSurfaceSync()

        try {
            videoEncoder?.stop()
        } catch (_: Exception) {
        }
        try {
            videoEncoder?.release()
        } catch (_: Exception) {
        }
        videoEncoder = null

        try {
            videoEncoderSurface?.release()
        } catch (_: Exception) {
        }
        videoEncoderSurface = null

        try {
            audioEncoder?.stop()
        } catch (_: Exception) {
        }
        try {
            audioEncoder?.release()
        } catch (_: Exception) {
        }
        audioEncoder = null

        baseAudioTimestampUs = -1L
        poorQualityStreak = 0
        goodQualityStreak = 0
        configuredBitrateKbps = 0
        currentAdaptiveBitrateKbps = 0
        videoFramesSentThisSecond.set(0)
        videoFramesDroppedThisSecond.set(0)
        glPipeline.framesRendered.set(0)
        glPipeline.framesDropped.set(0)
    }

    private fun launchStatsUpdater(session: Int) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var lastBytes = 0L
            var secondsWithoutVideo = 0

            while (isSessionActive(session) && _state.value.isStreaming) {
                delay(1000)

                if (!isSessionActive(session) || !_state.value.isStreaming) break

                val currentConnection = connection
                if (currentConnection != null && !currentConnection.isConnected) {
                    failStreaming(session, "接続が切断されました")
                    break
                }

                secondsWithoutVideo = if (videoEncoder == null) secondsWithoutVideo + 1 else 0
                if (secondsWithoutVideo >= 5) {
                    failStreaming(session, "カメラ映像の開始に失敗しました")
                    break
                }

                val elapsed = (System.nanoTime() - startTimeNanos) / 1_000_000_000L
                val bytesNow = totalBytesSent.get()
                val bytesDelta = bytesNow - lastBytes
                lastBytes = bytesNow
                val bitrateKbps = ((bytesDelta * 8) / 1000).toInt()
                val fps = videoFramesSentThisSecond.getAndSet(0)
                val dropped = videoFramesDroppedThisSecond.getAndSet(0) +
                    glPipeline.framesDropped.getAndSet(0)

                val ratio = if (configuredBitrateKbps > 0) {
                    bitrateKbps.toFloat() / configuredBitrateKbps
                } else {
                    1f
                }
                val quality = when {
                    currentConnection == null || !currentConnection.isConnected -> ConnectionQuality.POOR
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

                val config = currentConfig
                if (config != null && config.adaptiveBitrate && configuredBitrateKbps > 0 && videoEncoder != null) {
                    when (quality) {
                        ConnectionQuality.POOR -> {
                            poorQualityStreak++
                            goodQualityStreak = 0
                        }
                        ConnectionQuality.GOOD -> {
                            goodQualityStreak++
                            poorQualityStreak = 0
                        }
                        else -> {
                            poorQualityStreak = 0
                            goodQualityStreak = 0
                        }
                    }

                    val floor = (configuredBitrateKbps * 0.4f).toInt()
                    if (poorQualityStreak >= 3) {
                        poorQualityStreak = 0
                        val current = if (currentAdaptiveBitrateKbps > 0) {
                            currentAdaptiveBitrateKbps
                        } else {
                            configuredBitrateKbps
                        }
                        val reduced = maxOf((current * 0.75f).toInt(), floor)
                        if (reduced < current) {
                            applyAdaptiveBitrate(reduced)
                            currentAdaptiveBitrateKbps = reduced
                            _state.value = _state.value.copy(adaptiveBitrateKbps = reduced)
                        }
                    }

                    if (goodQualityStreak >= 10 && currentAdaptiveBitrateKbps > 0) {
                        goodQualityStreak = 0
                        val restored = minOf(
                            configuredBitrateKbps,
                            (currentAdaptiveBitrateKbps * 1.5f).toInt(),
                        )
                        currentAdaptiveBitrateKbps = if (restored >= configuredBitrateKbps) 0 else restored
                        val target = if (currentAdaptiveBitrateKbps == 0) {
                            configuredBitrateKbps
                        } else {
                            restored
                        }
                        applyAdaptiveBitrate(target)
                        _state.value = _state.value.copy(
                            adaptiveBitrateKbps = currentAdaptiveBitrateKbps,
                        )
                    }
                }
            }
        }
    }

    private fun applyAdaptiveBitrate(targetKbps: Int) {
        try {
            videoEncoder?.setParameters(Bundle().apply {
                putInt(MediaFormat.KEY_BIT_RATE, targetKbps * 1000)
            })
            Log.i(TAG, "Adaptive: set bitrate to ${targetKbps}kbps")
        } catch (e: Exception) {
            Log.w(TAG, "Adaptive bitrate update failed", e)
        }
    }

    private suspend fun shutdownStreaming(error: String?) {
        shutdownMutex.withLock {
            sessionVersion.incrementAndGet()

            val outputJob = videoOutputJob
            videoOutputJob = null
            if (outputJob != null) {
                outputJob.cancelAndJoin()
            }

            val updaterJob = statsJob
            statsJob = null
            if (updaterJob != null) {
                updaterJob.cancelAndJoin()
            }

            val currentConnection = connection
            connection = null
            try {
                currentConnection?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting stream connection", e)
            }

            releaseEncoders()
            currentConfig = null
            glPipeline.onFirstFrameReady = null
            _state.value = StreamState(error = error)
        }
    }

    companion object {
        private const val TAG = "StreamingEngine"
    }
}
