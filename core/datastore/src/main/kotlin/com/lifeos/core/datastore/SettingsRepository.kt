package com.lifeos.core.datastore

import kotlinx.coroutines.flow.Flow

/** Typed access to app-level settings persisted in DataStore (§1.2). */
interface SettingsRepository {
    /** Whether the first-run onboarding checklist has been completed (§8.4). */
    val onboardingCompleted: Flow<Boolean>

    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Theme palette id: "dynamic" (wallpaper) or a fixed seed from Settings. */
    val themePalette: Flow<String>

    suspend fun setThemePalette(palette: String)

    /** World-clock zone ids shown in the Clock module (§Module 4). */
    val worldClocks: Flow<List<String>>

    suspend fun setWorldClocks(zoneIds: List<String>)

    /** Whether new local calendar events are mirrored into the system calendar. */
    val calendarMirror: Flow<Boolean>

    suspend fun setCalendarMirror(enabled: Boolean)

    /** Ordered bottom-bar tab ids (HOME always first); empty = default set. */
    val navBarItems: Flow<List<String>>

    suspend fun setNavBarItems(ids: List<String>)

    /** Home layout: false = grid (default), true = list. */
    val homeListLayout: Flow<Boolean>

    suspend fun setHomeListLayout(list: Boolean)

    /** Planner items the user dismissed ("MODULE-entityId" keys). */
    val plannerDismissed: Flow<Set<String>>

    suspend fun addPlannerDismissed(key: String)

    /** Proton Full-view ICS subscription URL (§8.6); empty = not configured. */
    val protonIcsUrl: Flow<String>

    suspend fun setProtonIcsUrl(url: String)

    /** User-arranged Home tile order (module labels); empty = default order. */
    val homeOrder: Flow<List<String>>

    suspend fun setHomeOrder(labels: List<String>)

    /** Developer option: surface Jarvis debug data (raw output, tool calls, errors). */
    val jarvisDebug: Flow<Boolean>

    suspend fun setJarvisDebug(enabled: Boolean)

    /** Mirror module data to a readable /LifeOS folder on shared storage. */
    val publicFolderMirror: Flow<Boolean>

    suspend fun setPublicFolderMirror(enabled: Boolean)

    /**
     * Whether screen-time rows have been rebuilt with the event-based
     * calculation (the first implementation stored inflated bucket totals).
     */
    val screenTimeRebuilt: Flow<Boolean>

    suspend fun setScreenTimeRebuilt(done: Boolean)

    /**
     * Defaults applied to pastes created from the Android share sheet
     * (§Module Pastebin): "EXPIRY|VISIBILITY|burn|password".
     */
    val pastebinShareDefaults: Flow<String>

    suspend fun setPastebinShareDefaults(value: String)

    /** Pastebin account user key, minted from username/password once. */
    val pastebinUserKey: Flow<String>

    suspend fun setPastebinUserKey(key: String)

    /** PrivateBin instance used for burner pastes; empty = the public default. */
    val privateBinInstance: Flow<String>

    suspend fun setPrivateBinInstance(url: String)

    /** Saved Clear Sky observing spots, one "name~lat~lon" per line. */
    val clearSkyPlaces: Flow<String>

    suspend fun setClearSkyPlaces(value: String)

    /** The Clear Sky place shown on open ("name~lat~lon"); empty = ask. */
    val clearSkyLastPlace: Flow<String>

    suspend fun setClearSkyLastPlace(value: String)
}
