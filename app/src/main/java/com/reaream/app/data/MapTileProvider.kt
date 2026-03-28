package com.reaream.app.data

import android.graphics.*
import android.location.Location
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URL
import kotlin.math.*

/**
 * Fetches OpenStreetMap raster tiles for the current location.
 * Caches the tile bitmap and only re-fetches when location changes significantly.
 */
class MapTileProvider {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _mapBitmap = MutableStateFlow<Bitmap?>(null)
    val mapBitmap: StateFlow<Bitmap?> = _mapBitmap.asStateFlow()

    private var lastFetchLat = 0.0
    private var lastFetchLng = 0.0
    private var lastFetchZoom = 0
    private var fetching = false

    fun updateLocation(location: Location, zoom: Int) {
        // Only re-fetch if moved significantly (~100m) or zoom changed
        val dist = if (lastFetchLat != 0.0) {
            val prev = Location("").apply { latitude = lastFetchLat; longitude = lastFetchLng }
            prev.distanceTo(location)
        } else Float.MAX_VALUE

        if (dist < 100f && lastFetchZoom == zoom && _mapBitmap.value != null) return
        if (fetching) return

        lastFetchLat = location.latitude
        lastFetchLng = location.longitude
        lastFetchZoom = zoom
        fetching = true

        scope.launch {
            try {
                val bitmap = fetchTileWithMarker(location.latitude, location.longitude, zoom)
                _mapBitmap.value = bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch map tile", e)
            } finally {
                fetching = false
            }
        }
    }

    private fun fetchTileWithMarker(lat: Double, lng: Double, zoom: Int): Bitmap {
        // Calculate tile coordinates
        val tileX = lngToTileX(lng, zoom)
        val tileY = latToTileY(lat, zoom)

        // Fetch 3x3 grid of tiles for better coverage
        val combinedSize = 256 * 3
        val combined = Bitmap.createBitmap(combinedSize, combinedSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)

        for (dy in -1..1) {
            for (dx in -1..1) {
                val tx = tileX + dx
                val ty = tileY + dy
                try {
                    val tile = fetchTile(tx, ty, zoom)
                    canvas.drawBitmap(tile, ((dx + 1) * 256).toFloat(), ((dy + 1) * 256).toFloat(), null)
                    if (tile != combined) tile.recycle()
                } catch (e: Exception) {
                    // Fill with gray if tile fails
                    val paint = Paint().apply { color = Color.LTGRAY }
                    canvas.drawRect(
                        ((dx + 1) * 256).toFloat(), ((dy + 1) * 256).toFloat(),
                        ((dx + 2) * 256).toFloat(), ((dy + 2) * 256).toFloat(), paint
                    )
                }
            }
        }

        // Calculate pixel position of the marker within the 3x3 grid
        val fracX = lngToTileXFrac(lng, zoom) - tileX
        val fracY = latToTileYFrac(lat, zoom) - tileY
        val markerX = ((fracX + 1) * 256).toFloat() // +1 for the offset of center tile
        val markerY = ((fracY + 1) * 256).toFloat()

        // Draw marker
        drawMarker(canvas, markerX, markerY)

        // Crop to center portion (the visible map area)
        val cropSize = 256 * 2
        val cropX = (combinedSize - cropSize) / 2
        val cropY = (combinedSize - cropSize) / 2
        val cropped = Bitmap.createBitmap(combined, cropX, cropY, cropSize, cropSize)
        if (cropped != combined) combined.recycle()

        return cropped
    }

    private fun fetchTile(x: Int, y: Int, zoom: Int): Bitmap {
        val url = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
        val connection = URL(url).openConnection().apply {
            setRequestProperty("User-Agent", "Reaream/0.1 (Android streaming app)")
            connectTimeout = 5000
            readTimeout = 5000
        }
        return BitmapFactory.decodeStream(connection.getInputStream())
            ?: throw Exception("Failed to decode tile")
    }

    private fun drawMarker(canvas: Canvas, x: Float, y: Float) {
        // Blue circle with white border
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 150, 243) // Material Blue
            style = Paint.Style.FILL
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y + 2f, 14f, shadowPaint)
        canvas.drawCircle(x, y, 12f, outerPaint)
        canvas.drawCircle(x, y, 8f, innerPaint)
    }

    fun release() {
        scope.cancel()
        _mapBitmap.value?.recycle()
        _mapBitmap.value = null
    }

    companion object {
        private const val TAG = "MapTileProvider"

        fun lngToTileX(lng: Double, zoom: Int): Int {
            return ((lng + 180.0) / 360.0 * (1 shl zoom)).toInt()
        }

        fun latToTileY(lat: Double, zoom: Int): Int {
            val latRad = Math.toRadians(lat)
            return ((1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * (1 shl zoom)).toInt()
        }

        fun lngToTileXFrac(lng: Double, zoom: Int): Double {
            return (lng + 180.0) / 360.0 * (1 shl zoom)
        }

        fun latToTileYFrac(lat: Double, zoom: Int): Double {
            val latRad = Math.toRadians(lat)
            return (1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * (1 shl zoom)
        }
    }
}
