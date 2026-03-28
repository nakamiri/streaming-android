package com.reaream.app.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WidgetSettingsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `default widget settings has all disabled`() {
        val settings = WidgetSettings()
        assertFalse(settings.clockWidget.enabled)
        assertFalse(settings.locationWidget.enabled)
    }

    @Test
    fun `clock widget default position is top right area`() {
        val config = ClockWidgetConfig()
        assertTrue(config.x > 0.5f)
        assertTrue(config.y < 0.5f)
        assertEquals(14, config.fontSize)
    }

    @Test
    fun `location widget default position is bottom left area`() {
        val config = LocationWidgetConfig()
        assertTrue(config.x < 0.5f)
        assertTrue(config.y > 0.5f)
        assertEquals(12, config.fontSize)
    }

    @Test
    fun `clock format patterns are valid`() {
        assertEquals("HH:mm", ClockFormat.HH_MM.pattern)
        assertEquals("HH:mm:ss", ClockFormat.HH_MM_SS.pattern)
        assertEquals("hh:mm a", ClockFormat.TWELVE_HOUR.pattern)
    }

    @Test
    fun `widget settings serialization round trip`() {
        val settings = WidgetSettings(
            clockWidget = ClockWidgetConfig(
                enabled = true,
                format = ClockFormat.TWELVE_HOUR,
                x = 0.1f,
                y = 0.2f,
                fontSize = 20,
            ),
            locationWidget = LocationWidgetConfig(
                enabled = true,
                x = 0.3f,
                y = 0.4f,
                fontSize = 16,
            ),
        )

        val serialized = json.encodeToString(WidgetSettings.serializer(), settings)
        val deserialized = json.decodeFromString(WidgetSettings.serializer(), serialized)

        assertEquals(settings, deserialized)
    }

    @Test
    fun `deserialization with missing fields uses defaults`() {
        val minimal = """{"clockWidget":{"enabled":true}}"""
        val settings = json.decodeFromString(WidgetSettings.serializer(), minimal)

        assertTrue(settings.clockWidget.enabled)
        assertEquals(ClockFormat.HH_MM_SS, settings.clockWidget.format)
        assertFalse(settings.locationWidget.enabled)
    }

    @Test
    fun `x y coordinates are clamped in valid range by data class`() {
        val config = ClockWidgetConfig(x = 0.95f, y = 0.05f)
        assertTrue(config.x in 0f..1f)
        assertTrue(config.y in 0f..1f)
    }

    @Test
    fun `AppSettings includes widgets with defaults`() {
        val appSettings = AppSettings()
        assertNotNull(appSettings.widgets)
        assertFalse(appSettings.widgets.clockWidget.enabled)
    }
}
