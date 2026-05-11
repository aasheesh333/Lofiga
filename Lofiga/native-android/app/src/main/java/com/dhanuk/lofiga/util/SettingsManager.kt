package com.dhanuk.lofiga.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Manages app settings via DataStore.
 */
class SettingsManager(private val context: Context) {

companion object {
        private val KEY_AUDIO_FORMAT = stringPreferencesKey("audio_format")
        private val KEY_AUDIO_BITRATE = stringPreferencesKey("audio_bitrate")
        private val KEY_EXPORT_PATH = stringPreferencesKey("export_path")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")

        const val DEFAULT_FORMAT = "m4a"
        const val DEFAULT_BITRATE = "320k"
    }

    data class AppSettings(
        val audioFormat: String = DEFAULT_FORMAT,
        val audioBitrate: String = DEFAULT_BITRATE,
        val exportPath: String = "",
        val isDarkMode: Boolean = true,
        val hasSeenOnboarding: Boolean = false
    )

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            audioFormat = prefs[KEY_AUDIO_FORMAT] ?: DEFAULT_FORMAT,
            audioBitrate = prefs[KEY_AUDIO_BITRATE] ?: DEFAULT_BITRATE,
            exportPath = prefs[KEY_EXPORT_PATH] ?: "",
            isDarkMode = prefs[KEY_DARK_MODE] ?: true,
            hasSeenOnboarding = prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
        )
    }

    suspend fun updateFormat(format: String) {
        context.dataStore.edit { it[KEY_AUDIO_FORMAT] = format }
    }

    suspend fun updateBitrate(bitrate: String) {
        context.dataStore.edit { it[KEY_AUDIO_BITRATE] = bitrate }
    }

    suspend fun updateExportPath(path: String) {
        context.dataStore.edit { it[KEY_EXPORT_PATH] = path }
    }

    suspend fun updateDarkMode(dark: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = dark }
    }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        context.dataStore.edit { it[KEY_HAS_SEEN_ONBOARDING] = seen }
    }

    suspend fun resetOnboarding() {
        context.dataStore.edit { it[KEY_HAS_SEEN_ONBOARDING] = false }
    }
}