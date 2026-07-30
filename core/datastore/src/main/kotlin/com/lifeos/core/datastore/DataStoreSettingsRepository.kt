package com.lifeos.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    override val screenTimeRebuilt: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_SCREEN_TIME_REBUILT] ?: false }

    override suspend fun setScreenTimeRebuilt(done: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SCREEN_TIME_REBUILT] = done }
    }

    override val pastebinShareDefaults: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_PASTEBIN_SHARE_DEFAULTS] ?: "" }

    override suspend fun setPastebinShareDefaults(value: String) {
        dataStore.edit { prefs -> prefs[KEY_PASTEBIN_SHARE_DEFAULTS] = value }
    }

    override val pastebinUserKey: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_PASTEBIN_USER_KEY] ?: "" }

    override suspend fun setPastebinUserKey(key: String) {
        dataStore.edit { prefs -> prefs[KEY_PASTEBIN_USER_KEY] = key.trim() }
    }

    override val privateBinInstance: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_PRIVATEBIN_INSTANCE] ?: "" }

    override suspend fun setPrivateBinInstance(url: String) {
        dataStore.edit { prefs -> prefs[KEY_PRIVATEBIN_INSTANCE] = url.trim() }
    }

    override val backupPassphrase: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_BACKUP_PASSPHRASE] ?: "" }

    override suspend fun setBackupPassphrase(value: String) {
        dataStore.edit { prefs -> prefs[KEY_BACKUP_PASSPHRASE] = value }
    }

    override val backupWebdavUrl: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_BACKUP_WEBDAV_URL] ?: "" }

    override suspend fun setBackupWebdavUrl(value: String) {
        dataStore.edit { prefs -> prefs[KEY_BACKUP_WEBDAV_URL] = value.trim() }
    }

    override val backupWebdavUser: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_BACKUP_WEBDAV_USER] ?: "" }

    override suspend fun setBackupWebdavUser(value: String) {
        dataStore.edit { prefs -> prefs[KEY_BACKUP_WEBDAV_USER] = value.trim() }
    }

    override val backupWebdavPassword: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_BACKUP_WEBDAV_PASSWORD] ?: "" }

    override suspend fun setBackupWebdavPassword(value: String) {
        dataStore.edit { prefs -> prefs[KEY_BACKUP_WEBDAV_PASSWORD] = value }
    }

    override val backupKeepGenerations: Flow<Int> =
        dataStore.data.map { prefs -> prefs[KEY_BACKUP_KEEP] ?: 5 }

    override suspend fun setBackupKeepGenerations(value: Int) {
        dataStore.edit { prefs -> prefs[KEY_BACKUP_KEEP] = value.coerceIn(1, 30) }
    }

    override val clearSkyPlaces: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_CLEAR_SKY_PLACES] ?: "" }

    override suspend fun setClearSkyPlaces(value: String) {
        dataStore.edit { prefs -> prefs[KEY_CLEAR_SKY_PLACES] = value }
    }

    override val clearSkyLastPlace: Flow<String> =
        dataStore.data.map { prefs -> prefs[KEY_CLEAR_SKY_LAST_PLACE] ?: "" }

    override suspend fun setClearSkyLastPlace(value: String) {
        dataStore.edit { prefs -> prefs[KEY_CLEAR_SKY_LAST_PLACE] = value }
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
        val KEY_SCREEN_TIME_REBUILT = booleanPreferencesKey("screen_time_rebuilt_v2")
        val KEY_PASTEBIN_SHARE_DEFAULTS = stringPreferencesKey("pastebin_share_defaults")
        val KEY_PASTEBIN_USER_KEY = stringPreferencesKey("pastebin_user_key")
        val KEY_PRIVATEBIN_INSTANCE = stringPreferencesKey("privatebin_instance")
        val KEY_BACKUP_PASSPHRASE = stringPreferencesKey("backup_passphrase")
        val KEY_BACKUP_WEBDAV_URL = stringPreferencesKey("backup_webdav_url")
        val KEY_BACKUP_WEBDAV_USER = stringPreferencesKey("backup_webdav_user")
        val KEY_BACKUP_WEBDAV_PASSWORD = stringPreferencesKey("backup_webdav_password")
        val KEY_BACKUP_KEEP = intPreferencesKey("backup_keep_generations")
        val KEY_CLEAR_SKY_PLACES = stringPreferencesKey("clear_sky_places")
        val KEY_CLEAR_SKY_LAST_PLACE = stringPreferencesKey("clear_sky_last_place")
    }
}
