package com.reaream.app.streaming

import android.graphics.*
import android.location.Location
import com.reaream.app.data.model.ClockWidgetConfig
import com.reaream.app.data.model.LocationWidgetConfig
import com.reaream.app.data.model.WidgetSettings
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Renders widget overlays onto YUV video frames using Android Canvas.
 * Runs on the ImageAnalysis executor thread.
 */
class WidgetRenderer {

    val currentLocation = AtomicReference<Location?>(null)
    val currentAddress = AtomicReference<String?>(null)
    val currentSpeedKmh = AtomicReference(0f)

    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null

    private val textPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
    }

    private val bgPaint by lazy {
        Paint().apply {
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.FILL
        }
    }

    private val textPadding = 12f

    fun renderOntoFrame(
        yuvData: ByteArray,
        width: Int,
        height: Int,
        settings: WidgetSettings,
    ) {
        if (!settings.clockWidget.enabled && !settings.locationWidget.enabled) return

        val bitmap = ensureBitmap(width, height)
        val canvas = overlayCanvas ?: return

        bitmap.eraseColor(Color.TRANSPARENT)

        if (settings.clockWidget.enabled) {
            drawClock(canvas, width, height, settings.clockWidget)
        }

        if (settings.locationWidget.enabled) {
            drawLocation(canvas, width, height, settings.locationWidget)
        }

        YuvCompositor.blendOntoI420(bitmap, yuvData, width, height)
    }

    private fun drawClock(canvas: Canvas, w: Int, h: Int, config: ClockWidgetConfig) {
        val format = SimpleDateFormat(config.format.pattern, Locale.getDefault())
        val timeText = format.format(Date())
        drawTextWidget(canvas, w, h, timeText, config.x, config.y, config.fontSize)
    }

    private fun drawLocation(canvas: Canvas, w: Int, h: Int, config: LocationWidgetConfig) {
        val address = currentAddress.get()
        val location = currentLocation.get()
        val speed = currentSpeedKmh.get()

        val locationText = when {
            address != null -> address
            location != null -> String.format(
                Locale.US, "%.4f, %.4f", location.latitude, location.longitude
            )
            else -> return
        }

        val speedText = if (speed >= 1f) String.format(Locale.US, "%.0f km/h", speed) else null
        val text = listOfNotNull(locationText, speedText).joinToString(" | ")

        drawTextWidget(canvas, w, h, text, config.x, config.y, config.fontSize)
    }

    private fun drawTextWidget(canvas: Canvas, w: Int, h: Int, text: String, xPct: Float, yPct: Float, fontSize: Int) {
        // Scale font size relative to frame height (base: 14sp on 1280px height)
        textPaint.textSize = fontSize * (h / 1280f) * 3f

        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val textW = bounds.width().toFloat()
        val textH = bounds.height().toFloat()

        val bgW = textW + textPadding * 2
        val bgH = textH + textPadding * 2

        val x = (xPct * w).coerceIn(0f, (w - bgW).coerceAtLeast(0f))
        val y = (yPct * h).coerceIn(0f, (h - bgH).coerceAtLeast(0f))

        val bgRect = RectF(x, y, x + bgW, y + bgH)
        canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
        canvas.drawText(text, x + textPadding, y + textPadding + textH, textPaint)
    }

    private fun ensureBitmap(width: Int, height: Int): Bitmap {
        val bmp = overlayBitmap
        if (bmp != null && bmp.width == width && bmp.height == height) return bmp

        val newBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        overlayBitmap = newBmp
        overlayCanvas = Canvas(newBmp)
        return newBmp
    }

    fun release() {
        overlayBitmap?.recycle()
        overlayBitmap = null
        overlayCanvas = null
    }
}
