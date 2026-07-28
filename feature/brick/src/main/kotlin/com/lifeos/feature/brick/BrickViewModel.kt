package com.lifeos.feature.brick

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.database.brick.BrickProfileEntity
import com.lifeos.core.database.brick.BrickSessionEntity
import com.lifeos.feature.brick.data.BrickRepository
import com.lifeos.feature.brick.engine.BrickAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One installed, launchable app the user can block. */
data class InstalledApp(val packageName: String, val label: String, val system: Boolean)

/** Draft state of the profile editor. */
data class ProfileDraft(
    val id: Long = 0,
    val name: String = "",
    val blocked: Set<String> = emptySet(),
    val limits: Map<String, Int> = emptyMap(),
    val activator: String = "MANUAL",
    val deactivator: String = "MANUAL",
    val nfcTagId: String? = null,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null,
    val strict: Boolean = false,
)

@HiltViewModel
class BrickViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brickRepository: BrickRepository,
) : ViewModel() {

    val profiles = brickRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions = brickRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<BrickSessionEntity>())

    val active = brickRepository.active

    private val _serviceEnabled = MutableStateFlow(false)
    val serviceEnabled = _serviceEnabled.asStateFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _draft = MutableStateFlow<ProfileDraft?>(null)
    val draft = _draft.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    /** Tag id captured by the editor's "pair tag" flow, if any. */
    private val _pairingTag = MutableStateFlow(false)
    val pairingTag = _pairingTag.asStateFlow()

    init {
        refresh()
        loadApps()
    }

    fun refresh() {
        _serviceEnabled.value = BrickAccessibilityService.isConnected ||
            BrickAccessibilityService.isEnabledInSettings(context)
        viewModelScope.launch { brickRepository.refresh() }
    }

    private fun loadApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                val packageManager = context.packageManager
                val launchable = packageManager
                    .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .mapNotNull { it.activityInfo?.applicationInfo }
                    .distinctBy { it.packageName }
                launchable
                    .filter { it.packageName != context.packageName }
                    .map {
                        InstalledApp(
                            packageName = it.packageName,
                            label = runCatching { packageManager.getApplicationLabel(it).toString() }
                                .getOrDefault(it.packageName),
                            system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    // ---- editor ------------------------------------------------------------

    fun newProfile() { _draft.value = ProfileDraft() }

    fun editProfile(profile: BrickProfileEntity) {
        viewModelScope.launch {
            _draft.value = ProfileDraft(
                id = profile.id,
                name = profile.name,
                blocked = profile.blockedPackages.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                limits = brickRepository.limitsFor(profile.id),
                activator = profile.activator,
                deactivator = profile.deactivator,
                nfcTagId = profile.nfcTagId,
                startMinuteOfDay = profile.startMinuteOfDay,
                endMinuteOfDay = profile.endMinuteOfDay,
                strict = profile.strict,
            )
        }
    }

    fun updateDraft(transform: (ProfileDraft) -> ProfileDraft) {
        _draft.value = _draft.value?.let(transform)
    }

    fun closeEditor() {
        _draft.value = null
        _pairingTag.value = false
    }

    fun startTagPairing() { _pairingTag.value = true }

    /** Called by the screen when a tag is read while the editor is pairing. */
    fun onTagPaired(tagId: String) {
        val normalized = tagId.trim().uppercase()
        _pairingTag.value = false
        updateDraft { it.copy(nfcTagId = normalized, activator = "NFC", deactivator = "NFC") }
        _message.value = "Tag $normalized paired — remember to Save"
    }

    /**
     * A tag tapped while Brick is open (reader mode, not intent dispatch).
     * Flips the matching mode exactly like a tap from outside the app.
     */
    fun onTagTapped(tagId: String) {
        viewModelScope.launch {
            brickRepository.refresh()
            _message.value = brickRepository.onTagScanned(tagId)
        }
    }

    fun saveDraft() {
        val draft = _draft.value ?: return
        if (draft.name.isBlank() || draft.blocked.isEmpty()) {
            _message.value = "Give the mode a name and pick at least one app"
            return
        }
        if (draft.activator == "NFC" && draft.nfcTagId == null) {
            _message.value = "Pair an NFC tag first, or pick another activator"
            return
        }
        if (draft.activator == "TIME" && draft.startMinuteOfDay == null) {
            _message.value = "Set a start time"
            return
        }
        if (draft.deactivator == "TIME" && draft.endMinuteOfDay == null) {
            _message.value = "Set an end time"
            return
        }
        viewModelScope.launch {
            brickRepository.saveProfile(
                BrickProfileEntity(
                    id = draft.id,
                    name = draft.name.trim(),
                    blockedPackages = draft.blocked.joinToString("\n"),
                    activator = draft.activator,
                    deactivator = draft.deactivator,
                    nfcTagId = draft.nfcTagId,
                    startMinuteOfDay = draft.startMinuteOfDay,
                    endMinuteOfDay = draft.endMinuteOfDay,
                    strict = draft.strict,
                    createdAt = System.currentTimeMillis(),
                ),
                limits = draft.limits,
            )
            _draft.value = null
            _message.value = "Mode saved"
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            brickRepository.deleteProfile(id)
            _message.value = "Mode deleted"
        }
    }

    // ---- session control ---------------------------------------------------

    fun startNow(profile: BrickProfileEntity) {
        viewModelScope.launch {
            if (!_serviceEnabled.value) {
                _message.value = "Enable \"LifeOS Brick\" in accessibility settings first"
                return@launch
            }
            val started = brickRepository.start(profile.id, "MANUAL")
            _message.value = if (started) "\"${profile.name}\" is blocking now" else "Another mode is already running"
        }
    }

    fun stopNow() {
        viewModelScope.launch {
            val stopped = brickRepository.stop("MANUAL")
            if (!stopped) {
                val profile = brickRepository.active.value?.profile
                _message.value = when (profile?.deactivator) {
                    "NFC" -> "Scan the paired tag to unlock this mode"
                    "TIME" -> "This mode unlocks at its end time"
                    else -> "This mode can't be stopped early"
                }
            }
        }
    }

    fun dismissMessage() { _message.value = null }

    fun openAccessibilitySettings() {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
