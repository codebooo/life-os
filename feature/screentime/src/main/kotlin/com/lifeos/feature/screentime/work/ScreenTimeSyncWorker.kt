package com.lifeos.feature.screentime.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.feature.screentime.data.ScreenTimeCollector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Keeps the screen-time archive complete without the module ever being opened
 * (§Module Screen Time).
 *
 * Android purges raw usage events after roughly a month, so a phone that goes
 * weeks between visits to this screen would silently lose the gap. This worker
 * runs every few hours and re-derives the recent window, which means the local
 * copy is always ahead of the system's retention.
 */
@HiltWorker
class ScreenTimeSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val collector: ScreenTimeCollector,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        if (!collector.hasPermission()) {
            // Nothing to harvest yet; retrying would just burn wake-ups.
            Result.success()
        } else {
            collector.sync(days = 45)
            Result.success()
        }
    } catch (t: Throwable) {
        LifeLogger.w(TAG, "Screen-time sync failed", t)
        Result.retry()
    }

    companion object {
        private const val TAG = "ScreenTimeSyncWorker"
        private const val WORK_NAME = "lifeos-screentime-sync"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // UPDATE, not KEEP: an interval change has to take effect on upgrade.
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ScreenTimeSyncWorker>(4, TimeUnit.HOURS).build(),
            )
        }
    }
}
