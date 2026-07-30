package com.lifeos.feature.triggers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.places.PlaceDao
import com.lifeos.core.database.triggers.TriggerDao
import com.lifeos.core.database.triggers.TriggerFireEntity
import com.lifeos.core.database.triggers.TriggerRuleEntity
import com.lifeos.feature.triggers.data.TriggerCatalog
import com.lifeos.feature.triggers.data.TriggerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A rule being written; days are kept as a set so chips are trivial. */
data class RuleDraft(
    val id: Long = 0,
    val name: String = "",
    val triggerType: String = "TIME",
    val triggerArg: String = "",
    val actionType: String = "TASK",
    val actionArg: String = "",
    val days: Set<Int> = emptySet(),
)

/** A place being added or edited from the Places tab. */
data class PlaceDraft(
    val id: Long = 0,
    val name: String = "",
    val wifiSsid: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = "150",
)

data class TriggersUiState(
    val tab: Int = 0,
    val placeRows: List<com.lifeos.core.database.places.PlaceEntity> = emptyList(),
    val placeDraft: PlaceDraft? = null,
    val here: String? = null,
    val rules: List<TriggerRuleEntity> = emptyList(),
    val fires: List<TriggerFireEntity> = emptyList(),
    val places: List<Pair<Long, String>> = emptyList(),
    val draft: RuleDraft? = null,
    val message: String? = null,
)

@HiltViewModel
class TriggersViewModel @Inject constructor(
    private val triggerDao: TriggerDao,
    private val placeDao: PlaceDao,
    private val engine: TriggerEngine,
    private val placeEngine: com.lifeos.core.places.PlaceEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TriggersUiState())
    val uiState = _uiState.asStateFlow()

    init {
        engine.start()
        viewModelScope.launch {
            triggerDao.observeRules().collect { rules -> _uiState.value = _uiState.value.copy(rules = rules) }
        }
        viewModelScope.launch {
            triggerDao.observeFires().collect { fires -> _uiState.value = _uiState.value.copy(fires = fires) }
        }
        viewModelScope.launch {
            placeDao.observeAll().collect { places ->
                _uiState.value = _uiState.value.copy(
                    places = places.map { it.id to it.name },
                    placeRows = places,
                )
            }
        }
        viewModelScope.launch {
            placeEngine.current.collect { place ->
                _uiState.value = _uiState.value.copy(here = place?.name)
            }
        }
    }

    fun selectTab(index: Int) { _uiState.value = _uiState.value.copy(tab = index) }

    fun newRule() { _uiState.value = _uiState.value.copy(draft = RuleDraft()) }

    // ---- places ------------------------------------------------------------

    fun newPlace() { _uiState.value = _uiState.value.copy(placeDraft = PlaceDraft()) }

    fun editPlace(place: com.lifeos.core.database.places.PlaceEntity) {
        _uiState.value = _uiState.value.copy(
            placeDraft = PlaceDraft(
                id = place.id,
                name = place.name,
                wifiSsid = place.wifiSsid.orEmpty(),
                latitude = place.latitude?.toString().orEmpty(),
                longitude = place.longitude?.toString().orEmpty(),
                radiusMeters = place.radiusMeters.toString(),
            ),
        )
    }

    fun closePlaceEditor() { _uiState.value = _uiState.value.copy(placeDraft = null) }

    fun updatePlaceDraft(transform: (PlaceDraft) -> PlaceDraft) {
        _uiState.value = _uiState.value.copy(placeDraft = _uiState.value.placeDraft?.let(transform))
    }

    /** Fills the draft from where the phone is right now. */
    fun useHere() {
        val fix = placeEngine.lastKnownLocation()
        val ssid = placeEngine.currentSsid()
        updatePlaceDraft { draft ->
            draft.copy(
                latitude = fix?.latitude?.toString() ?: draft.latitude,
                longitude = fix?.longitude?.toString() ?: draft.longitude,
                wifiSsid = ssid ?: draft.wifiSsid,
            )
        }
        if (fix == null && ssid == null) {
            _uiState.value = _uiState.value.copy(
                message = "No location or Wi-Fi signal yet - grant location and try again",
            )
        }
    }

    fun savePlace() {
        val draft = _uiState.value.placeDraft ?: return
        if (draft.name.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Give the place a name")
            return
        }
        val latitude = draft.latitude.toDoubleOrNull()
        val longitude = draft.longitude.toDoubleOrNull()
        if (latitude == null && draft.wifiSsid.isBlank()) {
            _uiState.value = _uiState.value.copy(
                message = "A place needs coordinates or a Wi-Fi name to be recognised",
            )
            return
        }
        viewModelScope.launch {
            val row = com.lifeos.core.database.places.PlaceEntity(
                id = draft.id,
                name = draft.name.trim().take(40),
                latitude = latitude,
                longitude = longitude,
                radiusMeters = draft.radiusMeters.filter { it.isDigit() }.toIntOrNull()?.coerceIn(30, 5_000) ?: 150,
                wifiSsid = draft.wifiSsid.trim().ifBlank { null },
                createdAt = System.currentTimeMillis(),
            )
            if (draft.id == 0L) placeDao.insert(row) else placeDao.update(row)
            placeEngine.refresh()
            _uiState.value = _uiState.value.copy(placeDraft = null, message = "Place saved")
        }
    }

    fun deletePlace(id: Long) {
        viewModelScope.launch {
            placeDao.delete(id)
            placeEngine.refresh()
        }
    }

    fun editRule(rule: TriggerRuleEntity) {
        _uiState.value = _uiState.value.copy(
            draft = RuleDraft(
                id = rule.id,
                name = rule.name,
                triggerType = rule.triggerType,
                triggerArg = rule.triggerArg,
                actionType = rule.actionType,
                actionArg = rule.actionArg,
                days = rule.days.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
            ),
        )
    }

    fun closeEditor() { _uiState.value = _uiState.value.copy(draft = null) }
    fun dismissMessage() { _uiState.value = _uiState.value.copy(message = null) }

    fun onName(value: String) = updateDraft { it.copy(name = value) }
    fun onTriggerType(value: String) = updateDraft { it.copy(triggerType = value, triggerArg = "") }
    fun onTriggerArg(value: String) = updateDraft { it.copy(triggerArg = value) }
    fun onActionType(value: String) = updateDraft { it.copy(actionType = value, actionArg = "") }
    fun onActionArg(value: String) = updateDraft { it.copy(actionArg = value) }

    fun toggleDay(day: Int) = updateDraft { draft ->
        draft.copy(days = if (day in draft.days) draft.days - day else draft.days + day)
    }

    fun saveDraft(runAfterSave: Boolean = false) {
        val draft = _uiState.value.draft ?: return
        val triggerSpec = TriggerCatalog.triggerByType[draft.triggerType]
        val actionSpec = TriggerCatalog.actionByType[draft.actionType]
        if (triggerSpec == null || actionSpec == null) {
            _uiState.value = _uiState.value.copy(message = "Pick a trigger and an action")
            return
        }
        val triggerArg = normalizeArg(draft.triggerType, draft.triggerArg)
        if (triggerSpec.arg != com.lifeos.feature.triggers.data.TriggerArg.NONE && triggerArg.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "\"${triggerSpec.label}\" still needs a value")
            return
        }
        if (actionSpec.arg != com.lifeos.feature.triggers.data.TriggerArg.NONE && draft.actionArg.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "\"${actionSpec.label}\" still needs a value")
            return
        }
        viewModelScope.launch {
            val row = TriggerRuleEntity(
                id = draft.id,
                name = draft.name.trim().ifBlank { "${triggerSpec.label} -> ${actionSpec.label}" }.take(60),
                triggerType = draft.triggerType,
                triggerArg = triggerArg,
                days = draft.days.sorted().joinToString(","),
                actionType = draft.actionType,
                actionArg = draft.actionArg.trim(),
                createdAt = System.currentTimeMillis(),
            )
            val saved = if (draft.id == 0L) {
                val id = triggerDao.insertRule(row)
                triggerDao.rule(id)
            } else {
                val existing = triggerDao.rule(draft.id)
                if (existing != null) {
                    triggerDao.updateRule(
                        existing.copy(
                            name = row.name,
                            triggerType = row.triggerType,
                            triggerArg = row.triggerArg,
                            days = row.days,
                            actionType = row.actionType,
                            actionArg = row.actionArg,
                        ),
                    )
                }
                triggerDao.rule(draft.id)
            }
            engine.rearmAll()
            val message = if (runAfterSave && saved != null) engine.run(saved, byHand = true) else "Rule saved"
            _uiState.value = _uiState.value.copy(draft = null, message = message)
        }
    }

    /** Save, then run once so the rule can be checked without waiting for it. */
    fun testDraft() = saveDraft(runAfterSave = true)

    fun toggle(rule: TriggerRuleEntity) {
        viewModelScope.launch {
            triggerDao.updateRule(rule.copy(enabled = !rule.enabled))
            engine.rearmAll()
        }
    }

    fun runNow(rule: TriggerRuleEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(message = engine.run(rule, byHand = true))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            triggerDao.deleteRule(id)
            engine.rearmAll()
        }
    }

    /** "22:00" and "2200" both mean minute 1320. */
    private fun normalizeArg(triggerType: String, raw: String): String {
        if (triggerType != "TIME") return raw.trim()
        val digits = raw.filter { it.isDigit() }
        if (raw.contains(':')) {
            val hours = raw.substringBefore(':').filter { it.isDigit() }.toIntOrNull() ?: return ""
            val minutes = raw.substringAfter(':').filter { it.isDigit() }.toIntOrNull() ?: 0
            return (hours * 60 + minutes).coerceIn(0, 1439).toString()
        }
        val value = digits.toIntOrNull() ?: return ""
        return if (value > 1439) {
            ((value / 100) * 60 + value % 100).coerceIn(0, 1439).toString()
        } else {
            value.toString()
        }
    }

    private fun updateDraft(transform: (RuleDraft) -> RuleDraft) {
        _uiState.value = _uiState.value.copy(draft = _uiState.value.draft?.let(transform))
    }
}
