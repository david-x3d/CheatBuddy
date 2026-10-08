package com.cheatbuddy.mobile.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatbuddy.mobile.data.ImageRepository
import com.cheatbuddy.mobile.data.SheetRepository
import com.cheatbuddy.mobile.sync.SyncStatusRepository
import com.cheatbuddy.mobile.sync.WatchSyncStatus
import com.cheatbuddy.shared.model.Sheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SheetListViewModel @Inject constructor(
    private val sheets: SheetRepository,
    private val images: ImageRepository,
    private val syncStatus: SyncStatusRepository,
) : ViewModel() {

    /** Null until the first database read completes. */
    val items: StateFlow<List<Sheet>?> = sheets.observeSheets()
        .map<List<Sheet>, List<Sheet>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val status: StateFlow<WatchSyncStatus> = syncStatus.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchSyncStatus.Syncing)

    fun create(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(sheets.create().id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { sheets.delete(id) }
    }

    fun retrySync() = syncStatus.retry()

    fun thumbnail(imageId: String): File? = images.fullFile(imageId).takeIf { it.isFile }
}
