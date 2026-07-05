package com.lifeos.feature.dhl.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.lifeos.core.common.coroutines.DispatcherProvider
import com.lifeos.core.common.log.LifeLogger
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.common.result.getOrNull
import com.lifeos.core.common.result.runCatchingLife
import com.lifeos.core.database.dhl.PackageDao
import com.lifeos.core.database.dhl.PackageEntity
import com.lifeos.core.database.dhl.TrackingEventEntity
import com.lifeos.core.datastore.IntegrationsRepository
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

interface PackagesRepository {
    fun observePackages(): Flow<List<PackageEntity>>
    fun observeEvents(packageId: Long): Flow<List<TrackingEventEntity>>

    /** Idempotent per tracking number (R1 dedupe). */
    suspend fun addPackage(trackingNumber: String, label: String?, source: SourceRef?): LifeResult<Long>
    suspend fun refresh(packageId: Long): LifeResult<Unit>
    suspend fun refreshAllActive()
    suspend fun delete(packageId: Long)
}

@Singleton
internal class DefaultPackagesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageDao: PackageDao,
    private val dhlApi: DhlApi,
    private val integrationsRepository: IntegrationsRepository,
    private val actionDispatcher: dagger.Lazy<LifeActionDispatcher>,
    private val dispatchers: DispatcherProvider,
) : PackagesRepository {

    override fun observePackages(): Flow<List<PackageEntity>> = packageDao.observeAll()

    override fun observeEvents(packageId: Long): Flow<List<TrackingEventEntity>> =
        packageDao.observeEvents(packageId)

    override suspend fun addPackage(
        trackingNumber: String,
        label: String?,
        source: SourceRef?,
    ): LifeResult<Long> = withContext(dispatchers.io) {
        runCatchingLife {
            packageDao.getByTrackingNumber(trackingNumber)?.let { return@runCatchingLife it.id }
            val id = packageDao.insert(
                PackageEntity(
                    trackingNumber = trackingNumber,
                    label = label,
                    status = "NEW",
                    statusDescription = null,
                    estimatedDeliveryAt = null,
                    reminderId = null,
                    lastRefreshedAt = null,
                    sourceModule = source?.module?.name,
                    sourceEntityId = source?.entityId?.toLongOrNull(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            refresh(id)
            id
        }
    }

    override suspend fun refresh(packageId: Long): LifeResult<Unit> = withContext(dispatchers.io) {
        runCatchingLife {
            val pkg = packageDao.getById(packageId) ?: error("Package not found")
            val apiKey = integrationsRepository.dhlApiKey.first()
            check(apiKey.isNotBlank()) { "DHL API key not configured — add it in Packages settings" }

            val shipment = dhlApi.track(pkg.trackingNumber, apiKey)
            val statusChanged = shipment.status != pkg.status && shipment.status != "NOT_FOUND"

            packageDao.deleteEventsOf(packageId)
            packageDao.insertEvents(
                shipment.events.map {
                    TrackingEventEntity(
                        packageId = packageId,
                        status = it.status,
                        description = it.description,
                        location = it.location,
                        at = it.at,
                    )
                },
            )

            // R1: once a delivery estimate appears, set a morning-of reminder (once).
            var reminderId = pkg.reminderId
            if (reminderId == null && shipment.estimatedDeliveryAt != null) {
                val remindAt = morningOf(shipment.estimatedDeliveryAt)
                if (remindAt > System.currentTimeMillis()) {
                    reminderId = actionDispatcher.get().dispatch(
                        LifeAction.CreateReminder(
                            title = "Package arriving today: ${pkg.label ?: pkg.trackingNumber}",
                            at = remindAt,
                            source = SourceRef(LifeModule.DHL, packageId.toString()),
                        ),
                    ).getOrNull()
                }
            }

            packageDao.update(
                pkg.copy(
                    status = if (shipment.status == "NOT_FOUND" && pkg.status != "NEW") pkg.status else shipment.status,
                    statusDescription = shipment.statusDescription ?: pkg.statusDescription,
                    estimatedDeliveryAt = shipment.estimatedDeliveryAt ?: pkg.estimatedDeliveryAt,
                    reminderId = reminderId,
                    lastRefreshedAt = System.currentTimeMillis(),
                ),
            )

            if (statusChanged) notifyStatus(pkg.copy(status = shipment.status, statusDescription = shipment.statusDescription))
        }
    }

    override suspend fun refreshAllActive() {
        packageDao.activePackages().forEach { pkg ->
            refresh(pkg.id)
        }
    }

    override suspend fun delete(packageId: Long) = withContext(dispatchers.io) {
        packageDao.delete(packageId)
    }

    private fun notifyStatus(pkg: PackageEntity) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Package updates", NotificationManager.IMPORTANCE_DEFAULT),
            )
            manager.notify(
                pkg.trackingNumber.hashCode(),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(pkg.label ?: pkg.trackingNumber)
                    .setContentText(pkg.statusDescription ?: pkg.status)
                    .setAutoCancel(true)
                    .build(),
            )
        } catch (e: Exception) {
            LifeLogger.w(TAG, "Could not post package notification", e)
        }
    }

    companion object {
        private const val TAG = "PackagesRepository"
        private const val CHANNEL_ID = "lifeos_packages"

        fun morningOf(deliveryAt: Long): Long = Calendar.getInstance().apply {
            timeInMillis = deliveryAt
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
