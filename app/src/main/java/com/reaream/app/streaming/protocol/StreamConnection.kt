package com.reaream.app.streaming.protocol

interface StreamConnection {
    suspend fun connect()
    suspend fun disconnect()
    fun sendVideo(data: ByteArray, timestampUs: Long, flags: Int = 0)
    fun sendAudio(data: ByteArray, timestampUs: Long, flags: Int = 0)
    val isConnected: Boolean
}
