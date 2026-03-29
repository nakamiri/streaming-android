package com.reaream.app.streaming.gl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlStreamPipelineTest {

    @Test
    fun `display frames are always rendered when encoder is detached`() {
        assertTrue(
            shouldRenderDisplayFrame(
                frameTimestampNs = 10_000_000L,
                hasEncoder = false,
                lastDisplayRenderTimestampNs = 9_000_000L,
            )
        )
    }

    @Test
    fun `first display frame is rendered while encoding`() {
        assertTrue(
            shouldRenderDisplayFrame(
                frameTimestampNs = 10_000_000L,
                hasEncoder = true,
                lastDisplayRenderTimestampNs = Long.MIN_VALUE,
            )
        )
    }

    @Test
    fun `display frames are throttled while encoding`() {
        assertFalse(
            shouldRenderDisplayFrame(
                frameTimestampNs = 10_000_000L,
                hasEncoder = true,
                lastDisplayRenderTimestampNs =
                    10_000_000L - GlStreamPipeline.DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS + 1L,
            )
        )
    }

    @Test
    fun `display frame renders once throttle interval elapsed`() {
        assertTrue(
            shouldRenderDisplayFrame(
                frameTimestampNs = 10_000_000L,
                hasEncoder = true,
                lastDisplayRenderTimestampNs =
                    10_000_000L - GlStreamPipeline.DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS,
            )
        )
    }
}
