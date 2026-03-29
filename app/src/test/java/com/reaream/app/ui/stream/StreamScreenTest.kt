package com.reaream.app.ui.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamScreenTest {

    @Test
    fun `compact widget edit buttons are used on narrow portrait screens`() {
        assertTrue(
            shouldUseCompactWidgetEditButtons(
                isLandscape = false,
                screenWidthDp = 411,
            )
        )
    }

    @Test
    fun `compact widget edit buttons are not used on wide portrait screens`() {
        assertFalse(
            shouldUseCompactWidgetEditButtons(
                isLandscape = false,
                screenWidthDp = 480,
            )
        )
    }

    @Test
    fun `compact widget edit buttons are not used in landscape`() {
        assertFalse(
            shouldUseCompactWidgetEditButtons(
                isLandscape = true,
                screenWidthDp = 411,
            )
        )
    }
}
