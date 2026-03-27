package com.moblin.android.streaming.protocol

import org.junit.Assert.*
import org.junit.Test

class RtmpChunkHeaderTest {

    @Test
    fun `header is 12 bytes`() {
        val header = RtmpChunkHeader.create(
            chunkStreamId = 6,
            timestamp = 0,
            messageLength = 0,
            messageTypeId = 0x09,
            messageStreamId = 1,
        )
        assertEquals(12, header.size)
    }

    @Test
    fun `chunk stream id is in first byte lower 6 bits`() {
        val header = RtmpChunkHeader.create(
            chunkStreamId = 6,
            timestamp = 0,
            messageLength = 0,
            messageTypeId = 0,
            messageStreamId = 0,
        )
        assertEquals(6, (header[0].toInt() and 0x3F))
    }

    @Test
    fun `format bits are zero for type 0 header`() {
        val header = RtmpChunkHeader.create(
            chunkStreamId = 6,
            timestamp = 0,
            messageLength = 0,
            messageTypeId = 0,
            messageStreamId = 0,
        )
        assertEquals(0, (header[0].toInt() and 0xC0))
    }

    @Test
    fun `timestamp is big-endian in bytes 1-3`() {
        val timestamp = 0x123456
        val header = RtmpChunkHeader.create(
            chunkStreamId = 2,
            timestamp = timestamp,
            messageLength = 0,
            messageTypeId = 0,
            messageStreamId = 0,
        )
        assertEquals(0x12, header[1].toInt() and 0xFF)
        assertEquals(0x34, header[2].toInt() and 0xFF)
        assertEquals(0x56, header[3].toInt() and 0xFF)
    }

    @Test
    fun `message length is big-endian in bytes 4-6`() {
        val messageLength = 0xABCDEF
        val header = RtmpChunkHeader.create(
            chunkStreamId = 2,
            timestamp = 0,
            messageLength = messageLength,
            messageTypeId = 0,
            messageStreamId = 0,
        )
        assertEquals(0xAB, header[4].toInt() and 0xFF)
        assertEquals(0xCD, header[5].toInt() and 0xFF)
        assertEquals(0xEF, header[6].toInt() and 0xFF)
    }

    @Test
    fun `message type id is in byte 7`() {
        val header = RtmpChunkHeader.create(
            chunkStreamId = 2,
            timestamp = 0,
            messageLength = 0,
            messageTypeId = 0x09,
            messageStreamId = 0,
        )
        assertEquals(0x09, header[7].toInt() and 0xFF)
    }

    @Test
    fun `message stream id is little-endian in bytes 8-11`() {
        val streamId = 0x01020304
        val header = RtmpChunkHeader.create(
            chunkStreamId = 2,
            timestamp = 0,
            messageLength = 0,
            messageTypeId = 0,
            messageStreamId = streamId,
        )
        assertEquals(0x04, header[8].toInt() and 0xFF)
        assertEquals(0x03, header[9].toInt() and 0xFF)
        assertEquals(0x02, header[10].toInt() and 0xFF)
        assertEquals(0x01, header[11].toInt() and 0xFF)
    }

    @Test
    fun `video chunk header uses csid 6 and type 0x09`() {
        val header = RtmpChunkHeader.create(
            chunkStreamId = 6,
            timestamp = 1000,
            messageLength = 4096,
            messageTypeId = 0x09,
            messageStreamId = 1,
        )
        assertEquals(6, header[0].toInt() and 0x3F)
        assertEquals(0x09, header[7].toInt() and 0xFF)
    }

    @Test
    fun `audio chunk header uses csid 4 and type 0x08`() {
        val header = RtmpChunkHeader.create(
            chunkStreamId = 4,
            timestamp = 500,
            messageLength = 1024,
            messageTypeId = 0x08,
            messageStreamId = 1,
        )
        assertEquals(4, header[0].toInt() and 0x3F)
        assertEquals(0x08, header[7].toInt() and 0xFF)
    }

    @Test
    fun `zero timestamp produces zero bytes`() {
        val header = RtmpChunkHeader.create(
            chunkStreamId = 2,
            timestamp = 0,
            messageLength = 0,
            messageTypeId = 0,
            messageStreamId = 0,
        )
        assertEquals(0, header[1].toInt())
        assertEquals(0, header[2].toInt())
        assertEquals(0, header[3].toInt())
    }
}
