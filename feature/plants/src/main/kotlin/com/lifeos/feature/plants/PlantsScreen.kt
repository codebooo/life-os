package com.lifeos.feature.plants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.plants.MyPlantEntity
import com.lifeos.core.database.plants.PlantDao
import com.lifeos.core.database.reminders.ReminderDao
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.model.LifeModule
import com.lifeos.core.model.SourceRef
import com.lifeos.core.service.LifeAction
import com.lifeos.core.service.LifeActionDispatcher
import com.lifeos.feature.plants.data.PlantAtlas
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
                    title = "Water $name (${species.name}) ${species.emoji}",
                    at = firstAt,
                    source = SourceRef(LifeModule.SYSTEM, "plants"),
                    recurrence = "DAYS:$everyDays",
                ),
            )
            val reminderId = (result as? com.lifeos.core.common.result.LifeResult.Success)?.value
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

/**
 * Plants (§Module Plants): a bundled offline atlas of houseplants, herbs and
 * balcony plants with full care profiles, plus "My plants" — name one, and a
 * recurring watering reminder (every N days, species default) rings on time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantsRoute(viewModel: PlantsViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsState()
    val myPlants by viewModel.myPlants.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<PlantSpecies?>(null) }
    var adding by remember { mutableStateOf<PlantSpecies?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Plants") }) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Atlas") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("My plants (${myPlants.size})") })
            }

            if (tab == 0) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQuery,
                    label = { Text("Search ${PlantAtlas.ALL.size} plants") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    items(PlantAtlas.search(query), key = { it.id }) { species ->
                        Card(onClick = { detail = species }) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(species.emoji, style = MaterialTheme.typography.headlineMedium)
                                Text(species.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "💧 every ${species.waterEveryDays}d · ${species.difficulty}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                if (myPlants.isEmpty()) {
                    EmptyState(
                        title = "No plants yet",
                        description = "Pick one from the atlas, give it a name, and LifeOS reminds you to water it.",
                    )
                } else {
                    LazyColumn {
                        listItems(myPlants, key = { it.id }) { plant ->
                            val species = PlantAtlas.byId(plant.speciesId)
                            ListItem(
                                headlineContent = { Text("${species?.emoji ?: "🪴"} ${plant.name}") },
                                supportingContent = {
                                    val last = plant.lastWateredAt?.let { "last ${DAY.format(Date(it))}" } ?: "not watered yet"
                                    Text("${species?.name ?: plant.speciesId} · every ${plant.waterEveryDays}d · $last")
                                },
                                leadingContent = {
                                    IconButton(onClick = { viewModel.watered(plant) }) {
                                        Icon(Icons.Filled.WaterDrop, contentDescription = "Watered", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.delete(plant) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { species ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("${species.emoji} ${species.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(species.latin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("💧 Water: every ~${species.waterEveryDays} days")
                    Text("☀️ Light: ${species.light}")
                    Text("🌡 Temperature: ${species.temperature}")
                    Text("💨 Humidity: ${species.humidity}")
                    Text("🪱 Soil: ${species.soil}")
                    Text("🐾 ${species.toxicity} · ${species.difficulty} care")
                    Text(species.tips, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(onClick = { adding = species; detail = null }) { Text("Add to my plants") }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
        )
    }

    adding?.let { species ->
        var name by remember(species) { mutableStateOf(species.name) }
        var days by remember(species) { mutableIntStateOf(species.waterEveryDays) }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Add ${species.emoji} ${species.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Water every")
                        listOf(days - 1, days, days + 1).filter { it in 1..60 }.forEach { option ->
                            FilterChip(selected = option == days, onClick = { days = option }, label = { Text("${option}d") })
                        }
                    }
                    Text(
                        "A watering reminder rings every $days day(s) at 09:00.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addPlant(name.trim().ifBlank { species.name }, species, days)
                    adding = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }
}

private val DAY = SimpleDateFormat("EEE d MMM", Locale.getDefault())
