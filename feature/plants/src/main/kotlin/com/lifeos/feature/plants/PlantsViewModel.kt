package com.lifeos.feature.plants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.common.result.LifeResult
import com.lifeos.core.database.plants.MyPlantEntity
import com.lifeos.core.database.plants.PlantDao
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.feature.plants.data.PlantSpecies
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private val DAY = SimpleDateFormat("EEE d MMM", Locale.getDefault())

/** Shared date formatting for the plant screens. */
fun dayFormat(at: Long): String = DAY.format(Date(at))

@HiltViewModel
class PlantsViewModel @Inject constructor(
    private val plantDao: PlantDao,
    private val reminderDao: ReminderDao,
    private val dispatcher: LifeActionDispatcher,
) : ViewModel() {

    val myPlants = plantDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    fun onQuery(value: String) { _query.value = value }

    /** Adds the plant and schedules its recurring watering reminder at 09:00. */
    fun addPlant(name: String, species: PlantSpecies, everyDays: Int) {
        viewModelScope.launch {
            val firstAt = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, everyDays)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            val result = dispatcher.dispatch(
                LifeAction.CreateReminder(
                    title = "Water $name (${species.name})",
                    at = firstAt,
                    source = SourceRef(LifeModule.SYSTEM, "plants"),
                    recurrence = "DAYS:$everyDays",
                ),
            )
            val reminderId = (result as? LifeResult.Success)?.value
            plantDao.insert(
                MyPlantEntity(
                    name = name,
                    speciesId = species.id,
                    waterEveryDays = everyDays,
                    lastWateredAt = null,
                    reminderId = reminderId,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updatePlant(plant: MyPlantEntity) {
        viewModelScope.launch { plantDao.update(plant) }
    }

    fun watered(plant: MyPlantEntity) {
        viewModelScope.launch { plantDao.setWatered(plant.id, System.currentTimeMillis()) }
    }

    fun delete(plant: MyPlantEntity) {
        viewModelScope.launch {
            plant.reminderId?.let { reminderDao.setEnabled(it, false) }
            plantDao.delete(plant.id)
        }
    }
}
