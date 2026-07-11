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

    override val navBarItems: Flow<List<String>> =
        dataStore.data.map { prefs ->
            prefs[KEY_NAV_BAR_ITEMS]?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
        }

    override suspend fun setNavBarItems(ids: List<String>) {
        dataStore.edit { prefs -> prefs[KEY_NAV_BAR_ITEMS] = ids.joinToString("|") }
    }

    override val homeListLayout: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_HOME_LIST_LAYOUT] ?: false }

    override suspend fun setHomeListLayout(list: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_HOME_LIST_LAYOUT] = list }
    }

    override val plannerDismissed: Flow<Set<String>> =
        dataStore.data.map { prefs ->
            prefs[KEY_PLANNER_DISMISSED]?.split('|')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }

    override suspend fun addPlannerDismissed(key: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_PLANNER_DISMISSED]?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
            // Bounded: keep the most recent 200 dismissals.
            prefs[KEY_PLANNER_DISMISSED] = (current + key).takeLast(200).joinToString("|")
        }
    }

    override val protonIcsUrl: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_PROTON_ICS_URL] ?: "" }

    override suspend fun setProtonIcsUrl(url: String) {
        dataStore.edit { prefs -> prefs[KEY_PROTON_ICS_URL] = url.trim() }
    }

    override val homeOrder: Flow<List<String>> =
        dataStore.data.map { prefs ->
            prefs[KEY_HOME_ORDER]?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
        }

    override suspend fun setHomeOrder(labels: List<String>) {
        dataStore.edit { prefs -> prefs[KEY_HOME_ORDER] = labels.joinToString("|") }
    }

    override val jarvisDebug: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_JARVIS_DEBUG] ?: false }

    override suspend fun setJarvisDebug(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_JARVIS_DEBUG] = enabled }
    }

    override val publicFolderMirror: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_PUBLIC_FOLDER_MIRROR] ?: false }

    override suspend fun setPublicFolderMirror(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PUBLIC_FOLDER_MIRROR] = enabled }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_THEME_PALETTE = stringPreferencesKey("theme_palette")
        val KEY_WORLD_CLOCKS = stringPreferencesKey("world_clocks")
        val KEY_CALENDAR_MIRROR = booleanPreferencesKey("calendar_mirror")
        val KEY_NAV_BAR_ITEMS = stringPreferencesKey("nav_bar_items")
        val KEY_HOME_LIST_LAYOUT = booleanPreferencesKey("home_list_layout")
        val KEY_PLANNER_DISMISSED = stringPreferencesKey("planner_dismissed")
        val KEY_PROTON_ICS_URL = stringPreferencesKey("proton_ics_url")
        val KEY_HOME_ORDER = stringPreferencesKey("home_order")
        val KEY_JARVIS_DEBUG = booleanPreferencesKey("jarvis_debug")
        val KEY_PUBLIC_FOLDER_MIRROR = booleanPreferencesKey("public_folder_mirror")
    }
}
