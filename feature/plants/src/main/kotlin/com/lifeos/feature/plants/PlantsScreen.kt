package com.lifeos.feature.plants

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lifeos.core.database.plants.MyPlantEntity
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.feature.plants.data.PlantAtlas
import com.lifeos.feature.plants.data.PlantSpecies
import java.io.File

/**
 * Plants (§Module Plants): a bundled offline care atlas plus "My plants" —
 * each named, optionally photographed, with an every-N-days watering reminder.
 * Tapping a plant opens a full detail screen; the pen edits name, photo,
 * watering cadence and personal notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantsRoute(viewModel: PlantsViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsState()
    val myPlants by viewModel.myPlants.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var atlasDetail by remember { mutableStateOf<PlantSpecies?>(null) }
    var adding by remember { mutableStateOf<PlantSpecies?>(null) }
    var openPlantId by remember { mutableStateOf<Long?>(null) }
    var editPlantId by remember { mutableStateOf<Long?>(null) }

    val openPlant = myPlants.firstOrNull { it.id == openPlantId }
    val editPlant = myPlants.firstOrNull { it.id == editPlantId }

    when {
        editPlant != null -> {
            MyPlantEditScreen(
                plant = editPlant,
                viewModel = viewModel,
                onDone = { editPlantId = null },
            )
            return
        }
        openPlant != null -> {
            MyPlantDetailScreen(
                plant = openPlant,
                onBack = { openPlantId = null },
                onEdit = { editPlantId = openPlant.id },
                onWater = { viewModel.watered(openPlant) },
                onDelete = { viewModel.delete(openPlant); openPlantId = null },
            )
            return
        }
    }

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
                        Card(onClick = { atlasDetail = species }) {
                            Column {
                                PlantGlyph(modifier = Modifier.fillMaxWidth().aspectRatio(1.4f))
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(species.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        "Water every ${species.waterEveryDays}d · ${species.difficulty}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (myPlants.isEmpty()) {
                    EmptyState(
                        title = "No plants yet",
                        description = "Pick one from the atlas, give it a name and photo, and LifeOS reminds you to water it.",
                    )
                } else {
                    LazyColumn {
                        listItems(myPlants, key = { it.id }) { plant ->
                            val species = PlantAtlas.byId(plant.speciesId)
                            ListItem(
                                modifier = Modifier.padding(0.dp),
                                headlineContent = { Text(plant.name) },
                                supportingContent = {
                                    val last = plant.lastWateredAt?.let { "last ${com.lifeos.feature.plants.dayFormat(it)}" } ?: "not watered yet"
                                    Text("${species?.name ?: plant.speciesId} · every ${plant.waterEveryDays}d · $last")
                                },
                                leadingContent = { PlantThumb(plant.photoPath, size = 48.dp) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.watered(plant) }) {
                                        Icon(Icons.Filled.WaterDrop, contentDescription = "Watered", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                            )
                            Surface(
                                onClick = { openPlantId = plant.id },
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth().height(1.dp),
                            ) {}
                        }
                    }
                }
            }
        }
    }

    atlasDetail?.let { species ->
        AtlasDetailDialog(
            species = species,
            onDismiss = { atlasDetail = null },
            onAdd = { adding = species; atlasDetail = null },
        )
    }

    adding?.let { species ->
        AddPlantDialog(
            species = species,
            onDismiss = { adding = null },
            onAdd = { name, days -> viewModel.addPlant(name, species, days); adding = null },
        )
    }
}

@Composable
private fun PlantGlyph(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.LocalFlorist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun PlantThumb(photoPath: String?, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(photoPath) {
        photoPath?.let { path ->
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath, opts) }
            }.getOrNull()
        }
    }
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(size)) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LocalFlorist, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun SpeciesInfo(species: PlantSpecies) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(species.latin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Water: every ~${species.waterEveryDays} days")
        Text("Light: ${species.light}")
        Text("Temperature: ${species.temperature}")
        Text("Humidity: ${species.humidity}")
        Text("Soil: ${species.soil}")
        Text("${species.toxicity} · ${species.difficulty} care")
        Text(species.tips, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AtlasDetailDialog(species: PlantSpecies, onDismiss: () -> Unit, onAdd: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(species.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlantGlyph(modifier = Modifier.fillMaxWidth().height(120.dp))
                SpeciesInfo(species)
            }
        },
        confirmButton = { Button(onClick = onAdd) { Text("Add to my plants") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AddPlantDialog(species: PlantSpecies, onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var name by remember(species) { mutableStateOf(species.name) }
    var days by remember(species) { mutableIntStateOf(species.waterEveryDays) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${species.name}") },
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
        confirmButton = { Button(onClick = { onAdd(name.trim().ifBlank { species.name }, days) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyPlantDetailScreen(
    plant: MyPlantEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onWater: () -> Unit,
    onDelete: () -> Unit,
) {
    val species = PlantAtlas.byId(plant.speciesId)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plant.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlantThumb(plant.photoPath, size = 200.dp)
            Button(onClick = onWater) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null); Text("  Mark watered")
            }
            plant.lastWateredAt?.let { Text("Last watered ${com.lifeos.feature.plants.dayFormat(it)}") }
            Text("Waters every ${plant.waterEveryDays} days")
            if (!plant.notes.isNullOrBlank()) {
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                Text(plant.notes!!)
            }
            species?.let {
                Text(it.name, style = MaterialTheme.typography.titleMedium)
                SpeciesInfo(it)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyPlantEditScreen(plant: MyPlantEntity, viewModel: PlantsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(plant.name) }
    var days by remember { mutableStateOf(plant.waterEveryDays.toString()) }
    var notes by remember { mutableStateOf(plant.notes ?: "") }
    var photoPath by remember { mutableStateOf(plant.photoPath) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Copy the picked image into app storage so it survives the source being deleted.
        val dir = File(context.filesDir, "plants").apply { mkdirs() }
        val target = File(dir, "plant-${plant.id}.jpg")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            photoPath = target.absolutePath
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit plant") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.updatePlant(
                            plant.copy(
                                name = name.trim().ifBlank { plant.name },
                                waterEveryDays = days.toIntOrNull()?.coerceIn(1, 90) ?: plant.waterEveryDays,
                                notes = notes.trim().ifBlank { null },
                                photoPath = photoPath,
                            ),
                        )
                        onDone()
                    }) { Text("Save") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlantThumb(photoPath, size = 160.dp)
            TextButton(onClick = { photoPicker.launch("image/*") }) { Text("Choose photo") }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                days,
                { days = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Water every N days") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        }
    }
}
