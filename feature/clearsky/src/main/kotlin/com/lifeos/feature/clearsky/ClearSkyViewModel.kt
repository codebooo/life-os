package com.lifeos.feature.clearsky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.feature.clearsky.data.ClearSkyRepository
import com.lifeos.feature.clearsky.data.SkyForecast
import com.lifeos.feature.clearsky.data.SkyPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which hour the strip is centred on, mirroring clearoutside's own views. */
enum class SkyView(val query: String?, val label: String) {
    NOW(null, "Now"),
    MIDNIGHT("midnight", "Midnight"),
    MIDDAY("midday", "Midday"),
}

data class ClearSkyUiState(
    val loading: Boolean = false,
    val place: SkyPlace? = null,
    val forecast: SkyForecast? = null,
    val view: SkyView = SkyView.NOW,
    val selectedDay: Int = 0,
    val places: List<SkyPlace> = emptyList(),
    val query: String = "",
    val results: List<SkyPlace> = emptyList(),
    val searching: Boolean = false,
    val metric: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class ClearSkyViewModel @Inject constructor(
    private val repository: ClearSkyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClearSkyUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.places()
            val last = repository.lastPlace() ?: saved.firstOrNull()
            _uiState.value = _uiState.value.copy(places = saved, place = last)
            if (last != null) load(last)
        }
    }

    fun refresh() {
        _uiState.value.place?.let { load(it) }
    }

    fun selectView(view: SkyView) {
        _uiState.value = _uiState.value.copy(view = view)
        refresh()
    }

    fun selectDay(index: Int) {
        _uiState.value = _uiState.value.copy(selectedDay = index)
    }

    fun toggleMetric() {
        _uiState.value = _uiState.value.copy(metric = !_uiState.value.metric)
    }

    fun onQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun search() {
        val query = _uiState.value.query
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searching = true)
            val result = repository.search(query)
            _uiState.value = _uiState.value.copy(
                searching = false,
                results = result.getOrDefault(emptyList()),
                message = result.exceptionOrNull()?.let { "Place lookup failed: ${it.message}" }
                    ?: if (result.getOrDefault(emptyList()).isEmpty()) "No places matched" else null,
            )
        }
    }

    /** Accepts "52.52, 13.40" typed straight into the search field. */
    fun useTypedCoordinates(): Boolean {
        val match = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,; ]\s*(-?\d+(?:\.\d+)?)\s*$""")
            .find(_uiState.value.query) ?: return false
        val lat = match.groupValues[1].toDoubleOrNull() ?: return false
        val lon = match.groupValues[2].toDoubleOrNull() ?: return false
        selectPlace(SkyPlace("$lat, $lon", lat, lon))
        return true
    }

    fun selectPlace(place: SkyPlace) {
        _uiState.value = _uiState.value.copy(place = place, results = emptyList(), query = "")
        viewModelScope.launch { repository.setLastPlace(place) }
        load(place)
    }

    fun savePlace() {
        val place = _uiState.value.place ?: return
        val named = _uiState.value.forecast?.locationName?.takeIf { it.isNotBlank() } ?: place.name
        viewModelScope.launch {
            repository.savePlace(place.copy(name = named))
            _uiState.value = _uiState.value.copy(places = repository.places(), message = "Spot saved")
        }
    }

    fun removePlace(place: SkyPlace) {
        viewModelScope.launch {
            repository.removePlace(place)
            _uiState.value = _uiState.value.copy(places = repository.places())
        }
    }

    fun useDeviceLocation(latitude: Double, longitude: Double) {
        selectPlace(SkyPlace("My location", latitude, longitude))
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun load(place: SkyPlace) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val result = repository.forecast(place, _uiState.value.view.query)
            _uiState.value = _uiState.value.copy(
                loading = false,
                forecast = result.getOrNull() ?: _uiState.value.forecast,
                selectedDay = 0,
                message = result.exceptionOrNull()?.let { "Could not load the forecast: ${it.message}" },
            )
        }
    }
}
