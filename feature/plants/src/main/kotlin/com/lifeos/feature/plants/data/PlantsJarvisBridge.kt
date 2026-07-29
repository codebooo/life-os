package com.lifeos.feature.plants.data

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.plants.MyPlantEntity
import com.lifeos.core.database.plants.PlantDao
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionHandler
import com.lifeos.core.service.LifeDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Plants as Jarvis reads them: what is on the shelf and what is thirsty. */
internal class PlantsProvider @Inject constructor(
    private val plantDao: PlantDao,
) : LifeDataProvider {

    override val topic: String = "plants"
    override val description: String = "your plants, watering intervals and what is overdue"

    override suspend fun read(query: String?): String {
        val plants = plantDao.observeAll().first()
        if (plants.isEmpty()) return "Plants: none on the shelf yet."
        val now = System.currentTimeMillis()
        return buildString {
            appendLine("Plants (${plants.size}):")
            plants.forEach { plant ->
                val dueIn = plant.dueInDays(now)
                val state = when {
                    plant.lastWateredAt == null -> "never watered"
                    dueIn != null && dueIn < 0 -> "overdue by ${-dueIn}d"
                    dueIn == 0 -> "due today"
                    else -> "due in ${dueIn}d"
                }
                appendLine("- ${plant.name} (${plant.speciesId}, every ${plant.waterEveryDays}d): $state")
            }
        }.trim()
    }

    private fun MyPlantEntity.dueInDays(now: Long): Int? {
        val last = lastWateredAt ?: return null
        val elapsedDays = ((now - last) / 86_400_000L).toInt()
        return waterEveryDays - elapsedDays
    }
}

/** Watering and adding plants on Jarvis's word. */
internal class PlantsActionHandler @Inject constructor(
    private val plantDao: PlantDao,
) : LifeActionHandler {

    override fun canHandle(action: LifeAction): Boolean =
        action is LifeAction.WaterPlant || action is LifeAction.AddPlant

    override suspend fun execute(action: LifeAction): LifeResult<Long?> = when (action) {
        is LifeAction.WaterPlant -> {
            val plants = plantDao.observeAll().first()
            val needle = action.plantName.trim().lowercase()
            val plant = plants.firstOrNull { it.name.lowercase() == needle }
                ?: plants.firstOrNull { needle in it.name.lowercase() }
            if (plant == null) {
                LifeResult.Failure(LifeError.Validation("No plant called \"${action.plantName}\""))
            } else {
                plantDao.setWatered(plant.id, System.currentTimeMillis())
                LifeResult.Success(plant.id)
            }
        }

        is LifeAction.AddPlant -> {
            val id = plantDao.insert(
                MyPlantEntity(
                    name = action.plantName.trim().take(60),
                    speciesId = action.species.trim().ifBlank { "unknown" },
                    waterEveryDays = action.waterEveryDays.coerceIn(1, 120),
                    lastWateredAt = null,
                    reminderId = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            LifeResult.Success(id)
        }

        else -> LifeResult.Failure(LifeError.Validation("Unsupported action"))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlantsJarvisModule {

    @Binds
    @IntoSet
    abstract fun bindProvider(impl: PlantsProvider): LifeDataProvider

    @Binds
    @IntoSet
    abstract fun bindHandler(impl: PlantsActionHandler): LifeActionHandler
}
