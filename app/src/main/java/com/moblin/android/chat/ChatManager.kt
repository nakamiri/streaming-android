package com.moblin.android.chat

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String,
    val platform: ChatPlatform,
    val username: String,
    val message: String,
    val color: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val badges: List<String> = emptyList(),
    val isAction: Boolean = false,
)

enum class ChatPlatform { TWITCH, YOUTUBE }

class ChatManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var twitchSocket: WebSocket? = null

    fun connectTwitch(channel: String) {
        if (channel.isBlank()) return

        val request = Request.Builder()
            .url("wss://irc-ws.chat.twitch.tv:443")
            .build()

        twitchSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                webSocket.send("PASS SCHMOOPIIE")
                webSocket.send("NICK justinfan${(10000..99999).random()}")
                webSocket.send("JOIN #${channel.lowercase()}")
                _isConnected.value = true
                Log.i(TAG, "Connected to Twitch chat: #$channel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.lines().filter { it.isNotBlank() }.forEach { line ->
                    if (line.startsWith("PING")) {
                        webSocket.send("PONG :tmi.twitch.tv")
                        return@forEach
                    }
                    TwitchMessageParser.parse(line)?.let { msg ->
                        addMessage(msg)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Twitch chat error", t)
                _isConnected.value = false
                scope.launch {
                    delay(5000)
                    connectTwitch(channel)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                Log.i(TAG, "Twitch chat disconnected: $reason")
            }
        })
    }

    fun disconnect() {
        twitchSocket?.close(1000, "Disconnecting")
        twitchSocket = null
        _isConnected.value = false
    }

    fun sendMessage(message: String) {
        twitchSocket?.send("PRIVMSG $message")
    }

    internal fun addMessage(message: ChatMessage) {
        val current = _messages.value.toMutableList()
        current.add(message)
        if (current.size > MAX_MESSAGES) {
            current.removeAt(0)
        }
        _messages.value = current
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun release() {
        disconnect()
        scope.cancel()
    }

    companion object {
        private const val TAG = "ChatManager"
        internal const val MAX_MESSAGES = 200
    }
}
