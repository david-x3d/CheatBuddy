package com.cheatbuddy.wear.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.cheatbuddy.wear.data.SheetSummary
import com.cheatbuddy.wear.ui.components.LocalAmbientState
import com.cheatbuddy.wear.ui.components.rememberLocalImage
import java.io.File

@Composable
fun HomeScreen(
    onOpenSheet: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                ListHeader { Text("Cheat Buddy", color = MaterialTheme.colorScheme.primary) }
            }
            when (val current = state) {
                HomeUiState.Opening -> Unit
                is HomeUiState.Empty -> item(key = "empty") { EmptyState(current.neverSynced) }
                is HomeUiState.Sheets -> items(current.sheets, key = { it.id }) { sheet ->
                    val image = sheet.firstImageId?.takeIf { it in current.availableImages }
                    SheetCard(
                        sheet = sheet,
                        imageFile = image?.let(viewModel::imageFile),
                        onClick = { onOpenSheet(sheet.id) },
                    )
                }
            }
            item(key = "settings") {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Sync & settings") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetCard(sheet: SheetSummary, imageFile: File?, onClick: () -> Unit) {
    val ambient = LocalAmbientState.current.isAmbient
    val bitmap by rememberLocalImage(imageFile, maxEdge = 240)
    val subtitle = buildString {
        append(if (sheet.blockCount == 1) "1 block" else "${sheet.blockCount} blocks")
    }
    val current = bitmap
    if (current != null && !ambient) {
        TitleCard(
            onClick = onClick,
            containerPainter = CardDefaults.containerPainter(
                image = BitmapPainter(current),
                contentScale = ContentScale.Crop,
            ),
            title = { Text(sheet.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            subtitle = { Text(subtitle) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.height(28.dp))
        }
    } else {
        TitleCard(
            onClick = onClick,
            title = { Text(sheet.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            subtitle = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sheet.firstImageId != null) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                    }
                    Text(subtitle, color = MaterialTheme.colorScheme.primary)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            sheet.previewText?.let {
                Text(
                    it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(neverSynced: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            "No sheets yet",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            if (neverSynced) "Add one in Cheat Buddy on your phone" else "Add one on your phone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
