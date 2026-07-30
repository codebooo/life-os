package com.lifeos.feature.sync.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.ActionEcho
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Backup health as Jarvis reads it. */
internal class SyncProvider @Inject constructor(
    private val backupService: BackupService,
) : LifeDataProvider {

    override val topic: String = "backups"
    override val description: String = "when LifeOS was last backed up and whether it verified"

    override suspend fun read(query: String?): String {
        val runs = backupService.history(6)
        if (runs.isEmpty()) return "No backup has ever run. Set a passphrase in Sync and back up once."
        return buildString {
            val latest = runs.first()
            appendLine(
                "Last backup ${AT.format(Date(latest.at))}: ${latest.status}" +
                    if (latest.detail.isBlank()) "" else " (${latest.detail})",
            )
            appendLine("File ${latest.fileName}, ${latest.sizeBytes / 1024} KB, to ${latest.destination}")
            appendLine("Local snapshots kept: ${backupService.localBackups().size}")
            appendLine("History:")
            runs.forEach { appendLine("- ${AT.format(Date(it.at))} ${it.status} ${it.fileName}") }
        }.trim()
    }

    private companion object {
        val AT = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
    }
}

internal class SyncActionHandler @Inject constructor(
    private val backupService: BackupService,
    private val echo: ActionEcho,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.BackupNow

    override suspend fun execute(action: LifeAction): LifeResult<Long?> =
        backupService.backupNow().fold(
            onSuccess = { outcome ->
                echo.fileName(outcome.fileName)
                LifeResult.Success(outcome.sizeBytes)
            },
            onFailure = { LifeResult.Failure(LifeError.Unknown(it.message ?: "Backup failed")) },
        )
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: SyncProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: SyncActionHandler): LifeActionHandler
}
