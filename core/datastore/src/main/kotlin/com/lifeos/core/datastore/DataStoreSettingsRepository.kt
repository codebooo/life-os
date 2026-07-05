package com.lifeos.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_COMPLETED] = completed }
    }

    override val themePalette: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_THEME_PALETTE] ?: "dynamic" }

    override suspend fun setThemePalette(palette: String) {
        dataStore.edit { prefs -> prefs[KEY_THEME_PALETTE] = palette }
    }

    override val worldClocks: Flow<List<String>> =
        dataStore.data.map { prefs ->
            prefs[KEY_WORLD_CLOCKS]?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
        }

    override suspend fun setWorldClocks(zoneIds: List<String>) {
        dataStore.edit { prefs -> prefs[KEY_WORLD_CLOCKS] = zoneIds.joinToString("|") }
    }

    override val calendarMirror: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_CALENDAR_MIRROR] ?: false }

    override suspend fun setCalendarMirror(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_CALENDAR_MIRROR] = enabled }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_THEME_PALETTE = stringPreferencesKey("theme_palette")
        val KEY_WORLD_CLOCKS = stringPreferencesKey("world_clocks")
        val KEY_CALENDAR_MIRROR = booleanPreferencesKey("calendar_mirror")
    }
}
