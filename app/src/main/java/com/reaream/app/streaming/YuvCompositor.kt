package com.reaream.app.streaming

import android.graphics.Bitmap

/**
 * Composites an ARGB overlay Bitmap onto an I420 YUV frame using alpha blending.
 */
object YuvCompositor {

    fun blendOntoI420(overlay: Bitmap, yuvFrame: ByteArray, frameWidth: Int, frameHeight: Int) {
        val overlayWidth = overlay.width
        val overlayHeight = overlay.height
        val w = minOf(overlayWidth, frameWidth)
        val h = minOf(overlayHeight, frameHeight)

        val pixels = IntArray(w)
        val ySize = frameWidth * frameHeight
        val uvW = frameWidth / 2

        for (y in 0 until h) {
            overlay.getPixels(pixels, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val argb = pixels[x]
                val alpha = (argb ushr 24) and 0xFF
                if (alpha == 0) continue

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
                    val a = alpha
                    val invA = 255 - a
                    val origY = yuvFrame[yIdx].toInt() and 0xFF
                    yuvFrame[yIdx] = ((origY * invA + yVal.coerceIn(0, 255) * a) / 255).toByte()
                }

                // UV at half resolution
                if (x % 2 == 0 && y % 2 == 0) {
                    val uvX = x / 2
                    val uvY = y / 2
                    val uIdx = ySize + uvY * uvW + uvX
                    val vIdx = ySize + (uvW * (frameHeight / 2)) + uvY * uvW + uvX

                    if (alpha == 255) {
                        yuvFrame[uIdx] = uVal.coerceIn(0, 255).toByte()
                        yuvFrame[vIdx] = vVal.coerceIn(0, 255).toByte()
                    } else {
                        val a = alpha
                        val invA = 255 - a
                        val origU = yuvFrame[uIdx].toInt() and 0xFF
                        val origV = yuvFrame[vIdx].toInt() and 0xFF
                        yuvFrame[uIdx] = ((origU * invA + uVal.coerceIn(0, 255) * a) / 255).toByte()
                        yuvFrame[vIdx] = ((origV * invA + vVal.coerceIn(0, 255) * a) / 255).toByte()
                    }
                }
            }
        }
    }
}
