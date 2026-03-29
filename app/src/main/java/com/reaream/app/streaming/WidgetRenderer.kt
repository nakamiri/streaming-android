package com.reaream.app.streaming

import android.graphics.*
import android.location.Location
import com.reaream.app.data.model.ClockWidgetConfig
import com.reaream.app.data.model.LocationWidgetConfig
import com.reaream.app.data.model.MapWidgetConfig
import com.reaream.app.data.model.SpeedUnit
import com.reaream.app.data.model.SpeedWidgetConfig
import com.reaream.app.data.model.WidgetSettings
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Renders widget overlays onto YUV video frames using Android Canvas.
 * Runs on the ImageAnalysis executor thread.
 */
class WidgetRenderer {

    @Volatile var screenDensity: Float = 2.0f

    val currentLocation = AtomicReference<Location?>(null)
    val currentAddress = AtomicReference<String?>(null)
    val currentSpeedKmh = AtomicReference(0f)
    val currentMapBitmap = AtomicReference<Bitmap?>(null)

    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null

    // Accumulated bounding rect of widgets drawn this frame — passed to YuvCompositor
    // to limit blending to only the regions that actually have pixels.
    private val dirtyRect = Rect()

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
        if (!settings.clockWidget.enabled && !settings.locationWidget.enabled
            && !settings.speedWidget.enabled && !settings.mapWidget.enabled) return

        val bitmap = ensureBitmap(width, height)
        val canvas = overlayCanvas ?: return

        bitmap.eraseColor(Color.TRANSPARENT)
        dirtyRect.setEmpty()

        if (settings.mapWidget.enabled) {
            drawMap(canvas, width, height, settings.mapWidget)
        }

        if (settings.clockWidget.enabled) {
            drawClock(canvas, width, height, settings.clockWidget)
        }

        if (settings.locationWidget.enabled) {
            drawLocation(canvas, width, height, settings.locationWidget)
        }

        if (settings.speedWidget.enabled) {
            drawSpeed(canvas, width, height, settings.speedWidget)
        }

        if (!dirtyRect.isEmpty) {
            YuvCompositor.blendOntoI420(bitmap, yuvData, width, height, dirtyRect)
        }
    }

    /**
     * Renders widget overlays onto a Bitmap and returns it.
     * Returns null if no widgets are enabled or none were drawn.
     * Called from the GL thread — no YUV compositing.
     */
    fun renderOverlayBitmap(width: Int, height: Int, settings: WidgetSettings): Bitmap? {
        if (!settings.clockWidget.enabled && !settings.locationWidget.enabled
            && !settings.speedWidget.enabled && !settings.mapWidget.enabled) return null

        val bitmap = ensureBitmap(width, height)
        val canvas = overlayCanvas ?: return null

        bitmap.eraseColor(Color.TRANSPARENT)
        dirtyRect.setEmpty()

        if (settings.mapWidget.enabled) drawMap(canvas, width, height, settings.mapWidget)
        if (settings.clockWidget.enabled) drawClock(canvas, width, height, settings.clockWidget)
        if (settings.locationWidget.enabled) drawLocation(canvas, width, height, settings.locationWidget)
        if (settings.speedWidget.enabled) drawSpeed(canvas, width, height, settings.speedWidget)

        return if (!dirtyRect.isEmpty) bitmap else null
    }

    private fun drawClock(canvas: Canvas, w: Int, h: Int, config: ClockWidgetConfig) {
        val format = SimpleDateFormat(config.format.pattern, Locale.getDefault())
        val timeText = format.format(Date())
        drawTextWidget(canvas, w, h, timeText, config.x, config.y, config.fontSize)
    }

    private fun drawLocation(canvas: Canvas, w: Int, h: Int, config: LocationWidgetConfig) {
        val address = currentAddress.get()
        val location = currentLocation.get()

        val text = when {
            address != null -> address
            location != null -> String.format(
                Locale.US, "%.4f, %.4f", location.latitude, location.longitude
            )
            else -> return
        }

        drawTextWidget(canvas, w, h, text, config.x, config.y, config.fontSize)
    }

    private fun drawSpeed(canvas: Canvas, w: Int, h: Int, config: SpeedWidgetConfig) {
        val kmh = currentSpeedKmh.get()
        val value = if (config.unit == SpeedUnit.MPH) kmh * 0.621371f else kmh
        val text = String.format(Locale.US, "%.0f %s", value, config.unit.label)
        drawTextWidget(canvas, w, h, text, config.x, config.y, config.fontSize)
    }

    private fun drawMap(canvas: Canvas, w: Int, h: Int, config: MapWidgetConfig) {
        val mapBmp = currentMapBitmap.get() ?: return
        val mapSize = (config.sizeDp * screenDensity).toInt().coerceIn(50, minOf(w, h))

        val x = (config.x * w).coerceIn(0f, (w - mapSize).coerceAtLeast(0).toFloat())
        val y = (config.y * h).coerceIn(0f, (h - mapSize).coerceAtLeast(0).toFloat())

        val dst = RectF(x, y, x + mapSize, y + mapSize)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val path = android.graphics.Path()
        path.addRoundRect(dst, 12f, 12f, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(mapBmp, null, dst, null)
        canvas.restore()
        canvas.drawRoundRect(dst, 12f, 12f, borderPaint)

        dirtyRect.union(dst.left.toInt(), dst.top.toInt(), dst.right.toInt() + 1, dst.bottom.toInt() + 1)
    }

    private fun drawTextWidget(canvas: Canvas, w: Int, h: Int, text: String, xPct: Float, yPct: Float, fontSize: Int) {
        textPaint.textSize = fontSize * screenDensity

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

        dirtyRect.union(x.toInt(), y.toInt(), (x + bgW).toInt() + 1, (y + bgH).toInt() + 1)
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
