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
                displayFrameIntervalNs = GlStreamPipeline.DEFAULT_DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS,
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
                displayFrameIntervalNs = GlStreamPipeline.DEFAULT_DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS,
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
                    10_000_000L - GlStreamPipeline.DEFAULT_DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS + 1L,
                displayFrameIntervalNs = GlStreamPipeline.DEFAULT_DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS,
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
                    10_000_000L - GlStreamPipeline.DEFAULT_DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS,
                displayFrameIntervalNs = GlStreamPipeline.DEFAULT_DISPLAY_FRAME_INTERVAL_WHILE_ENCODING_NS,
            )
        )
    }

    @Test
    fun `display throttling can be disabled with zero interval`() {
        assertTrue(
            shouldRenderDisplayFrame(
                frameTimestampNs = 10_000_000L,
                hasEncoder = true,
                lastDisplayRenderTimestampNs = 9_999_999L,
                displayFrameIntervalNs = 0L,
            )
        )
    }
}
