package com.lifeos.feature.route

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.route.RouteDao
import com.lifeos.core.database.route.SavedPlaceEntity
import com.lifeos.core.designsystem.component.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routeDao: RouteDao,
) : ViewModel() {

    val places = routeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SavedPlaceEntity>())

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    fun onName(value: String) { _name.value = value }
    fun onQuery(value: String) { _query.value = value }

    fun add() {
        val name = _name.value.trim()
        val query = _query.value.trim()
        if (name.isEmpty() || query.isEmpty()) return
        viewModelScope.launch {
            routeDao.insert(SavedPlaceEntity(name = name, query = query, createdAt = System.currentTimeMillis()))
            _name.value = ""
            _query.value = ""
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { routeDao.delete(id) }
    }
}

/** Route planner (§Module 17): saved places, one-tap hand-off to the nav app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteRoute(viewModel: RouteViewModel = hiltViewModel()) {
    val places by viewModel.places.collectAsState()
    val name by viewModel.name.collectAsState()
    val query by viewModel.query.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Routes") }) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = viewModel::onName,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQuery,
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.weight(1.5f),
                )
                Button(onClick = viewModel::add) { Text("Save") }
            }
            if (places.isEmpty()) {
                EmptyState(
                    title = "No saved places",
                    description = "One tap hands navigation to Google Maps or OsmAnd.",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(places, key = { it.id }) { place ->
                        ListItem(
                            headlineContent = { Text(place.name) },
                            supportingContent = { Text(place.query) },
                            leadingContent = {
                                IconButton(onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("google.navigation:q=" + Uri.encode(place.query)),
                                        ),
                                    )
                                }) {
                                    Icon(Icons.Filled.Navigation, contentDescription = "Navigate")
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.delete(place.id) }) {
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
