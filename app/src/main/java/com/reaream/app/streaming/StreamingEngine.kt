package com.reaream.app.streaming

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import com.reaream.app.data.model.StreamConfig
import com.reaream.app.data.model.StreamProtocol
import com.reaream.app.data.model.VideoCodec
import com.reaream.app.streaming.protocol.RtmpConnection
import com.reaream.app.streaming.protocol.SrtConnection
import com.reaream.app.streaming.protocol.StreamConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.reaream.app.data.model.WidgetSettings
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

class StreamingEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state.asStateFlow()

    val widgetRenderer = WidgetRenderer()
    val widgetSettingsRef = AtomicReference(WidgetSettings())

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var connection: StreamConnection? = null
    private var currentConfig: StreamConfig? = null
    private var startTimeNanos: Long = 0L
    private var baseVideoTimestampUs: Long = -1L
    private var baseAudioTimestampUs: Long = -1L
    private var totalBytesSent: Long = 0L
    private var encoderColorFormat: Int = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible

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

        scope.launch {
            try {
                setupAudioEncoder(config)
                connection = createConnection(config)
                withTimeout(10_000L) {
                    connection?.connect()
                }

                startTimeNanos = System.nanoTime()
                baseVideoTimestampUs = -1L
                baseAudioTimestampUs = -1L
                totalBytesSent = 0
                _state.value = _state.value.copy(
                    isStreaming = true,
                    isConnecting = false,
                )
                launchStatsUpdater()
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Connection timed out", e)
                _state.value = _state.value.copy(
                    isStreaming = false,
                    isConnecting = false,
                    error = "接続がタイムアウトしました",
                )
                releaseEncoders()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start streaming", e)
                _state.value = _state.value.copy(
                    isStreaming = false,
                    isConnecting = false,
                    error = e.message?.takeIf { it.isNotBlank() }
                        ?: "接続に失敗しました: ${e.javaClass.simpleName}",
                )
                releaseEncoders()
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun stopStreaming() {
        scope.launch {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting", e)
            }
            connection = null
            releaseEncoders()
            currentConfig = null
            _state.value = StreamState()
        }
    }

    fun onVideoFrame(buffer: ByteBuffer, width: Int, height: Int, presentationTimeUs: Long) {
        if (!_state.value.isStreaming) return

        // Lazy-init video encoder with actual camera resolution
        if (videoEncoder == null) {
            val config = currentConfig ?: return
            setupVideoEncoder(config, width, height)
            Log.i(TAG, "Video encoder initialized: ${width}x${height}")
            _state.value = _state.value.copy(videoWidth = width, videoHeight = height)
        }

        if (baseVideoTimestampUs < 0) baseVideoTimestampUs = presentationTimeUs
        val relativeUs = presentationTimeUs - baseVideoTimestampUs
        try {
            // Apply widget overlay onto the I420 YUV frame
            val i420Bytes = ByteArray(buffer.remaining())
            buffer.get(i420Bytes)
            widgetRenderer.renderOntoFrame(i420Bytes, width, height, widgetSettingsRef.get())

            // Convert I420 to the format the encoder actually expects
            val frameBytes = convertI420ForEncoder(i420Bytes, width, height)

            videoEncoder?.let { encoder ->
                val inputIndex = encoder.dequeueInputBuffer(0)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex) ?: return
                    inputBuffer.clear()
                    val size = minOf(frameBytes.size, inputBuffer.remaining())
                    inputBuffer.put(frameBytes, 0, size)
                    encoder.queueInputBuffer(inputIndex, 0, size, relativeUs, 0)
                }

                val info = MediaCodec.BufferInfo()
                var outputIndex = encoder.dequeueOutputBuffer(info, 0)
                while (outputIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        val data = ByteArray(info.size)
                        outputBuffer.get(data)
                        connection?.sendVideo(data, info.presentationTimeUs, info.flags)
                        totalBytesSent += data.size
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    outputIndex = encoder.dequeueOutputBuffer(info, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding video frame", e)
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

    private fun setupVideoEncoder(config: StreamConfig, width: Int, height: Int) {
        val videoMime = when (config.videoCodec) {
            VideoCodec.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
            VideoCodec.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
        }

        val videoFormat = MediaFormat.createVideoFormat(videoMime, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate * 1000)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
        }

        videoEncoder = MediaCodec.createEncoderByType(videoMime).apply {
            configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()

            // Query actual color format the encoder uses after start()
            val inputFormat = this.inputFormat
            encoderColorFormat = inputFormat.getInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            Log.i(TAG, "Encoder actual color format: $encoderColorFormat (0x${encoderColorFormat.toString(16)})")
        }
    }

    /**
     * Convert I420 frame to the format expected by the hardware encoder.
     * I420: [Y][U][V] (planar)
     * NV12 (COLOR_FormatYUV420SemiPlanar): [Y][UVUV...] (semi-planar, U first)
     * NV21 (COLOR_FormatYUV420PackedSemiPlanar): [Y][VUVU...] (semi-planar, V first)
     */
    @Suppress("DEPRECATION")
    private fun convertI420ForEncoder(i420: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4

        return when (encoderColorFormat) {
            // Semi-planar NV12: Y + interleaved UV
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            0x7F420888 /* COLOR_FormatYUV420Flexible often means NV12 on HW encoders */ -> {
                val nv12 = ByteArray(ySize + ySize / 2)
                // Copy Y plane as-is
                System.arraycopy(i420, 0, nv12, 0, ySize)
                // Interleave U and V
                val uOffset = ySize
                val vOffset = ySize + uvSize
                var nv12Offset = ySize
                for (i in 0 until uvSize) {
                    nv12[nv12Offset++] = i420[uOffset + i]
                    nv12[nv12Offset++] = i420[vOffset + i]
                }
                nv12
            }
            // Planar I420 (COLOR_FormatYUV420Planar) — no conversion needed
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> i420
            // Unknown/flexible — try NV12 as it's the most common HW encoder format
            else -> {
                val nv12 = ByteArray(ySize + ySize / 2)
                System.arraycopy(i420, 0, nv12, 0, ySize)
                val uOffset = ySize
                val vOffset = ySize + uvSize
                var nv12Offset = ySize
                for (i in 0 until uvSize) {
                    nv12[nv12Offset++] = i420[uOffset + i]
                    nv12[nv12Offset++] = i420[vOffset + i]
                }
                nv12
            }
        }
    }

    private fun setupAudioEncoder(config: StreamConfig) {
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            44100,
            1
        ).apply {
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
            StreamProtocol.RIST -> RtmpConnection(url) // Placeholder, RIST needs native lib
        }
    }

    private fun buildStreamUrl(config: StreamConfig): String {
        val base = config.url.trimEnd('/')
        return if (config.streamKey.isNotEmpty()) {
            "$base/${config.streamKey}"
        } else {
            base
        }
    }

    private fun releaseEncoders() {
        try {
            videoEncoder?.stop()
            videoEncoder?.release()
        } catch (_: Exception) {}
        videoEncoder = null

        try {
            audioEncoder?.stop()
            audioEncoder?.release()
        } catch (_: Exception) {}
        audioEncoder = null
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

                val quality = when {
                    bitrateKbps <= 0 -> ConnectionQuality.POOR
                    bitrateKbps < 500 -> ConnectionQuality.FAIR
                    else -> ConnectionQuality.GOOD
                }

                _state.value = _state.value.copy(
                    bitrateKbps = bitrateKbps,
                    uptime = elapsed,
                    connectionQuality = quality,
                )
            }
        }
    }

    fun release() {
        stopStreaming()
        widgetRenderer.release()
        scope.cancel()
    }

    companion object {
        private const val TAG = "StreamingEngine"
    }
}
