package com.lifeos.app.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Home shell prefs: grid vs list layout (§7.6). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val listLayout = settingsRepository.homeListLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleLayout() {
        viewModelScope.launch {
            settingsRepository.setHomeListLayout(!settingsRepository.homeListLayout.first())
        }
    }
}
