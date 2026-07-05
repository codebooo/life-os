package com.lifeos.feature.dhl.data

import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import javax.inject.Inject

/** Executes [LifeAction.TrackPackage] (rule R1's target). */
internal class DhlActionHandler @Inject constructor(
    private val packagesRepository: PackagesRepository,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean = action is LifeAction.TrackPackage

    override suspend fun execute(action: LifeAction): LifeResult<Long?> {
        val track = action as LifeAction.TrackPackage
        return when (
            val result = packagesRepository.addPackage(
                trackingNumber = track.trackingNumber,
                label = null,
                source = track.source,
            )
        ) {
            is LifeResult.Success -> LifeResult.Success(result.value)
            is LifeResult.Failure -> result
        }
    }
}
