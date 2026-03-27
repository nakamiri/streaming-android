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

enum class ChatPlatform { TWITCH, KICK, YOUTUBE }

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
    private var messageCounter = 0L

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
                    parseTwitchMessage(line)?.let { msg ->
                        addMessage(msg)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Twitch chat error", t)
                _isConnected.value = false
                // Reconnect after delay
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

    private fun parseTwitchMessage(raw: String): ChatMessage? {
        // Parse IRC message with tags
        if (!raw.contains("PRIVMSG")) return null

        try {
            var tags = mapOf<String, String>()
            var remaining = raw

            if (remaining.startsWith("@")) {
                val tagEnd = remaining.indexOf(' ')
                val tagString = remaining.substring(1, tagEnd)
                tags = tagString.split(';').associate {
                    val parts = it.split('=', limit = 2)
                    parts[0] to (parts.getOrNull(1) ?: "")
                }
                remaining = remaining.substring(tagEnd + 1)
            }

            val username = tags["display-name"]
                ?: remaining.substringBefore('!').removePrefix(":")
            val color = tags["color"]?.takeIf { it.isNotEmpty() }
            val message = remaining.substringAfter("PRIVMSG").substringAfter(':')
            val isAction = message.startsWith("\u0001ACTION") && message.endsWith("\u0001")
            val cleanMessage = if (isAction) {
                message.removePrefix("\u0001ACTION ").removeSuffix("\u0001")
            } else {
                message
            }

            val badges = tags["badges"]?.split(',')?.mapNotNull {
                it.split('/').firstOrNull()
            } ?: emptyList()

            return ChatMessage(
                id = tags["id"] ?: "${messageCounter++}",
                platform = ChatPlatform.TWITCH,
                username = username,
                message = cleanMessage.trim(),
                color = color,
                badges = badges,
                isAction = isAction,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Twitch message: $raw", e)
            return null
        }
    }

    private fun addMessage(message: ChatMessage) {
        val current = _messages.value.toMutableList()
        current.add(message)
        // Keep last 200 messages
        if (current.size > 200) {
            current.removeAt(0)
        }
        _messages.value = current
    }

    fun release() {
        disconnect()
        scope.cancel()
    }

    companion object {
        private const val TAG = "ChatManager"
    }
}
