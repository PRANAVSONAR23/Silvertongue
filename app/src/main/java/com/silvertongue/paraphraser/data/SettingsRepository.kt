package com.silvertongue.paraphraser.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.silvertongue.paraphraser.BuildConfig
import com.silvertongue.paraphraser.paraphrase.ProviderId
import com.silvertongue.paraphraser.paraphrase.ProviderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("silvertongue_settings")

data class OverlayPosition(val x: Int, val y: Int)

class SettingsRepository(context: Context) : ProviderPreferences {

    private val dataStore = context.applicationContext.settingsDataStore

    val activeProvider: Flow<ProviderId> = dataStore.data.map { preferences ->
        preferences[ACTIVE_PROVIDER]
            ?.let { stored -> ProviderId.entries.firstOrNull { it.name == stored } }
            ?: ProviderId.GROQ
    }

    val overlayPosition: Flow<OverlayPosition> = dataStore.data.map { preferences ->
        OverlayPosition(
            x = preferences[OVERLAY_X] ?: UNSET_POSITION,
            y = preferences[OVERLAY_Y] ?: UNSET_POSITION
        )
    }

    fun keyOverride(provider: ProviderId): Flow<String> = dataStore.data.map { preferences ->
        preferences[keyOverrideKey(provider)].orEmpty()
    }

    override suspend fun currentProvider(): ProviderId = activeProvider.first()

    override suspend fun keyFor(provider: ProviderId): String = apiKey(provider)

    suspend fun apiKey(provider: ProviderId): String {
        val override = dataStore.data.first()[keyOverrideKey(provider)].orEmpty().trim()
        return override.ifEmpty { buildTimeKey(provider) }
    }

    fun hasKey(provider: ProviderId): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[keyOverrideKey(provider)].orEmpty().isNotBlank() || buildTimeKey(provider).isNotBlank()
    }

    suspend fun setActiveProvider(provider: ProviderId) {
        dataStore.edit { it[ACTIVE_PROVIDER] = provider.name }
    }

    suspend fun setKeyOverride(provider: ProviderId, key: String) {
        dataStore.edit { preferences ->
            val trimmed = key.trim()
            if (trimmed.isEmpty()) {
                preferences.remove(keyOverrideKey(provider))
            } else {
                preferences[keyOverrideKey(provider)] = trimmed
            }
        }
    }

    suspend fun saveOverlayPosition(position: OverlayPosition) {
        dataStore.edit { preferences ->
            preferences[OVERLAY_X] = position.x
            preferences[OVERLAY_Y] = position.y
        }
    }

    private fun buildTimeKey(provider: ProviderId): String = when (provider) {
        ProviderId.GROQ -> BuildConfig.GROQ_API_KEY
        ProviderId.GEMINI -> BuildConfig.GEMINI_API_KEY
        ProviderId.ANTHROPIC -> BuildConfig.ANTHROPIC_API_KEY
    }

    private fun keyOverrideKey(provider: ProviderId) = stringPreferencesKey("api_key_${provider.name}")

    companion object {
        const val UNSET_POSITION = -1
        private val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        private val OVERLAY_X = intPreferencesKey("overlay_x")
        private val OVERLAY_Y = intPreferencesKey("overlay_y")
    }
}
