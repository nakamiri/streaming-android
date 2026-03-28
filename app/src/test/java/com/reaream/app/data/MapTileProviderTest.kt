package com.reaream.app.data

import org.junit.Assert.*
import org.junit.Test

class MapTileProviderTest {

    @Test
    fun `lngToTileX calculates correct tile for Tokyo at zoom 15`() {
        // Tokyo: lng=139.767
        val tileX = MapTileProvider.lngToTileX(139.767, 15)
        // At zoom 15, Tokyo should be around tile 29105
        assertTrue(tileX in 29000..29200)
    }

    @Test
    fun `latToTileY calculates correct tile for Tokyo at zoom 15`() {
        // Tokyo: lat=35.6814
        val tileY = MapTileProvider.latToTileY(35.6814, 15)
        // At zoom 15, Tokyo latitude should be around tile 12903
        assertTrue(tileY in 12800..13000)
    }

    @Test
    fun `lngToTileXFrac returns fractional tile coordinate`() {
        val frac = MapTileProvider.lngToTileXFrac(139.767, 15)
        val intPart = MapTileProvider.lngToTileX(139.767, 15)
        // Fractional should be close to integer part
        assertEquals(intPart.toDouble(), frac, 1.0)
        // Fractional should have decimal part
        assertTrue(frac > intPart - 0.5)
    }

    @Test
    fun `latToTileYFrac returns fractional tile coordinate`() {
        val frac = MapTileProvider.latToTileYFrac(35.6814, 15)
        val intPart = MapTileProvider.latToTileY(35.6814, 15)
        assertEquals(intPart.toDouble(), frac, 1.0)
    }

    @Test
    fun `tile coordinates at zoom 0 are 0`() {
        assertEquals(0, MapTileProvider.lngToTileX(0.0, 0))
        assertEquals(0, MapTileProvider.latToTileY(0.0, 0))
    }

    @Test
    fun `tile coordinates at zoom 1`() {
        // At zoom 1, there are 2x2 tiles
        // lng=0 should be tile 1 (center)
        assertEquals(1, MapTileProvider.lngToTileX(0.0, 1))
        // lat=0 should be tile 1 (center)
        assertEquals(1, MapTileProvider.latToTileY(0.0, 1))
    }

    @Test
    fun `negative longitude maps correctly`() {
        // New York: lng=-73.9857
        val tileX = MapTileProvider.lngToTileX(-73.9857, 15)
        assertTrue(tileX in 9600..9700)
    }

    @Test
    fun `negative latitude maps correctly`() {
        // Sydney: lat=-33.8688
        val tileY = MapTileProvider.latToTileY(-33.8688, 15)
        assertTrue(tileY in 19600..19700)
    }

    @Test
    fun `higher zoom produces larger tile coordinates`() {
        val tileX14 = MapTileProvider.lngToTileX(139.767, 14)
        val tileX15 = MapTileProvider.lngToTileX(139.767, 15)
        // Zoom 15 tile should be roughly 2x zoom 14
        assertEquals(tileX14 * 2.0, tileX15.toDouble(), 2.0)
    }
}
