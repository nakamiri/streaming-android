package com.reaream.app.streaming

/**
 * Utilities for YUV frame rotation (I420 planar format).
 */
object YuvUtils {

    data class RotatedFrame(val data: ByteArray, val width: Int, val height: Int)

    fun rotateI420(src: ByteArray, srcW: Int, srcH: Int, degrees: Int): RotatedFrame {
        if (degrees == 0) return RotatedFrame(src, srcW, srcH)
        val dst = ByteArray(src.size)
        val (dstW, dstH) = rotateI420Into(src, srcW, srcH, degrees, dst)
        return RotatedFrame(dst, dstW, dstH)
    }

    /**
     * Rotate into a pre-allocated [dst] buffer to avoid per-frame allocation.
     * Returns the output dimensions (dstW, dstH). [dst] must be at least src.size bytes.
     * If [degrees] is 0 returns (srcW, srcH) without copying.
     */
    fun rotateI420Into(src: ByteArray, srcW: Int, srcH: Int, degrees: Int, dst: ByteArray): Pair<Int, Int> {
        if (degrees == 0) {
            System.arraycopy(src, 0, dst, 0, src.size)
            return srcW to srcH
        }

        val ySize = srcW * srcH
        val uvW = srcW / 2
        val uvH = srcH / 2
        val uvPlaneSize = uvW * uvH

        val dstW: Int
        val dstH: Int

        when (degrees) {
            90 -> {
                dstW = srcH; dstH = srcW
                rotatePlane90(src, 0, dst, 0, srcW, srcH)
                rotatePlane90(src, ySize, dst, dstW * dstH, uvW, uvH)
                rotatePlane90(src, ySize + uvPlaneSize, dst, dstW * dstH + (dstW / 2) * (dstH / 2), uvW, uvH)
            }
            180 -> {
                dstW = srcW; dstH = srcH
                rotatePlane180(src, 0, dst, 0, srcW, srcH)
                rotatePlane180(src, ySize, dst, dstW * dstH, uvW, uvH)
                rotatePlane180(src, ySize + uvPlaneSize, dst, dstW * dstH + uvPlaneSize, uvW, uvH)
            }
            270 -> {
                dstW = srcH; dstH = srcW
                rotatePlane270(src, 0, dst, 0, srcW, srcH)
                rotatePlane270(src, ySize, dst, dstW * dstH, uvW, uvH)
                rotatePlane270(src, ySize + uvPlaneSize, dst, dstW * dstH + (dstW / 2) * (dstH / 2), uvW, uvH)
            }
            else -> return srcW to srcH
        }
        return dstW to dstH
    }

    // 90° CW: src(x,y) → dst(h-1-y, x)
    internal fun rotatePlane90(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, w: Int, h: Int) {
        val dstW = h
        for (y in 0 until h) {
            for (x in 0 until w) {
                dst[dstOff + x * dstW + (dstW - 1 - y)] = src[srcOff + y * w + x]
            }
        }
    }

    // 270° CW: src(x,y) → dst(y, w-1-x)
    internal fun rotatePlane270(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, w: Int, h: Int) {
        val dstW = h
        for (y in 0 until h) {
            for (x in 0 until w) {
                dst[dstOff + (w - 1 - x) * dstW + y] = src[srcOff + y * w + x]
            }
        }
    }

    // 180°: src(x,y) → dst(w-1-x, h-1-y)
    internal fun rotatePlane180(src: ByteArray, srcOff: Int, dst: ByteArray, dstOff: Int, w: Int, h: Int) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                dst[dstOff + (h - 1 - y) * w + (w - 1 - x)] = src[srcOff + y * w + x]
            }
        }
    }

    // Nearest-neighbor I420 scale. Works for both up/downscale.
    fun scaleI420(src: ByteArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): ByteArray {
        if (srcW == dstW && srcH == dstH) return src
        val dst = ByteArray(dstW * dstH * 3 / 2)
        scaleI420Into(src, srcW, srcH, dst, dstW, dstH)
        return dst
    }

    // In-place version that writes into a pre-allocated dst buffer to avoid allocation per frame.
    fun scaleI420Into(src: ByteArray, srcW: Int, srcH: Int, dst: ByteArray, dstW: Int, dstH: Int) {
        if (srcW == dstW && srcH == dstH) {
            System.arraycopy(src, 0, dst, 0, src.size.coerceAtMost(dst.size))
            return
        }
        // Y plane
        for (y in 0 until dstH) {
            val sy = y * srcH / dstH
            for (x in 0 until dstW) {
                dst[y * dstW + x] = src[sy * srcW + x * srcW / dstW]
            }
        }
        // U and V planes (half size)
        val srcUOff = srcW * srcH
        val srcVOff = srcUOff + (srcW / 2) * (srcH / 2)
        val dstUOff = dstW * dstH
        val dstVOff = dstUOff + (dstW / 2) * (dstH / 2)
        val uvSrcW = srcW / 2; val uvSrcH = srcH / 2
        val uvDstW = dstW / 2; val uvDstH = dstH / 2
        for (y in 0 until uvDstH) {
            val sy = y * uvSrcH / uvDstH
            for (x in 0 until uvDstW) {
                val sx = x * uvSrcW / uvDstW
                dst[dstUOff + y * uvDstW + x] = src[srcUOff + sy * uvSrcW + sx]
                dst[dstVOff + y * uvDstW + x] = src[srcVOff + sy * uvSrcW + sx]
            }
        }
    }
}
