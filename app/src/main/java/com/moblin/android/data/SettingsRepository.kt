package com.moblin.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moblin.android.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moblin_settings")

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settingsKey = stringPreferencesKey("app_settings")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val raw = prefs[settingsKey]
        if (raw != null) {
            try {
                json.decodeFromString<AppSettings>(raw)
            } catch (_: Exception) {
                AppSettings()
            }
        } else {
            AppSettings()
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[settingsKey]?.let {
                try { json.decodeFromString<AppSettings>(it) } catch (_: Exception) { AppSettings() }
            } ?: AppSettings()
            val updated = transform(current)
            prefs[settingsKey] = json.encodeToString(updated)
        }
    }
}
