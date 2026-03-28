package com.reaream.app.streaming

import org.junit.Assert.*
import org.junit.Test

class YuvUtilsTest {

    @Test
    fun `0 degrees returns same data and dimensions`() {
        val src = byteArrayOf(1, 2, 3, 4, 5, 6) // 2x2 Y + 1x1 U + 1x1 V
        val result = YuvUtils.rotateI420(src, 2, 2, 0)
        assertArrayEquals(src, result.data)
        assertEquals(2, result.width)
        assertEquals(2, result.height)
    }

    @Test
    fun `unsupported degrees returns same data`() {
        val src = byteArrayOf(1, 2, 3, 4, 5, 6)
        val result = YuvUtils.rotateI420(src, 2, 2, 45)
        assertArrayEquals(src, result.data)
    }

    @Test
    fun `90 degree rotation swaps dimensions`() {
        // 4x2 image → should become 2x4
        val w = 4; val h = 2
        val ySize = w * h
        val uvW = w / 2; val uvH = h / 2
        val src = ByteArray(w * h * 3 / 2)
        for (i in src.indices) src[i] = i.toByte()

        val result = YuvUtils.rotateI420(src, w, h, 90)
        assertEquals(h, result.width)  // 2
        assertEquals(w, result.height) // 4
        assertEquals(src.size, result.data.size)
    }

    @Test
    fun `270 degree rotation swaps dimensions`() {
        val w = 4; val h = 2
        val src = ByteArray(w * h * 3 / 2)
        for (i in src.indices) src[i] = i.toByte()

        val result = YuvUtils.rotateI420(src, w, h, 270)
        assertEquals(h, result.width)
        assertEquals(w, result.height)
    }

    @Test
    fun `180 degree rotation preserves dimensions`() {
        val w = 4; val h = 2
        val src = ByteArray(w * h * 3 / 2)
        for (i in src.indices) src[i] = i.toByte()

        val result = YuvUtils.rotateI420(src, w, h, 180)
        assertEquals(w, result.width)
        assertEquals(h, result.height)
    }

    @Test
    fun `90 then 270 is identity for Y plane`() {
        val w = 4; val h = 2
        val src = ByteArray(w * h * 3 / 2)
        for (i in src.indices) src[i] = (i + 1).toByte()

        val r90 = YuvUtils.rotateI420(src, w, h, 90)
        val r360 = YuvUtils.rotateI420(r90.data, r90.width, r90.height, 270)

        assertEquals(w, r360.width)
        assertEquals(h, r360.height)
        assertArrayEquals(src, r360.data)
    }

    @Test
    fun `180 twice is identity`() {
        val w = 4; val h = 2
        val src = ByteArray(w * h * 3 / 2)
        for (i in src.indices) src[i] = (i + 10).toByte()

        val r180 = YuvUtils.rotateI420(src, w, h, 180)
        val r360 = YuvUtils.rotateI420(r180.data, r180.width, r180.height, 180)

        assertArrayEquals(src, r360.data)
    }

    @Test
    fun `90 degree Y plane pixel mapping is correct`() {
        // 4x2 Y plane:
        // [ 0  1  2  3 ]
        // [ 4  5  6  7 ]
        // After 90° CW (becomes 2x4):
        // [ 4  0 ]
        // [ 5  1 ]
        // [ 6  2 ]
        // [ 7  3 ]
        val w = 4; val h = 2
        val src = ByteArray(w * h * 3 / 2)
        for (i in 0 until w * h) src[i] = i.toByte()

        val result = YuvUtils.rotateI420(src, w, h, 90)
        val y = result.data

        assertEquals(4.toByte(), y[0]) // top-left
        assertEquals(0.toByte(), y[1]) // top-right
        assertEquals(5.toByte(), y[2])
        assertEquals(1.toByte(), y[3])
        assertEquals(6.toByte(), y[4])
        assertEquals(2.toByte(), y[5])
        assertEquals(7.toByte(), y[6])
        assertEquals(3.toByte(), y[7])
    }

    @Test
    fun `rotatePlane90 basic verification`() {
        // 3x2 plane:
        // [1 2 3]
        // [4 5 6]
        // 90° CW → 2x3:
        // [4 1]
        // [5 2]
        // [6 3]
        val src = byteArrayOf(1, 2, 3, 4, 5, 6)
        val dst = ByteArray(6)
        YuvUtils.rotatePlane90(src, 0, dst, 0, 3, 2)

        assertArrayEquals(byteArrayOf(4, 1, 5, 2, 6, 3), dst)
    }

    @Test
    fun `rotatePlane270 basic verification`() {
        // 3x2 plane:
        // [1 2 3]
        // [4 5 6]
        // 270° CW → 2x3:
        // [3 6]
        // [2 5]
        // [1 4]
        val src = byteArrayOf(1, 2, 3, 4, 5, 6)
        val dst = ByteArray(6)
        YuvUtils.rotatePlane270(src, 0, dst, 0, 3, 2)

        assertArrayEquals(byteArrayOf(3, 6, 2, 5, 1, 4), dst)
    }

    // ── scaleI420 tests ──

    @Test
    fun `scaleI420 same size returns same data`() {
        val w = 4; val h = 4
        val src = ByteArray(w * h * 3 / 2) { it.toByte() }
        val result = YuvUtils.scaleI420(src, w, h, w, h)
        assertArrayEquals(src, result)
    }

    @Test
    fun `scaleI420 returns identity reference when same size`() {
        val w = 4; val h = 4
        val src = ByteArray(w * h * 3 / 2)
        val result = YuvUtils.scaleI420(src, w, h, w, h)
        assertSame(src, result)
    }

    @Test
    fun `scaleI420 downscale output has correct size`() {
        val srcW = 8; val srcH = 8
        val dstW = 4; val dstH = 4
        val src = ByteArray(srcW * srcH * 3 / 2) { it.toByte() }
        val result = YuvUtils.scaleI420(src, srcW, srcH, dstW, dstH)
        assertEquals(dstW * dstH * 3 / 2, result.size)
    }

    @Test
    fun `scaleI420 upscale output has correct size`() {
        val srcW = 4; val srcH = 4
        val dstW = 8; val dstH = 8
        val src = ByteArray(srcW * srcH * 3 / 2) { it.toByte() }
        val result = YuvUtils.scaleI420(src, srcW, srcH, dstW, dstH)
        assertEquals(dstW * dstH * 3 / 2, result.size)
    }

    @Test
    fun `scaleI420 2x downscale Y plane uses nearest neighbor`() {
        // 4x4 → 2x2 nearest-neighbor: picks pixels at (0,0),(2,0),(0,2),(2,2)
        val srcW = 4; val srcH = 4
        val src = ByteArray(srcW * srcH * 3 / 2)
        // Fill Y plane:
        // [ 10 11 12 13 ]
        // [ 20 21 22 23 ]
        // [ 30 31 32 33 ]
        // [ 40 41 42 43 ]
        for (y in 0 until srcH) {
            for (x in 0 until srcW) {
                src[y * srcW + x] = ((y + 1) * 10 + x).toByte()
            }
        }

        val result = YuvUtils.scaleI420(src, srcW, srcH, 2, 2)
        // dst(0,0) ← src(0*4/2, 0*4/2) = src(0,0) = 10
        assertEquals(10.toByte(), result[0])
        // dst(1,0) ← src(0, 1*4/2) = src(0, 2) = 12
        assertEquals(12.toByte(), result[1])
        // dst(0,1) ← src(1*4/2, 0) = src(2, 0) = 30
        assertEquals(30.toByte(), result[2])
        // dst(1,1) ← src(2, 2) = 32
        assertEquals(32.toByte(), result[3])
    }

    @Test
    fun `scaleI420 non-uniform scale produces correct output size`() {
        // 8x4 → 4x2
        val srcW = 8; val srcH = 4
        val dstW = 4; val dstH = 2
        val src = ByteArray(srcW * srcH * 3 / 2)
        val result = YuvUtils.scaleI420(src, srcW, srcH, dstW, dstH)
        assertEquals(dstW * dstH * 3 / 2, result.size)
    }

    @Test
    fun `scaleI420 preserves UV plane structure`() {
        // 4x4 → 2x2: U plane is 1x1, V plane is 1x1
        val srcW = 4; val srcH = 4
        val src = ByteArray(srcW * srcH * 3 / 2)
        val srcUOff = srcW * srcH     // 16
        val srcVOff = srcUOff + 2 * 2 // 20
        src[srcUOff] = 50  // U(0,0)
        src[srcVOff] = 90  // V(0,0)

        val result = YuvUtils.scaleI420(src, srcW, srcH, 2, 2)
        val dstUOff = 2 * 2     // 4
        val dstVOff = dstUOff + 1 // 5
        assertEquals(50.toByte(), result[dstUOff])
        assertEquals(90.toByte(), result[dstVOff])
    }

    @Test
    fun `rotatePlane180 basic verification`() {
        // 3x2 plane:
        // [1 2 3]
        // [4 5 6]
        // 180° → 3x2:
        // [6 5 4]
        // [3 2 1]
        val src = byteArrayOf(1, 2, 3, 4, 5, 6)
        val dst = ByteArray(6)
        YuvUtils.rotatePlane180(src, 0, dst, 0, 3, 2)

        assertArrayEquals(byteArrayOf(6, 5, 4, 3, 2, 1), dst)
    }
}
