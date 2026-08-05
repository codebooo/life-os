package com.lifeos.feature.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.datastore.SettingsRepository
import com.lifeos.feature.sync.data.BackupService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Daily unattended backup (§Module Sync). Runs only once a passphrase exists —
 * without one there is nothing to encrypt with, and a silent plaintext dump is
 * not a trade this app makes.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupService: BackupService,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        if (settingsRepository.backupPassphrase.first().isBlank()) {
            Result.success()
        } else {
            backupService.backupNow().fold(
                onSuccess = { Result.success() },
                onFailure = {
                    LifeLogger.w(TAG, "Auto backup failed", it)
                    Result.retry()
                },
            )
        }
    } catch (t: Throwable) {
        LifeLogger.w(TAG, "Auto backup crashed", t)
        Result.retry()
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val WORK_NAME = "lifeos-auto-backup"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
