package com.moblin.android.streaming.protocol

/**
 * Utility for creating RTMP chunk headers.
 */
object RtmpChunkHeader {

    /**
     * Creates a Format 0 (full) RTMP chunk header.
     *
     * @param chunkStreamId Chunk stream ID (2-65599)
     * @param timestamp Timestamp in milliseconds
     * @param messageLength Length of the message body
     * @param messageTypeId Message type (0x08=Audio, 0x09=Video, etc.)
     * @param messageStreamId Message stream ID
     * @return 12-byte header
     */
    fun create(
        chunkStreamId: Int,
        timestamp: Int,
        messageLength: Int,
        messageTypeId: Int,
        messageStreamId: Int,
    ): ByteArray {
        val header = ByteArray(12)
        // Basic header: format(2 bits) = 0, csid(6 bits)
        header[0] = (0x00 or (chunkStreamId and 0x3F)).toByte()
        // Timestamp (3 bytes, big-endian)
        header[1] = (timestamp shr 16 and 0xFF).toByte()
        header[2] = (timestamp shr 8 and 0xFF).toByte()
        header[3] = (timestamp and 0xFF).toByte()
        // Message length (3 bytes, big-endian)
        header[4] = (messageLength shr 16 and 0xFF).toByte()
        header[5] = (messageLength shr 8 and 0xFF).toByte()
        header[6] = (messageLength and 0xFF).toByte()
        // Message type ID
        header[7] = messageTypeId.toByte()
        // Message stream ID (4 bytes, little-endian)
        header[8] = (messageStreamId and 0xFF).toByte()
        header[9] = (messageStreamId shr 8 and 0xFF).toByte()
        header[10] = (messageStreamId shr 16 and 0xFF).toByte()
        header[11] = (messageStreamId shr 24 and 0xFF).toByte()
        return header
    }
}
