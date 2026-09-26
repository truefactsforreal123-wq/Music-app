package com.aura.player.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Context.dataStore by preferencesDataStore(name = "aura_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val THEME = stringPreferencesKey("theme")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val RECENT_URLS = stringSetPreferencesKey("recent_urls")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    companion object {
        // Sensible default for the user's LAN; editable in Settings at any time.
        const val DEFAULT_SERVER_URL = "http://192.168.1.8:8787"
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { it[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL }

    /** Blocking-safe parse used by the OkHttp interceptor (runs on an IO thread). */
    fun serverUrlHttp(): HttpUrl {
        val raw = kotlinx.coroutines.runBlocking { serverUrl.first() }
        return raw.toHttpUrlOrNull() ?: DEFAULT_SERVER_URL.toHttpUrlOrNull()!!
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        when (it[Keys.THEME]) {
            "dark" -> ThemeMode.DARK
            "light" -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.WIFI_ONLY] ?: true }
    val skipSilence: Flow<Boolean> = context.dataStore.data.map { it[Keys.SKIP_SILENCE] ?: false }
    val recentUrls: Flow<Set<String>> = context.dataStore.data.map { it[Keys.RECENT_URLS] ?: emptySet() }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url.trim() }
    }

    /** Remembers fetched playlist URLs for the Import screen's "Recent links". */
    suspend fun addRecentUrl(url: String) {
        if (url.isBlank()) return
        context.dataStore.edit {
            val recent = it[Keys.RECENT_URLS] ?: emptySet()
            it[Keys.RECENT_URLS] = (setOf(url.trim()) + recent).take(8).toSet()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = when (mode) {
        ThemeMode.DARK -> "dark"
        ThemeMode.LIGHT -> "light"
        ThemeMode.SYSTEM -> "system"
    } }

    suspend fun setWifiOnly(value: Boolean) = context.dataStore.edit { it[Keys.WIFI_ONLY] = value }
    suspend fun setSkipSilence(value: Boolean) = context.dataStore.edit { it[Keys.SKIP_SILENCE] = value }

    suspend fun markOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
}
