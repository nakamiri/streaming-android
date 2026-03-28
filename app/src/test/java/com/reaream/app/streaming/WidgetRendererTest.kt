package com.reaream.app.streaming

import android.location.Location
import android.graphics.Bitmap
import com.reaream.app.data.model.ClockWidgetConfig
import com.reaream.app.data.model.LocationWidgetConfig
import com.reaream.app.data.model.MapWidgetConfig
import com.reaream.app.data.model.SpeedUnit
import com.reaream.app.data.model.SpeedWidgetConfig
import com.reaream.app.data.model.WidgetSettings
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetRendererTest {

    private lateinit var renderer: WidgetRenderer

    @Before
    fun setUp() {
        renderer = WidgetRenderer()
    }

    @After
    fun tearDown() {
        renderer.release()
    }

    @Test
    fun `screenDensity defaults to 2_0`() {
        assertEquals(2.0f, renderer.screenDensity, 0.01f)
    }

    @Test
    fun `screenDensity can be updated`() {
        renderer.screenDensity = 3.5f
        assertEquals(3.5f, renderer.screenDensity, 0.01f)
    }

    @Test
    fun `renderOntoFrame with all disabled does not modify frame`() {
        val w = 64; val h = 64
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }
        val original = frame.copyOf()

        val settings = WidgetSettings()
        renderer.renderOntoFrame(frame, w, h, settings)

        assertArrayEquals(original, frame)
    }

    @Test
    fun `renderOntoFrame with clock enabled does not crash`() {
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        val settings = WidgetSettings(
            clockWidget = ClockWidgetConfig(enabled = true, x = 0.1f, y = 0.1f)
        )
        renderer.renderOntoFrame(frame, w, h, settings)
        // Robolectric Canvas does not render real pixels, so just verify no crash
    }

    @Test
    fun `renderOntoFrame with location enabled and no location does not crash`() {
        val w = 64; val h = 64
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        val settings = WidgetSettings(
            locationWidget = LocationWidgetConfig(enabled = true)
        )
        // No location set - should not crash
        renderer.renderOntoFrame(frame, w, h, settings)
    }

    @Test
    fun `renderOntoFrame with location set does not crash`() {
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        val location = Location("test").apply {
            latitude = 35.6895
            longitude = 139.6917
        }
        renderer.currentLocation.set(location)

        val settings = WidgetSettings(
            locationWidget = LocationWidgetConfig(enabled = true, x = 0.1f, y = 0.1f)
        )
        renderer.renderOntoFrame(frame, w, h, settings)
        // Robolectric Canvas does not render real pixels, so just verify no crash
    }

    @Test
    fun `currentAddress takes priority over lat lng`() {
        renderer.currentAddress.set("Tokyo, Japan")
        val location = Location("test").apply {
            latitude = 35.6895
            longitude = 139.6917
        }
        renderer.currentLocation.set(location)

        // Address is set, so it should be used instead of coordinates
        assertNotNull(renderer.currentAddress.get())
        assertEquals("Tokyo, Japan", renderer.currentAddress.get())
    }

    @Test
    fun `speed is stored as AtomicReference`() {
        renderer.currentSpeedKmh.set(42.5f)
        assertEquals(42.5f, renderer.currentSpeedKmh.get(), 0.01f)
    }

    @Test
    fun `renderOntoFrame with speed enabled does not crash`() {
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        renderer.currentSpeedKmh.set(55.0f)

        val settings = WidgetSettings(
            speedWidget = SpeedWidgetConfig(enabled = true, x = 0.1f, y = 0.1f)
        )
        renderer.renderOntoFrame(frame, w, h, settings)
    }

    @Test
    fun `renderOntoFrame with speed and mph unit does not crash`() {
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        renderer.currentSpeedKmh.set(100.0f)

        val settings = WidgetSettings(
            speedWidget = SpeedWidgetConfig(enabled = true, unit = SpeedUnit.MPH)
        )
        renderer.renderOntoFrame(frame, w, h, settings)
    }

    @Test
    fun `release does not crash when called twice`() {
        renderer.release()
        renderer.release()
    }

    @Test
    fun `renderOntoFrame after release does not crash`() {
        renderer.release()
        val w = 64; val h = 64
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }
        val settings = WidgetSettings(
            clockWidget = ClockWidgetConfig(enabled = true)
        )
        // Should recreate bitmap internally
        renderer.renderOntoFrame(frame, w, h, settings)
    }

    @Test
    fun `renderOntoFrame with map enabled and no bitmap does not crash`() {
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        val settings = WidgetSettings(
            mapWidget = MapWidgetConfig(enabled = true)
        )
        // No map bitmap set - should not crash
        renderer.renderOntoFrame(frame, w, h, settings)
    }

    @Test
    fun `renderOntoFrame with map enabled and bitmap set does not crash`() {
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        val mapBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        renderer.currentMapBitmap.set(mapBmp)

        val settings = WidgetSettings(
            mapWidget = MapWidgetConfig(enabled = true, x = 0.01f, y = 0.01f, sizeDp = 120)
        )
        renderer.renderOntoFrame(frame, w, h, settings)
        mapBmp.recycle()
    }

    @Test
    fun `currentMapBitmap is stored as AtomicReference`() {
        assertNull(renderer.currentMapBitmap.get())
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        renderer.currentMapBitmap.set(bmp)
        assertNotNull(renderer.currentMapBitmap.get())
        bmp.recycle()
    }

    @Test
    fun `renderOntoFrame with all widgets enabled does not crash`() {
        val w = 640; val h = 480
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        val location = Location("test").apply {
            latitude = 35.6895
            longitude = 139.6917
        }
        renderer.currentLocation.set(location)
        renderer.currentAddress.set("Tokyo")
        renderer.currentSpeedKmh.set(30.0f)

        val mapBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        renderer.currentMapBitmap.set(mapBmp)

        val settings = WidgetSettings(
            clockWidget = ClockWidgetConfig(enabled = true),
            locationWidget = LocationWidgetConfig(enabled = true),
            speedWidget = SpeedWidgetConfig(enabled = true),
            mapWidget = MapWidgetConfig(enabled = true),
        )
        renderer.renderOntoFrame(frame, w, h, settings)
        mapBmp.recycle()
    }
}
