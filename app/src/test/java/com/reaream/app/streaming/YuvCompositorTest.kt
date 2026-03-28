package com.reaream.app.streaming

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class YuvCompositorTest {

    @Test
    fun `transparent overlay does not modify frame`() {
        val w = 4; val h = 4
        val frame = ByteArray(w * h * 3 / 2) { 128.toByte() }
        val original = frame.copyOf()

        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Default is all transparent (0x00000000)

        YuvCompositor.blendOntoI420(overlay, frame, w, h)
        assertArrayEquals(original, frame)
    }

    @Test
    fun `opaque white pixel sets Y to near white`() {
        val w = 2; val h = 2
        val frame = ByteArray(w * h * 3 / 2) { 0 }

        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        overlay.setPixel(0, 0, Color.WHITE) // 0xFFFFFFFF

        YuvCompositor.blendOntoI420(overlay, frame, w, h)

        // Y for white (255,255,255) = (66*255 + 129*255 + 25*255 + 128) >> 8 + 16 = 235
        val y = frame[0].toInt() and 0xFF
        assertTrue("Y should be near 235, was $y", y in 220..255)
    }

    @Test
    fun `opaque black pixel sets Y to near black`() {
        val w = 2; val h = 2
        val frame = ByteArray(w * h * 3 / 2) { 200.toByte() }

        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        overlay.setPixel(0, 0, Color.BLACK) // 0xFF000000

        YuvCompositor.blendOntoI420(overlay, frame, w, h)

        val y = frame[0].toInt() and 0xFF
        assertTrue("Y should be near 16, was $y", y in 0..30)
    }

    @Test
    fun `semi-transparent pixel blends with existing Y`() {
        val w = 2; val h = 2
        val frame = ByteArray(w * h * 3 / 2) { 128.toByte() }

        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // 50% transparent white: alpha=128, R=G=B=255
        overlay.setPixel(0, 0, Color.argb(128, 255, 255, 255))

        YuvCompositor.blendOntoI420(overlay, frame, w, h)

        val y = frame[0].toInt() and 0xFF
        // Should be blended between 128 (original) and 235 (white Y)
        assertTrue("Y should be between 128 and 235, was $y", y in 150..200)
    }

    @Test
    fun `overlay larger than frame is clamped`() {
        val w = 2; val h = 2
        val frame = ByteArray(w * h * 3 / 2) { 100.toByte() }

        val overlay = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        // Fill with opaque red
        for (x in 0 until 10) for (y in 0 until 10) overlay.setPixel(x, y, Color.RED)

        // Should not crash, only process within frame bounds
        YuvCompositor.blendOntoI420(overlay, frame, w, h)

        // Y for red should be modified
        val y = frame[0].toInt() and 0xFF
        assertNotEquals(100, y)
    }

    @Test
    fun `UV planes are set for opaque red pixel`() {
        val w = 2; val h = 2
        val ySize = w * h
        val uvSize = (w / 2) * (h / 2)
        val frame = ByteArray(ySize + uvSize * 2) { 128.toByte() }

        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        overlay.setPixel(0, 0, Color.RED)

        YuvCompositor.blendOntoI420(overlay, frame, w, h)

        // U for red (255,0,0) = (-38*255 - 74*0 + 112*0 + 128) >> 8 + 128 ≈ 90
        val u = frame[ySize].toInt() and 0xFF
        assertTrue("U for red should be < 128, was $u", u < 128)

        // V for red = (112*255 - 94*0 - 18*0 + 128) >> 8 + 128 ≈ 240
        val v = frame[ySize + uvSize].toInt() and 0xFF
        assertTrue("V for red should be > 200, was $v", v > 200)
    }
}
