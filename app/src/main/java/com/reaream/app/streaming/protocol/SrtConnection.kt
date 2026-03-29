package com.reaream.app.streaming.protocol

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI

/**
 * SRT connection implementation using UDP transport.
 * This is a simplified SRT implementation that handles basic data transmission.
 * For production use, a native SRT library (libsrt) should be integrated via JNI.
 */
class SrtConnection(
    private val url: String,
    private val latencyMs: Int = 2000,
) : StreamConnection {

    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private var port: Int = 0

    @Volatile
    override var isConnected: Boolean = false
        private set

    override suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            address = InetAddress.getByName(uri.host)
            port = if (uri.port > 0) uri.port else 9000

            socket = DatagramSocket().apply {
                soTimeout = latencyMs
                sendBufferSize = 1024 * 1024
            }

            isConnected = true
            Log.i(TAG, "SRT connected to ${uri.host}:$port (latency: ${latencyMs}ms)")
        } catch (e: Exception) {
            isConnected = false
            throw IOException("SRT connection failed: ${e.message}", e)
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        isConnected = false
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing SRT connection", e)
        }
        socket = null
        address = null
        Log.i(TAG, "SRT disconnected")
    }

    override fun sendVideo(data: ByteArray, timestampUs: Long, flags: Int): Boolean {
        return sendData(data, timestampUs)
    }

    override fun sendAudio(data: ByteArray, timestampUs: Long, flags: Int): Boolean {
        return sendData(data, timestampUs)
    }

    private fun sendData(data: ByteArray, timestampUs: Long): Boolean {
        if (!isConnected) return false
        try {
            val addr = address ?: return false
            // Fragment large packets into MTU-sized chunks
            val maxPayload = 1316 // SRT default payload size
            var offset = 0
            while (offset < data.size) {
                val chunkSize = minOf(maxPayload, data.size - offset)
                val packet = DatagramPacket(data, offset, chunkSize, addr, port)
                socket?.send(packet)
                offset += chunkSize
            }
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error sending SRT data", e)
            isConnected = false
            return false
        }
    }

    companion object {
        private const val TAG = "SrtConnection"
    }
}
