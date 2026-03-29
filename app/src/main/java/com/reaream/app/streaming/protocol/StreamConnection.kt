package com.reaream.app.streaming.protocol

import com.reaream.app.data.model.VideoCodec

interface StreamConnection {
    suspend fun connect()
    suspend fun disconnect()
    fun sendVideo(data: ByteArray, timestampUs: Long, flags: Int = 0): Boolean
    fun sendAudio(data: ByteArray, timestampUs: Long, flags: Int = 0): Boolean
    val isConnected: Boolean

    fun updateVideoParameters(width: Int, height: Int, codec: VideoCodec) = Unit
}
