package com.lifeos.core.datastore

import kotlinx.coroutines.flow.Flow

/** Typed access to app-level settings persisted in DataStore (§1.2). */
interface SettingsRepository {
    /** Whether the first-run onboarding checklist has been completed (§8.4). */
    val onboardingCompleted: Flow<Boolean>

    suspend fun setOnboardingCompleted(completed: Boolean)
}
