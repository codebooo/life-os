package com.lifeos.feature.dhl.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.feature.dhl.data.PackagesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Periodic refresh of all active shipments (§Module 15). */
@HiltWorker
class PackagePollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val packagesRepository: PackagesRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        packagesRepository.refreshAllActive()
        Result.success()
    } catch (t: Throwable) {
        LifeLogger.w(TAG, "Package poll failed", t)
        Result.retry()
    }

    companion object {
        private const val TAG = "PackagePollWorker"
        private const val WORK_NAME = "lifeos-package-poll"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PackagePollWorker>(1, TimeUnit.HOURS).build(),
            )
        }
    }
}
