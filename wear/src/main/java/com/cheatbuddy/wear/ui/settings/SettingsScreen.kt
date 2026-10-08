package com.cheatbuddy.wear.ui.settings

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sd
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.cheatbuddy.wear.BuildConfig
import com.cheatbuddy.wear.data.SheetRepository
import com.cheatbuddy.wear.data.WatchPreferences
import com.cheatbuddy.wear.sync.SyncScheduler
import com.cheatbuddy.wear.sync.SyncState
import com.cheatbuddy.wear.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    val status: SyncStatus = SyncStatus(),
    val sheetCount: Int = 0,
    val imageBytes: Long = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    preferences: WatchPreferences,
    repository: SheetRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        preferences.syncStatus,
        repository.observeSheetCount(),
        repository.observeImageBytes(),
    ) { status, count, bytes -> SettingsUiState(status, count, bytes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun syncNow() = scheduler.syncNow()
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val status = state.status
    val presentation = status.presentation()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Sync") } }
            item {
                Card(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(presentation.icon, contentDescription = null, tint = presentation.tint())
                        Spacer(Modifier.width(8.dp))
                        Text(presentation.title, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        status.lastSuccessAt?.let {
                            "Last synced " + DateUtils.getRelativeTimeSpanString(
                                it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                            )
                        } ?: "Not synced yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val detail = status.message ?: presentation.detail(status)
                    if (detail != null) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(
                    onClick = viewModel::syncNow,
                    enabled = status.state != SyncState.Syncing,
                    icon = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                    label = { Text("Sync now") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ListHeader { Text("Storage") } }
            item {
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(Icons.Rounded.Sd, contentDescription = null) },
                    label = { Text("${state.sheetCount} sheets") },
                    secondaryLabel = { Text("Images: " + Formatter.formatShortFileSize(context, state.imageBytes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Cheat Buddy ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class StatusPresentation(
    val icon: ImageVector,
    val title: String,
    val tint: @Composable () -> Color,
    val detail: (SyncStatus) -> String? = { null },
)

private fun SyncStatus.presentation(): StatusPresentation = when (state) {
    SyncState.NeverSynced -> StatusPresentation(
        Icons.Rounded.CloudOff, "Waiting for phone", { MaterialTheme.colorScheme.onSurfaceVariant },
        { "Open Cheat Buddy on your phone with the watch nearby" },
    )
    SyncState.Syncing -> StatusPresentation(Icons.Rounded.Sync, "Syncing…", { MaterialTheme.colorScheme.primary })
    SyncState.UpToDate -> StatusPresentation(Icons.Rounded.CheckCircle, "Up to date", { MaterialTheme.colorScheme.primary })
    SyncState.WaitingForPhone -> StatusPresentation(
        Icons.Rounded.CloudOff, "Phone not reachable", { MaterialTheme.colorScheme.onSurfaceVariant },
        { if (it.pendingItems > 0) "${it.pendingItems} item(s) will sync when the phone is nearby" else null },
    )
    SyncState.Failed -> StatusPresentation(Icons.Rounded.SyncProblem, "Sync failed", { MaterialTheme.colorScheme.error })
    SyncState.StorageFull -> StatusPresentation(Icons.Rounded.ErrorOutline, "Storage full", { MaterialTheme.colorScheme.error })
}
