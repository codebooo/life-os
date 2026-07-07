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
}
