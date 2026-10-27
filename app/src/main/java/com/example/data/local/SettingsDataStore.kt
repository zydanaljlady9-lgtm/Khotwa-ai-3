package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AiModelTier
import com.example.domain.model.AiProviderType
import com.example.domain.model.SearchProviderType
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.khotwaDataStore by preferencesDataStore(name = "khotwa_ai_settings")

data class KhotwaSettingsState(
    val aiProvider: AiProviderType = AiProviderType.FIREBASE_GEMINI,
    val aiModelTier: AiModelTier = AiModelTier.AUTO,
    val customEndpointUrl: String = "",
    val searchProvider: SearchProviderType = SearchProviderType.MULTI_SOURCE_HYBRID,
    val autoWebSearchEnabled: Boolean = true,
    val parallelSearchEnabled: Boolean = true,
    val safeCacheEnabled: Boolean = true,
    val floatingOverlayEnabled: Boolean = true,
    val externalAiConsentGranted: Boolean = true,
    val screenInspectionConsentGranted: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val totalRequestsUsed: Int = 0,
    val dailySoftLimit: Int = 100,
    val lastAvgTtftMs: Long = 0L,
    val lastAvgTotalMs: Long = 0L
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL_TIER = stringPreferencesKey("ai_model_tier")
        val CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val AUTO_WEB_SEARCH = booleanPreferencesKey("auto_web_search")
        val PARALLEL_SEARCH = booleanPreferencesKey("parallel_search")
        val SAFE_CACHE = booleanPreferencesKey("safe_cache")
        val FLOATING_OVERLAY = booleanPreferencesKey("floating_overlay")
        val EXTERNAL_AI_CONSENT = booleanPreferencesKey("external_ai_consent")
        val SCREEN_CONSENT = booleanPreferencesKey("screen_inspection_consent")
        val THEME_PREF = stringPreferencesKey("theme_preference")
        val TOTAL_REQUESTS = intPreferencesKey("total_requests_used")
        val DAILY_LIMIT = intPreferencesKey("daily_soft_limit")
        val LAST_AVG_TTFT = longPreferencesKey("last_avg_ttft_ms")
        val LAST_AVG_TOTAL = longPreferencesKey("last_avg_total_ms")
    }

    val settingsFlow: Flow<KhotwaSettingsState> = context.khotwaDataStore.data.map { prefs ->
        mapPreferences(prefs)
    }

    private fun mapPreferences(prefs: Preferences): KhotwaSettingsState {
        val provider = runCatching {
            AiProviderType.valueOf(prefs[Keys.AI_PROVIDER] ?: AiProviderType.FIREBASE_GEMINI.name)
        }.getOrDefault(AiProviderType.FIREBASE_GEMINI)

        val tier = runCatching {
            AiModelTier.valueOf(prefs[Keys.AI_MODEL_TIER] ?: AiModelTier.AUTO.name)
        }.getOrDefault(AiModelTier.AUTO)

        val searchProv = runCatching {
            SearchProviderType.valueOf(
                prefs[Keys.SEARCH_PROVIDER] ?: SearchProviderType.MULTI_SOURCE_HYBRID.name
            )
        }.getOrDefault(SearchProviderType.MULTI_SOURCE_HYBRID)

        val themePref = runCatching {
            ThemePreference.valueOf(prefs[Keys.THEME_PREF] ?: ThemePreference.DARK.name)
        }.getOrDefault(ThemePreference.DARK)

        return KhotwaSettingsState(
            aiProvider = provider,
            aiModelTier = tier,
            customEndpointUrl = prefs[Keys.CUSTOM_ENDPOINT] ?: "",
            searchProvider = searchProv,
            autoWebSearchEnabled = prefs[Keys.AUTO_WEB_SEARCH] ?: true,
            parallelSearchEnabled = prefs[Keys.PARALLEL_SEARCH] ?: true,
            safeCacheEnabled = prefs[Keys.SAFE_CACHE] ?: true,
            floatingOverlayEnabled = prefs[Keys.FLOATING_OVERLAY] ?: true,
            externalAiConsentGranted = prefs[Keys.EXTERNAL_AI_CONSENT] ?: true,
            screenInspectionConsentGranted = prefs[Keys.SCREEN_CONSENT] ?: false,
            themePreference = themePref,
            totalRequestsUsed = prefs[Keys.TOTAL_REQUESTS] ?: 0,
            dailySoftLimit = prefs[Keys.DAILY_LIMIT] ?: 100,
            lastAvgTtftMs = prefs[Keys.LAST_AVG_TTFT] ?: 0L,
            lastAvgTotalMs = prefs[Keys.LAST_AVG_TOTAL] ?: 0L
        )
    }

    suspend fun updateAiProvider(provider: AiProviderType) {
        context.khotwaDataStore.edit { it[Keys.AI_PROVIDER] = provider.name }
    }

    suspend fun updateAiModelTier(tier: AiModelTier) {
        context.khotwaDataStore.edit { it[Keys.AI_MODEL_TIER] = tier.name }
    }

    suspend fun updateCustomEndpoint(url: String) {
        context.khotwaDataStore.edit { it[Keys.CUSTOM_ENDPOINT] = url.trim() }
    }

    suspend fun updateSearchProvider(provider: SearchProviderType) {
        context.khotwaDataStore.edit { it[Keys.SEARCH_PROVIDER] = provider.name }
    }

    suspend fun updateAutoWebSearch(enabled: Boolean) {
        context.khotwaDataStore.edit { it[Keys.AUTO_WEB_SEARCH] = enabled }
    }

    suspend fun updateParallelSearch(enabled: Boolean) {
        context.khotwaDataStore.edit { it[Keys.PARALLEL_SEARCH] = enabled }
    }

    suspend fun updateSafeCache(enabled: Boolean) {
        context.khotwaDataStore.edit { it[Keys.SAFE_CACHE] = enabled }
    }

    suspend fun updateFloatingOverlay(enabled: Boolean) {
        context.khotwaDataStore.edit { it[Keys.FLOATING_OVERLAY] = enabled }
    }

    suspend fun updateExternalAiConsent(granted: Boolean) {
        context.khotwaDataStore.edit { it[Keys.EXTERNAL_AI_CONSENT] = granted }
    }

    suspend fun updateScreenInspectionConsent(granted: Boolean) {
        context.khotwaDataStore.edit { it[Keys.SCREEN_CONSENT] = granted }
    }

    suspend fun updateThemePreference(theme: ThemePreference) {
        context.khotwaDataStore.edit { it[Keys.THEME_PREF] = theme.name }
    }

    suspend fun updateDailySoftLimit(limit: Int) {
        context.khotwaDataStore.edit { it[Keys.DAILY_LIMIT] = limit.coerceIn(10, 1000) }
    }

    suspend fun recordRequestMetrics(ttftMs: Long, totalMs: Long) {
        context.khotwaDataStore.edit { prefs ->
            val currentCount = prefs[Keys.TOTAL_REQUESTS] ?: 0
            val prevTtft = prefs[Keys.LAST_AVG_TTFT] ?: ttftMs
            val prevTotal = prefs[Keys.LAST_AVG_TOTAL] ?: totalMs
            prefs[Keys.TOTAL_REQUESTS] = currentCount + 1
            prefs[Keys.LAST_AVG_TTFT] = if (prevTtft == 0L) ttftMs else (prevTtft + ttftMs) / 2
            prefs[Keys.LAST_AVG_TOTAL] = if (prevTotal == 0L) totalMs else (prevTotal + totalMs) / 2
        }
    }

    suspend fun resetUsageCounters() {
        context.khotwaDataStore.edit { prefs ->
            prefs[Keys.TOTAL_REQUESTS] = 0
            prefs[Keys.LAST_AVG_TTFT] = 0L
            prefs[Keys.LAST_AVG_TOTAL] = 0L
        }
    }
}
