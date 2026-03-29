package com.reaream.app.streaming

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Composites an ARGB overlay Bitmap onto an I420 YUV frame using alpha blending.
 */
object YuvCompositor {

    // Reused across frames to avoid per-frame IntArray allocation.
    private var pixelCache: IntArray? = null

    /**
     * Blend [overlay] onto [yuvFrame].
     * [dirtyRect] limits processing to the region actually drawn by widgets.
     * When null the entire overlay is processed (fallback, slow for large frames).
     */
    fun blendOntoI420(
        overlay: Bitmap,
        yuvFrame: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        dirtyRect: Rect? = null,
    ) {
        val overlayWidth = overlay.width
        val overlayHeight = overlay.height

        // Clamp the dirty region to overlay/frame bounds, aligned to even pixels for UV sub-sampling
        val x0 = ((dirtyRect?.left ?: 0).coerceIn(0, overlayWidth) and 1.inv())
        val y0 = ((dirtyRect?.top ?: 0).coerceIn(0, overlayHeight) and 1.inv())
        val x1 = ((dirtyRect?.right ?: overlayWidth).coerceIn(0, minOf(overlayWidth, frameWidth)) + 1) and 1.inv()
        val y1 = ((dirtyRect?.bottom ?: overlayHeight).coerceIn(0, minOf(overlayHeight, frameHeight)) + 1) and 1.inv()

        val rw = x1 - x0
        val rh = y1 - y0
        if (rw <= 0 || rh <= 0) return

        val pixelCount = rw * rh
        val pixels = pixelCache?.takeIf { it.size >= pixelCount }
            ?: IntArray(pixelCount).also { pixelCache = it }

        // Read only the dirty rect — avoids JNI overhead for the entire frame
        overlay.getPixels(pixels, 0, rw, x0, y0, rw, rh)

        val ySize = frameWidth * frameHeight
        val uvW = frameWidth / 2

        for (ry in 0 until rh) {
            val y = y0 + ry
            val rowBase = ry * rw
            for (rx in 0 until rw) {
                val argb = pixels[rowBase + rx]
                val alpha = (argb ushr 24) and 0xFF
                if (alpha == 0) continue

                val x = x0 + rx
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                // RGB to YUV BT.601
                val yVal = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val uVal = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val vVal = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                val yIdx = y * frameWidth + x
                if (alpha == 255) {
                    yuvFrame[yIdx] = yVal.coerceIn(0, 255).toByte()
                } else {
                    val invA = 255 - alpha
                    val origY = yuvFrame[yIdx].toInt() and 0xFF
                    yuvFrame[yIdx] = ((origY * invA + yVal.coerceIn(0, 255) * alpha) / 255).toByte()
                }

                if (x % 2 == 0 && y % 2 == 0) {
                    val uvX = x / 2
                    val uvY = y / 2
                    val uIdx = ySize + uvY * uvW + uvX
                    val vIdx = ySize + (uvW * (frameHeight / 2)) + uvY * uvW + uvX

                    if (alpha == 255) {
                        yuvFrame[uIdx] = uVal.coerceIn(0, 255).toByte()
                        yuvFrame[vIdx] = vVal.coerceIn(0, 255).toByte()
                    } else {
                        val invA = 255 - alpha
                        val origU = yuvFrame[uIdx].toInt() and 0xFF
                        val origV = yuvFrame[vIdx].toInt() and 0xFF
                        yuvFrame[uIdx] = ((origU * invA + uVal.coerceIn(0, 255) * alpha) / 255).toByte()
                        yuvFrame[vIdx] = ((origV * invA + vVal.coerceIn(0, 255) * alpha) / 255).toByte()
                    }
                }
            }
        }
    }
}
