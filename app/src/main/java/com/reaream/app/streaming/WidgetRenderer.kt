package com.reaream.app.streaming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import com.reaream.app.data.model.ClockWidgetConfig
import com.reaream.app.data.model.LocationWidgetConfig
import com.reaream.app.data.model.MapWidgetConfig
import com.reaream.app.data.model.SpeedUnit
import com.reaream.app.data.model.SpeedWidgetConfig
import com.reaream.app.data.model.WidgetSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Renders widget overlays onto frames.
 * Canvas rendering happens on the GL thread when streaming is active.
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
    private val textBounds = Rect()
    private val mapClipPath = Path()

    private var cachedClockPattern: String? = null
    private var cachedClockFormatter: SimpleDateFormat? = null
    private var lastOverlayKey: OverlayKey? = null
    private var lastOverlayVisible = false
    private val overlayVersionCounter = AtomicInteger(0)

    val overlayVersion: Int
        get() = overlayVersionCounter.get()

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

    private val borderPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
    }

    private val textPadding = 12f

    fun renderOntoFrame(
        yuvData: ByteArray,
        width: Int,
        height: Int,
        settings: WidgetSettings,
    ) {
        val overlay = renderOverlayBitmap(width, height, settings) ?: return
        YuvCompositor.blendOntoI420(overlay, yuvData, width, height, dirtyRect)
    }

    /**
     * Renders widget overlays onto a Bitmap and returns it.
     * Returns null if no widgets are enabled or none were drawn.
     */
    fun renderOverlayBitmap(width: Int, height: Int, settings: WidgetSettings): Bitmap? {
        if (!hasAnyEnabledWidget(settings)) {
            if (lastOverlayVisible || lastOverlayKey != null) {
                overlayVersionCounter.incrementAndGet()
            }
            lastOverlayKey = null
            lastOverlayVisible = false
            return null
        }

        val overlayData = buildOverlayData(width, height, settings)
        val bitmap = ensureBitmap(width, height)
        if (overlayData.key == lastOverlayKey) {
            return if (lastOverlayVisible) bitmap else null
        }

        lastOverlayKey = overlayData.key
        bitmap.eraseColor(Color.TRANSPARENT)
        dirtyRect.setEmpty()

        overlayData.mapBitmap?.let { mapBmp ->
            drawMap(overlayCanvas ?: return null, width, height, settings.mapWidget, mapBmp)
        }
        overlayData.clockText?.let { text ->
            drawTextWidget(overlayCanvas ?: return null, width, height, text, settings.clockWidget.x, settings.clockWidget.y, settings.clockWidget.fontSize)
        }
        overlayData.locationText?.let { text ->
            drawTextWidget(overlayCanvas ?: return null, width, height, text, settings.locationWidget.x, settings.locationWidget.y, settings.locationWidget.fontSize)
        }
        overlayData.speedText?.let { text ->
            drawTextWidget(overlayCanvas ?: return null, width, height, text, settings.speedWidget.x, settings.speedWidget.y, settings.speedWidget.fontSize)
        }

        lastOverlayVisible = !dirtyRect.isEmpty
        overlayVersionCounter.incrementAndGet()
        return if (lastOverlayVisible) bitmap else null
    }

    private fun hasAnyEnabledWidget(settings: WidgetSettings): Boolean {
        return settings.clockWidget.enabled || settings.locationWidget.enabled ||
            settings.speedWidget.enabled || settings.mapWidget.enabled
    }

    private fun buildOverlayData(width: Int, height: Int, settings: WidgetSettings): OverlayData {
        val clockText = if (settings.clockWidget.enabled) {
            formatClockText(settings.clockWidget)
        } else {
            null
        }
        val locationText = if (settings.locationWidget.enabled) {
            buildLocationText()
        } else {
            null
        }
        val speedText = if (settings.speedWidget.enabled) {
            buildSpeedText(settings.speedWidget)
        } else {
            null
        }
        val mapBitmap = if (settings.mapWidget.enabled) {
            currentMapBitmap.get()?.takeUnless { it.isRecycled }
        } else {
            null
        }

        return OverlayData(
            clockText = clockText,
            locationText = locationText,
            speedText = speedText,
            mapBitmap = mapBitmap,
            key = OverlayKey(
                width = width,
                height = height,
                densityBits = screenDensity.toRawBits(),
                settingsHash = settings.hashCode(),
                clockText = clockText,
                locationText = locationText,
                speedText = speedText,
                mapIdentity = System.identityHashCode(mapBitmap),
                mapGeneration = mapBitmap?.generationId ?: -1,
            ),
        )
    }

    private fun formatClockText(config: ClockWidgetConfig): String {
        val pattern = config.format.pattern
        val formatter = if (cachedClockPattern == pattern) {
            cachedClockFormatter
        } else {
            SimpleDateFormat(pattern, Locale.getDefault()).also {
                cachedClockPattern = pattern
                cachedClockFormatter = it
            }
        } ?: SimpleDateFormat(pattern, Locale.getDefault()).also {
            cachedClockPattern = pattern
            cachedClockFormatter = it
        }
        return formatter.format(Date())
    }

    private fun buildLocationText(): String? {
        val address = currentAddress.get()
        val location = currentLocation.get()
        return when {
            address != null -> address
            location != null -> String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
            else -> null
        }
    }

    private fun buildSpeedText(config: SpeedWidgetConfig): String {
        val kmh = currentSpeedKmh.get()
        val value = if (config.unit == SpeedUnit.MPH) kmh * 0.621371f else kmh
        return String.format(Locale.US, "%.0f %s", value, config.unit.label)
    }

    private fun drawMap(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: MapWidgetConfig,
        mapBitmap: Bitmap,
    ) {
        val mapSize = (config.sizeDp * screenDensity).toInt().coerceIn(50, minOf(width, height))

        val x = (config.x * width).coerceIn(0f, (width - mapSize).coerceAtLeast(0).toFloat())
        val y = (config.y * height).coerceIn(0f, (height - mapSize).coerceAtLeast(0).toFloat())
        val dst = RectF(x, y, x + mapSize, y + mapSize)

        mapClipPath.reset()
        mapClipPath.addRoundRect(dst, 12f, 12f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(mapClipPath)
        canvas.drawBitmap(mapBitmap, null, dst, null)
        canvas.restore()
        canvas.drawRoundRect(dst, 12f, 12f, borderPaint)

        dirtyRect.union(dst.left.toInt(), dst.top.toInt(), dst.right.toInt() + 1, dst.bottom.toInt() + 1)
    }

    private fun drawTextWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
        text: String,
        xPct: Float,
        yPct: Float,
        fontSize: Int,
    ) {
        textPaint.textSize = fontSize * screenDensity

        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textW = textBounds.width().toFloat()
        val textH = textBounds.height().toFloat()

        val bgW = textW + textPadding * 2
        val bgH = textH + textPadding * 2

        val x = (xPct * width).coerceIn(0f, (width - bgW).coerceAtLeast(0f))
        val y = (yPct * height).coerceIn(0f, (height - bgH).coerceAtLeast(0f))

        val bgRect = RectF(x, y, x + bgW, y + bgH)
        canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
        canvas.drawText(text, x + textPadding, y + textPadding + textH, textPaint)

        dirtyRect.union(x.toInt(), y.toInt(), (x + bgW).toInt() + 1, (y + bgH).toInt() + 1)
    }

    private fun ensureBitmap(width: Int, height: Int): Bitmap {
        val current = overlayBitmap
        if (current != null && current.width == width && current.height == height) return current

        overlayBitmap?.recycle()
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        overlayBitmap = newBitmap
        overlayCanvas = Canvas(newBitmap)
        lastOverlayKey = null
        lastOverlayVisible = false
        overlayVersionCounter.incrementAndGet()
        return newBitmap
    }

    fun release() {
        overlayBitmap?.recycle()
        overlayBitmap = null
        overlayCanvas = null
        lastOverlayKey = null
        lastOverlayVisible = false
        overlayVersionCounter.incrementAndGet()
    }

    private data class OverlayData(
        val clockText: String?,
        val locationText: String?,
        val speedText: String?,
        val mapBitmap: Bitmap?,
        val key: OverlayKey,
    )

    private data class OverlayKey(
        val width: Int,
        val height: Int,
        val densityBits: Int,
        val settingsHash: Int,
        val clockText: String?,
        val locationText: String?,
        val speedText: String?,
        val mapIdentity: Int,
        val mapGeneration: Int,
    )
}
