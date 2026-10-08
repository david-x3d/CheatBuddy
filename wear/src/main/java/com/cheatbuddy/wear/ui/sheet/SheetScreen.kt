package com.cheatbuddy.wear.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.cheatbuddy.shared.model.Block
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.wear.ui.components.ZoomLimit
import com.cheatbuddy.wear.ui.components.ZoomState
import java.io.File

@Composable
fun SheetScreen(viewModel: SheetViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        SheetUiState.Opening -> Box(Modifier.fillMaxSize())
        SheetUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "This sheet was removed on your phone",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is SheetUiState.Ready -> SheetContent(
            sheet = current.sheet,
            availableImages = current.availableImages,
            imageFile = viewModel::imageFile,
        )
    }
}

/** Index of the first block in the list (index 0 is the title). */
private const val FIRST_BLOCK_INDEX = 1

@Composable
fun SheetContent(sheet: Sheet, availableImages: Set<String>, imageFile: (String) -> File) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = FIRST_BLOCK_INDEX)
    val zoomStates = remember(sheet.id) { mutableMapOf<String, ZoomState>() }
    fun zoomStateFor(block: Block) = zoomStates.getOrPut(block.id) {
        ZoomState(maxScale = if (block is TableBlock) 2.5f else 4f)
    }

    val haptics = LocalHapticFeedback.current
    val onZoomLimit: (ZoomLimit) -> Unit = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.85f, edgeAlpha = 0.6f),
            // Use Wear's built-in rotary scrolling, including velocity tracking and fling.
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") { SheetTitle(sheet.title) }
            if (sheet.blocks.isEmpty()) {
                item(key = "empty") { EmptySheetHint() }
            }
            items(sheet.blocks, key = { it.id }) { block ->
                when (block) {
                    is TextBlock -> TextBlockView(block)
                    is ImageBlock -> ImageBlockView(
                        block = block,
                        file = if (block.imageId in availableImages) imageFile(block.imageId) else null,
                        zoomState = zoomStateFor(block),
                        onZoomLimit = onZoomLimit,
                    )
                    is TableBlock -> TableBlockView(
                        block = block,
                        zoomState = zoomStateFor(block),
                        onZoomLimit = onZoomLimit,
                        maxHeight = screenHeight * 0.7f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
