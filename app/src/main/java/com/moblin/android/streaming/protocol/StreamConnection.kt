package com.moblin.android.streaming.protocol

interface StreamConnection {
    suspend fun connect()
    suspend fun disconnect()
    fun sendVideo(data: ByteArray, timestampUs: Long)
    fun sendAudio(data: ByteArray, timestampUs: Long)
    val isConnected: Boolean
}
