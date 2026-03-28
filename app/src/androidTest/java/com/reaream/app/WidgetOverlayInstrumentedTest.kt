package com.reaream.app

import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reaream.app.data.model.*
import com.reaream.app.streaming.WidgetRenderer
import com.reaream.app.streaming.YuvCompositor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests that verify widget rendering on actual Android runtime.
 * Unlike Robolectric unit tests, Canvas produces real pixels here.
 */
@RunWith(AndroidJUnit4::class)
class WidgetOverlayInstrumentedTest {

    @Test
    fun yuvCompositorBlendsOpaqueWhitePixel() {
        val w = 4; val h = 4
        val frame = ByteArray(w * h * 3 / 2) { 0 }

        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        overlay.setPixel(0, 0, Color.WHITE)

        YuvCompositor.blendOntoI420(overlay, frame, w, h)

        val y = frame[0].toInt() and 0xFF
        assertTrue("Y for white should be near 235, was $y", y in 220..255)
    }

    @Test
    fun yuvCompositorPreservesTransparentArea() {
        val w = 4; val h = 4
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }
        val original = frame.copyOf()

        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        YuvCompositor.blendOntoI420(overlay, frame, w, h)

        assertArrayEquals(original, frame)
    }

    @Test
    fun widgetRendererClockModifiesFrame() {
        val renderer = WidgetRenderer()
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }
        val original = frame.copyOf()

        val settings = WidgetSettings(
            clockWidget = ClockWidgetConfig(enabled = true, x = 0.1f, y = 0.1f)
        )
        renderer.renderOntoFrame(frame, w, h, settings)

        assertFalse("Frame should be modified by clock rendering", frame.contentEquals(original))
        renderer.release()
    }

    @Test
    fun widgetRendererLocationModifiesFrame() {
        val renderer = WidgetRenderer()
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }
        val original = frame.copyOf()

        renderer.currentLocation.set(Location("test").apply {
            latitude = 35.6895
            longitude = 139.6917
        })

        val settings = WidgetSettings(
            locationWidget = LocationWidgetConfig(enabled = true, x = 0.1f, y = 0.1f)
        )
        renderer.renderOntoFrame(frame, w, h, settings)

        assertFalse("Frame should be modified by location rendering", frame.contentEquals(original))
        renderer.release()
    }

    @Test
    fun widgetRendererSpeedModifiesFrame() {
        val renderer = WidgetRenderer()
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }
        val original = frame.copyOf()

        renderer.currentSpeedKmh.set(42.5f)

        val settings = WidgetSettings(
            speedWidget = SpeedWidgetConfig(enabled = true, x = 0.1f, y = 0.1f)
        )
        renderer.renderOntoFrame(frame, w, h, settings)

        assertFalse("Frame should be modified by speed rendering", frame.contentEquals(original))
        renderer.release()
    }

    @Test
    fun widgetRendererDisabledDoesNotModify() {
        val renderer = WidgetRenderer()
        val w = 64; val h = 64
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }
        val original = frame.copyOf()

        renderer.renderOntoFrame(frame, w, h, WidgetSettings())
        assertArrayEquals(original, frame)
        renderer.release()
    }
}
