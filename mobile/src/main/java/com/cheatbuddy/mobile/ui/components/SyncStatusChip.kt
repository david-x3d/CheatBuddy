package com.cheatbuddy.mobile.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WatchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheatbuddy.mobile.sync.WatchSyncStatus

/** Compact sync state in the top bar. Tapping it retries a failed or pending sync. */
@Composable
fun SyncStatusChip(status: WatchSyncStatus, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = status,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        modifier = modifier,
        label = "sync-chip",
    ) { current ->
        val colors = MaterialTheme.colorScheme
        val (label, icon) = when (current) {
            WatchSyncStatus.NoWatch -> "No watch" to @Composable { Icon(Icons.Rounded.WatchOff, null, Modifier.size(18.dp)) }
            WatchSyncStatus.Syncing -> "Syncing" to @Composable {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            is WatchSyncStatus.Synced -> "Synced " + DateUtils.getRelativeTimeSpanString(
                current.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            ) to @Composable { Icon(Icons.Rounded.Watch, null, Modifier.size(18.dp), tint = colors.primary) }
            WatchSyncStatus.WaitingForWatch -> "Waiting for watch" to @Composable { Icon(Icons.Rounded.CloudOff, null, Modifier.size(18.dp)) }
            is WatchSyncStatus.Failed -> "Sync failed · Retry" to @Composable {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = colors.error)
            }
        }
        AssistChip(
            onClick = onRetry,
            label = { Text(label, maxLines = 1) },
            leadingIcon = icon,
            colors = if (current is WatchSyncStatus.Failed) {
                AssistChipDefaults.assistChipColors(containerColor = colors.errorContainer, labelColor = colors.onErrorContainer)
            } else {
                AssistChipDefaults.assistChipColors()
            },
        )
    }
}
