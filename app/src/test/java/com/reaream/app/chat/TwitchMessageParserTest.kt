package com.reaream.app.chat

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TwitchMessageParserTest {

    @Before
    fun setup() {
        TwitchMessageParser.resetCounter()
    }

    @Test
    fun `parse basic PRIVMSG`() {
        val raw = ":testuser!testuser@testuser.tmi.twitch.tv PRIVMSG #channel :Hello World"
        val msg = TwitchMessageParser.parse(raw)

        assertNotNull(msg)
        assertEquals("testuser", msg!!.username)
        assertEquals("Hello World", msg.message)
        assertEquals(ChatPlatform.TWITCH, msg.platform)
        assertFalse(msg.isAction)
    }

    @Test
    fun `parse PRIVMSG with tags`() {
        val raw = "@badge-info=subscriber/12;badges=subscriber/12,premium/1;" +
                "color=#FF4500;display-name=CoolUser;id=abc-123;" +
                "mod=0;subscriber=1;turbo=0;user-type= " +
                ":cooluser!cooluser@cooluser.tmi.twitch.tv PRIVMSG #channel :GG!"
        val msg = TwitchMessageParser.parse(raw)

        assertNotNull(msg)
        assertEquals("CoolUser", msg!!.username)
        assertEquals("GG!", msg.message)
        assertEquals("#FF4500", msg.color)
        assertEquals("abc-123", msg.id)
        assertTrue(msg.badges.contains("subscriber"))
        assertTrue(msg.badges.contains("premium"))
    }

    @Test
    fun `parse action message`() {
        val raw = "@display-name=Streamer;id=xyz " +
                ":streamer!streamer@streamer.tmi.twitch.tv PRIVMSG #channel :" +
                "\u0001ACTION is streaming!\u0001"
        val msg = TwitchMessageParser.parse(raw)

        assertNotNull(msg)
        assertTrue(msg!!.isAction)
        assertEquals("is streaming!", msg.message)
    }

    @Test
    fun `returns null for non-PRIVMSG`() {
        val raw = ":tmi.twitch.tv 001 justinfan12345 :Welcome, GLHF!"
        assertNull(TwitchMessageParser.parse(raw))
    }

    @Test
    fun `returns null for PING`() {
        assertNull(TwitchMessageParser.parse("PING :tmi.twitch.tv"))
    }

    @Test
    fun `returns null for JOIN message`() {
        assertNull(TwitchMessageParser.parse(":user!user@user.tmi.twitch.tv JOIN #channel"))
    }

    @Test
    fun `parse message without display-name tag uses IRC nick`() {
        val raw = "@color=#00FF00;id=test-id " +
                ":someuser!someuser@someuser.tmi.twitch.tv PRIVMSG #channel :hi there"
        val msg = TwitchMessageParser.parse(raw)

        assertNotNull(msg)
        assertEquals("someuser", msg!!.username)
    }

    @Test
    fun `parse message with empty color returns null color`() {
        val raw = "@color=;display-name=User;id=test " +
                ":user!user@user.tmi.twitch.tv PRIVMSG #channel :test"
        val msg = TwitchMessageParser.parse(raw)

        assertNotNull(msg)
        assertNull(msg!!.color)
    }

    @Test
    fun `parse message with no badges returns empty list`() {
        val raw = "@display-name=User;id=test " +
                ":user!user@user.tmi.twitch.tv PRIVMSG #channel :test"
        val msg = TwitchMessageParser.parse(raw)

        assertNotNull(msg)
        assertTrue(msg!!.badges.isEmpty())
    }

    @Test
    fun `parse message with multiple badges`() {
        val raw = "@badges=broadcaster/1,subscriber/3012,premium/1;display-name=User;id=test " +
                ":user!user@user.tmi.twitch.tv PRIVMSG #channel :test"
        val msg = TwitchMessageParser.parse(raw)

        assertNotNull(msg)
        assertEquals(3, msg!!.badges.size)
        assertTrue(msg.badges.contains("broadcaster"))
        assertTrue(msg.badges.contains("subscriber"))
        assertTrue(msg.badges.contains("premium"))
    }

    @Test
    fun `auto-incremented ID when no id tag present`() {
        val raw1 = ":user!user@user.tmi.twitch.tv PRIVMSG #ch :msg1"
        val raw2 = ":user!user@user.tmi.twitch.tv PRIVMSG #ch :msg2"

        val msg1 = TwitchMessageParser.parse(raw1)
        val msg2 = TwitchMessageParser.parse(raw2)

        assertNotNull(msg1)
        assertNotNull(msg2)
        assertNotEquals(msg1!!.id, msg2!!.id)
    }

    @Test
    fun `parse message with unicode content`() {
        val raw = "@display-name=User;id=uni-test " +
                ":user!user@user.tmi.twitch.tv PRIVMSG #channel :\u3053\u3093\u306B\u3061\u306F\uD83D\uDC4B"
        val msg = TwitchMessageParser.parse(raw)

        assertNotNull(msg)
        assertEquals("\u3053\u3093\u306B\u3061\u306F\uD83D\uDC4B", msg!!.message)
    }
}
