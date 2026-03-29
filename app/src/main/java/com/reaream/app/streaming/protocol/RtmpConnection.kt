package com.reaream.app.streaming.protocol

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.rtmp.rtmp.RtmpClient
import com.reaream.app.data.model.VideoCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * RTMP connection using RootEncoder's RtmpClient.
 * Handles the full RTMP protocol: handshake, connect, createStream, publish.
 */
class RtmpConnection(
    private val url: String,
    private var videoWidth: Int = 1920,
    private var videoHeight: Int = 1080,
    private val sampleRate: Int = 44100,
) : StreamConnection {

    private var rtmpClient: RtmpClient? = null
    private var videoInfoSent = false

    @Volatile
    override var isConnected: Boolean = false
        private set

    override suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val client = RtmpClient(object : ConnectChecker {
                override fun onConnectionStarted(url: String) {
                    Log.d(TAG, "Connection started: $url")
                }

                override fun onConnectionSuccess() {
                    Log.i(TAG, "RTMP connected successfully")
                    isConnected = true
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onConnectionFailed(reason: String) {
                    Log.e(TAG, "RTMP connection failed: $reason")
                    isConnected = false
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IOException("RTMP connection failed: $reason")
                        )
                    }
                }

                override fun onDisconnect() {
                    Log.i(TAG, "RTMP disconnected")
                    isConnected = false
                }

                override fun onAuthError() {
                    Log.e(TAG, "RTMP auth error")
                    isConnected = false
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IOException("RTMP authentication failed")
                        )
                    }
                }

                override fun onAuthSuccess() {
                    Log.d(TAG, "RTMP auth success")
                }

                override fun onNewBitrate(bitrate: Long) {}
            })

            client.setVideoResolution(videoWidth, videoHeight)
            client.setAudioInfo(sampleRate, isStereo = false)

            rtmpClient = client
            videoInfoSent = false
            client.connect(url)

            continuation.invokeOnCancellation {
                client.disconnect()
            }
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        isConnected = false
        try {
            rtmpClient?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting RTMP", e)
        }
        rtmpClient = null
        videoInfoSent = false
    }

    override fun updateVideoParameters(width: Int, height: Int, codec: VideoCodec) {
        videoWidth = width
        videoHeight = height
        rtmpClient?.setVideoResolution(width, height)
    }

    override fun sendVideo(data: ByteArray, timestampUs: Long, flags: Int): Boolean {
        if (!isConnected) return false
        try {
            // Extract SPS/PPS from codec config and call setVideoInfo
            if (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                val spsPps = parseSpsPps(data)
                if (spsPps != null) {
                    Log.i(TAG, "Setting video info: SPS=${spsPps.first.remaining()} PPS=${spsPps.second.remaining()}")
                    rtmpClient?.setVideoInfo(spsPps.first, spsPps.second, null)
                    videoInfoSent = true
                } else {
                    Log.w(TAG, "Could not parse SPS/PPS from codec config")
                    return false
                }
                return true // Don't send codec config as a regular frame
            }

            if (!videoInfoSent) return false // Can't send video without SPS/PPS

            val buffer = ByteBuffer.wrap(data)
            val info = MediaCodec.BufferInfo().apply {
                set(0, data.size, timestampUs, flags)
            }
            rtmpClient?.sendVideo(buffer, info)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending video", e)
            isConnected = false
            return false
        }
    }

    override fun sendAudio(data: ByteArray, timestampUs: Long, flags: Int): Boolean {
        if (!isConnected) return false
        try {
            val buffer = ByteBuffer.wrap(data)
            val info = MediaCodec.BufferInfo().apply {
                set(0, data.size, timestampUs, flags)
            }
            rtmpClient?.sendAudio(buffer, info)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio", e)
            isConnected = false
            return false
        }
    }

    /**
     * Parse H.264 codec config data to extract SPS and PPS NAL units.
     * Format: [00 00 00 01 SPS ... 00 00 00 01 PPS ...]
     */
    internal fun parseSpsPps(data: ByteArray): Pair<ByteBuffer, ByteBuffer>? {
        val nalUnits = mutableListOf<ByteArray>()
        var i = 0
        while (i < data.size) {
            // Find start code (00 00 00 01 or 00 00 01)
            val startCodeLen = when {
                i + 3 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                        data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> 4
                i + 2 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                        data[i + 2] == 1.toByte() -> 3
                else -> {
                    i++
                    continue
                }
            }

            val nalStart = i + startCodeLen
            // Find next start code or end
            var nalEnd = data.size
            for (j in nalStart until data.size - 2) {
                if (data[j] == 0.toByte() && data[j + 1] == 0.toByte() &&
                    (data[j + 2] == 1.toByte() || (j + 3 < data.size && data[j + 2] == 0.toByte() && data[j + 3] == 1.toByte()))
                ) {
                    nalEnd = j
                    break
                }
            }

            nalUnits.add(data.copyOfRange(nalStart, nalEnd))
            i = nalEnd
        }

        var sps: ByteArray? = null
        var pps: ByteArray? = null
        for (nal in nalUnits) {
            if (nal.isEmpty()) continue
            val nalType = nal[0].toInt() and 0x1F
            when (nalType) {
                7 -> sps = nal  // SPS
                8 -> pps = nal  // PPS
            }
        }

        return if (sps != null && pps != null) {
            Pair(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps))
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "RtmpConnection"
    }
}
