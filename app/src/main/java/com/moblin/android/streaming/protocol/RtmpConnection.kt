package com.moblin.android.streaming.protocol

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.URI
import javax.net.ssl.SSLSocketFactory

/**
 * RTMP connection implementation.
 * Handles the RTMP handshake and sends audio/video data over the RTMP protocol.
 */
class RtmpConnection(private val url: String) : StreamConnection {

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: InputStream? = null

    @Volatile
    override var isConnected: Boolean = false
        private set

    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            val host = uri.host ?: throw IOException("Invalid host in URL: $url")
            val isSecure = uri.scheme?.lowercase() == "rtmps"
            val port = if (uri.port > 0) uri.port else if (isSecure) 443 else 1935

            socket = if (isSecure) {
                SSLSocketFactory.getDefault().createSocket(host, port)
            } else {
                Socket(host, port)
            }

            socket?.let { s ->
                s.tcpNoDelay = true
                s.soTimeout = 10_000
                outputStream = DataOutputStream(s.getOutputStream())
                inputStream = s.getInputStream()
            }

            performHandshake()
            isConnected = true
            Log.i(TAG, "RTMP connected to $host:$port")
        } catch (e: Exception) {
            isConnected = false
            throw IOException("RTMP connection failed: ${e.message}", e)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        isConnected = false
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing RTMP connection", e)
        }
        outputStream = null
        inputStream = null
        socket = null
        Log.i(TAG, "RTMP disconnected")
    }

    override fun sendVideo(data: ByteArray, timestampUs: Long) {
        if (!isConnected) return
        try {
            val timestampMs = (timestampUs / 1000).toInt()
            val chunkHeader = RtmpChunkHeader.create(
                chunkStreamId = 6,
                timestamp = timestampMs,
                messageLength = data.size,
                messageTypeId = 0x09, // Video
                messageStreamId = 1,
            )
            synchronized(this) {
                outputStream?.write(chunkHeader)
                outputStream?.write(data)
                outputStream?.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error sending video", e)
            isConnected = false
        }
    }

    override fun sendAudio(data: ByteArray, timestampUs: Long) {
        if (!isConnected) return
        try {
            val timestampMs = (timestampUs / 1000).toInt()
            val chunkHeader = RtmpChunkHeader.create(
                chunkStreamId = 4,
                timestamp = timestampMs,
                messageLength = data.size,
                messageTypeId = 0x08, // Audio
                messageStreamId = 1,
            )
            synchronized(this) {
                outputStream?.write(chunkHeader)
                outputStream?.write(data)
                outputStream?.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error sending audio", e)
            isConnected = false
        }
    }

    private fun performHandshake() {
        val out = outputStream ?: throw IOException("No output stream")
        val inp = inputStream ?: throw IOException("No input stream")

        // C0: RTMP version
        out.writeByte(0x03)

        // C1: timestamp(4) + zero(4) + random(1528)
        val c1 = ByteArray(1536)
        val timestamp = (System.currentTimeMillis() / 1000).toInt()
        c1[0] = (timestamp shr 24).toByte()
        c1[1] = (timestamp shr 16).toByte()
        c1[2] = (timestamp shr 8).toByte()
        c1[3] = timestamp.toByte()
        // bytes 4-7 are zero
        for (i in 8 until 1536) {
            c1[i] = (Math.random() * 256).toInt().toByte()
        }
        out.write(c1)
        out.flush()

        // S0
        val s0 = inp.read()
        if (s0 != 0x03) {
            Log.w(TAG, "Unexpected RTMP version: $s0")
        }

        // S1
        val s1 = ByteArray(1536)
        readFully(inp, s1)

        // C2: echo S1
        out.write(s1)
        out.flush()

        // S2
        val s2 = ByteArray(1536)
        readFully(inp, s2)

        Log.d(TAG, "RTMP handshake completed")
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw IOException("Unexpected end of stream")
            offset += read
        }
    }

    companion object {
        private const val TAG = "RtmpConnection"
    }
}
