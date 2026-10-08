package com.cheatbuddy.wear.ui.sheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.wear.data.SheetRepository
import com.cheatbuddy.wear.tile.LastSheetTileService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface SheetUiState {
    /** Only visible for the few milliseconds of the first local DB read. Rendered as an empty black screen. */
    data object Opening : SheetUiState
    data object NotFound : SheetUiState
    data class Ready(val sheet: Sheet, val availableImages: Set<String>) : SheetUiState
}

@HiltViewModel
class SheetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SheetRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val sheetId: String = checkNotNull(savedStateHandle["sheetId"])

    val state: StateFlow<SheetUiState> =
        combine(repository.observeSheet(sheetId), repository.observeAvailableImages()) { sheet, images ->
            if (sheet == null) SheetUiState.NotFound else SheetUiState.Ready(sheet, images)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SheetUiState.Opening)

    init {
        viewModelScope.launch {
            repository.markViewed(sheetId)
            runCatching { TileService.getUpdater(context).requestUpdate(LastSheetTileService::class.java) }
        }
    }

    fun imageFile(imageId: String): File = repository.imageFile(imageId)
}
