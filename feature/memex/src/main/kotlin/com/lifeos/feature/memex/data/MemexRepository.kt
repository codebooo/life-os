package com.lifeos.feature.memex.data

import android.util.Patterns
import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.memex.ArchiveItemEntity
import com.lifeos.core.database.memex.MemexDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Personal Memex (§Module 22, [src 12]): a private archive of what you read
 * and clip. Alpha scope: share-ins and manual clips; un-annotated items
 * auto-expire after [RETENTION_DAYS] while annotated ones persist.
 */
@Singleton
class MemexRepository @Inject constructor(
    private val memexDao: MemexDao,
) {

    fun observe(query: String): Flow<List<ArchiveItemEntity>> =
        if (query.isBlank()) memexDao.observeAll() else memexDao.search(query.trim())

    suspend fun clip(text: String, source: String): LifeResult<Long> {
        if (text.isBlank()) return LifeResult.Failure(LifeError.Validation("Nothing to archive"))
        val trimmed = text.trim()
        val isUrl = Patterns.WEB_URL.matcher(trimmed).matches()
        val now = System.currentTimeMillis()
        return runCatchingLife {
            memexDao.insert(
                ArchiveItemEntity(
                    source = source,
                    kind = if (isUrl) "URL" else "TEXT",
                    title = trimmed.lineSequence().first().take(80),
                    body = trimmed,
                    capturedAt = now,
                    expiresAt = now + RETENTION_DAYS * DAY_MS,
                ),
            )
        }
    }

    suspend fun annotate(item: ArchiveItemEntity, annotation: String): LifeResult<Unit> =
        runCatchingLife {
            memexDao.update(item.copy(annotated = annotation.isNotBlank(), annotation = annotation.trim()))
        }

    suspend fun delete(id: Long): LifeResult<Unit> = runCatchingLife { memexDao.delete(id) }

    /** Retention pass (§Module 22): drop expired items nobody annotated. */
    suspend fun purgeExpired(): Int = memexDao.purgeExpired(System.currentTimeMillis())

    private companion object {
        const val RETENTION_DAYS = 365L
        const val DAY_MS = 86_400_000L
    }
}
