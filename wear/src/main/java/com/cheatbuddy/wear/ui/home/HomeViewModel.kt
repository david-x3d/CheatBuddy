package com.cheatbuddy.wear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatbuddy.wear.data.SheetRepository
import com.cheatbuddy.wear.data.SheetSummary
import com.cheatbuddy.wear.data.WatchPreferences
import com.cheatbuddy.wear.sync.SyncScheduler
import com.cheatbuddy.wear.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

sealed interface HomeUiState {
    data object Opening : HomeUiState
    data class Empty(val neverSynced: Boolean) : HomeUiState
    data class Sheets(val sheets: List<SheetSummary>, val availableImages: Set<String>) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SheetRepository,
    preferences: WatchPreferences,
    scheduler: SyncScheduler,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repository.observeSummaries(),
        repository.observeAvailableImages(),
        preferences.syncStatus,
    ) { sheets, images, status ->
        if (sheets.isEmpty()) {
            HomeUiState.Empty(neverSynced = status.state == SyncState.NeverSynced)
        } else {
            HomeUiState.Sheets(sheets, images)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Opening)

    init {
        // Opportunistic background refresh; the list itself only ever reads the local database.
        scheduler.syncSoon(delaySeconds = 0)
    }

    fun imageFile(imageId: String): File = repository.imageFile(imageId)
}
