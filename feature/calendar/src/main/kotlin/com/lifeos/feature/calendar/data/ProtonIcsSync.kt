package com.lifeos.feature.calendar.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.datastore.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proton Calendar one-way import (§8.6): fetch the user's Full-view ICS share
 * link (the URL carries the decryption key, so treat it like a secret) and
 * merge new events into the local calendar. Proton offers no two-way API —
 * export back is via the shareable ICS document.
 */
@Singleton
class ProtonIcsSync @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
) {

    suspend fun sync(): LifeResult<Int> = withContext(Dispatchers.IO) {
        val url = settingsRepository.protonIcsUrl.first()
        if (url.isBlank()) {
            return@withContext LifeResult.Failure(
                LifeError.Validation("No Proton ICS link set — paste your calendar's Full-view share link first"),
            )
        }
        val ics = try {
            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LifeResult.Failure(
                        LifeError.Network("Proton returned HTTP ${response.code} — re-check the share link"),
                    )
                }
                response.body.string()
            }
        } catch (t: Throwable) {
            return@withContext LifeResult.Failure(LifeError.Network("Fetch failed: ${t.message}", t))
        }
        val parsed = IcsCodec.parse(ics)
        if (parsed.isEmpty()) {
            return@withContext LifeResult.Failure(
                LifeError.Validation("No events found — is this the Full-view ICS link?"),
            )
        }
        calendarRepository.importParsed(parsed)
    }
}
