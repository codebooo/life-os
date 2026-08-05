package com.lifeos.feature.calendar.data

import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.calendar.CalendarDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live-syncing calendar subscriptions (§Module 19).
 *
 * Any `webcal://`/`https://` ICS feed — public holidays, a shared work
 * calendar, a sports schedule — becomes a read-only coloured calendar that is
 * refreshed on open and on a schedule. Each refresh replaces the feed's events
 * wholesale, so cancellations upstream disappear here too, and events are keyed
 * by their ICS UID so nothing duplicates.
 */
@Singleton
class CalendarSubscriptionSync @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val calendarDao: CalendarDao,
    private val calendarRepository: CalendarRepository,
) {

    /** Refreshes every subscription; returns how many events were written. */
    suspend fun syncAll(): LifeResult<Int> = withContext(Dispatchers.IO) {
        val subscriptions = calendarDao.subscriptions()
        if (subscriptions.isEmpty()) {
            return@withContext LifeResult.Failure(
                LifeError.Validation("No subscribed calendars yet — add an ICS link first"),
            )
        }
        var total = 0
        val failures = mutableListOf<String>()
        subscriptions.forEach { calendar ->
            when (val result = syncOne(calendar.id)) {
                is LifeResult.Success -> total += result.value
                is LifeResult.Failure -> failures += "${calendar.name}: ${result.error.message}"
            }
        }
        if (failures.isNotEmpty() && total == 0) {
            LifeResult.Failure(LifeError.Network(failures.joinToString("; ")))
        } else {
            LifeResult.Success(total)
        }
    }

    suspend fun syncOne(calendarId: Long): LifeResult<Int> = withContext(Dispatchers.IO) {
        val calendar = calendarDao.calendar(calendarId)
            ?: return@withContext LifeResult.Failure(LifeError.NotFound("Calendar is gone"))
        val url = calendar.subscriptionUrl
            ?: return@withContext LifeResult.Failure(
                LifeError.Validation("\"${calendar.name}\" is a local calendar, not a subscription"),
            )
        val ics = try {
            okHttpClient.newCall(Request.Builder().url(normalise(url)).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LifeResult.Failure(
                        LifeError.Network("${calendar.name}: HTTP ${response.code}"),
                    )
                }
                response.body.string()
            }
        } catch (t: Throwable) {
            LifeLogger.w(TAG, "Subscription fetch failed for ${calendar.name}", t)
            return@withContext LifeResult.Failure(LifeError.Network("Fetch failed: ${t.message}", t))
        }
        val parsed = IcsCodec.parse(ics)
        if (parsed.isEmpty()) {
            return@withContext LifeResult.Failure(
                LifeError.Validation("${calendar.name}: the feed held no events"),
            )
        }
        val result = calendarRepository.importParsed(parsed, calendarId, replaceCalendar = true)
        if (result is LifeResult.Success) calendarDao.markSynced(calendarId, System.currentTimeMillis())
        result
    }

    /** Calendar links are handed out as webcal:// but fetched over HTTPS. */
    private fun normalise(url: String): String = url.trim()
        .replace(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")

    private companion object {
        const val TAG = "CalendarSubscriptionSync"
    }
}
