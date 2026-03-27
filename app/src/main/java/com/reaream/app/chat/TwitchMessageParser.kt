package com.reaream.app.chat

/**
 * Parses raw Twitch IRC messages into ChatMessage objects.
 */
object TwitchMessageParser {

    private var messageCounter = 0L

    fun parse(raw: String): ChatMessage? {
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

            val username = tags["display-name"]?.takeIf { it.isNotEmpty() }
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
                it.split('/').firstOrNull()?.takeIf { badge -> badge.isNotEmpty() }
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
        } catch (_: Exception) {
            return null
        }
    }

    fun resetCounter() {
        messageCounter = 0
    }
}
