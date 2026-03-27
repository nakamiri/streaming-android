package com.reaream.app.chat

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChatManagerTest {

    private lateinit var chatManager: ChatManager

    @Before
    fun setup() {
        chatManager = ChatManager()
    }

    @Test
    fun `initial state has no messages`() {
        assertTrue(chatManager.messages.value.isEmpty())
    }

    @Test
    fun `initial state is not connected`() {
        assertFalse(chatManager.isConnected.value)
    }

    @Test
    fun `addMessage adds message to list`() {
        val message = ChatMessage(
            id = "1",
            platform = ChatPlatform.TWITCH,
            username = "testuser",
            message = "hello",
        )

        chatManager.addMessage(message)

        assertEquals(1, chatManager.messages.value.size)
        assertEquals("hello", chatManager.messages.value[0].message)
    }

    @Test
    fun `addMessage caps at MAX_MESSAGES`() {
        repeat(ChatManager.MAX_MESSAGES + 50) { i ->
            chatManager.addMessage(
                ChatMessage(
                    id = "$i",
                    platform = ChatPlatform.TWITCH,
                    username = "user",
                    message = "message $i",
                )
            )
        }

        assertEquals(ChatManager.MAX_MESSAGES, chatManager.messages.value.size)
        // First message should be the 51st (index 50)
        assertEquals("message 50", chatManager.messages.value.first().message)
    }

    @Test
    fun `clearMessages empties the list`() {
        chatManager.addMessage(
            ChatMessage(id = "1", platform = ChatPlatform.TWITCH, username = "u", message = "m")
        )
        assertFalse(chatManager.messages.value.isEmpty())

        chatManager.clearMessages()

        assertTrue(chatManager.messages.value.isEmpty())
    }

    @Test
    fun `disconnect sets isConnected to false`() {
        chatManager.disconnect()
        assertFalse(chatManager.isConnected.value)
    }

    @Test
    fun `messages preserve order`() {
        val messages = listOf("first", "second", "third")
        messages.forEachIndexed { index, msg ->
            chatManager.addMessage(
                ChatMessage(id = "$index", platform = ChatPlatform.TWITCH, username = "u", message = msg)
            )
        }

        assertEquals(messages, chatManager.messages.value.map { it.message })
    }
}
