package com.lifeos.feature.calendar.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.feature.calendar.data.CalendarSubscriptionSync
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Keeps subscribed calendars live (§Module 19). Holiday and shared feeds change
 * without warning, so they are pulled every six hours in the background rather
 * than only when the Calendar screen happens to be open.
 */
@HiltWorker
class CalendarSubscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val subscriptionSync: CalendarSubscriptionSync,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        subscriptionSync.syncAll()
        Result.success()
    } catch (t: Throwable) {
        LifeLogger.w(TAG, "Subscription sync failed", t)
        Result.retry()
    }

    companion object {
        private const val TAG = "CalendarSubscriptionWorker"
        private const val WORK_NAME = "lifeos-calendar-subscriptions"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CalendarSubscriptionWorker>(6, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build(),
            )
        }
    }
}
