package com.cheatbuddy.wear.ui.sheet

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.exp

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
    val scope = rememberCoroutineScope()
    val focusRequester = rememberActiveFocusRequester()
    var crownTarget by remember { mutableStateOf<String?>(null) }
    var crownActivity by remember { mutableStateOf(0) }

    // Hide the crown-target ring shortly after the user stops turning the crown.
    LaunchedEffect(crownActivity) {
        if (crownTarget != null) {
            delay(1_200)
            crownTarget = null
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.85f, edgeAlpha = 0.6f),
            // The crown is handled below: it zooms the centred image/table, or scrolls when there is none.
            rotaryScrollableBehavior = null,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    val target = findZoomTarget(listState, sheet.blocks)
                    if (target != null) {
                        crownTarget = target.id
                        crownActivity++
                        val factor = exp(event.verticalScrollPixels / 450f)
                        scope.launch { zoomStateFor(target).animateZoomBy(factor, onLimit = onZoomLimit) }
                    } else {
                        scope.launch { listState.animateScrollBy(event.verticalScrollPixels) }
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
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
                        isCrownTarget = crownTarget == block.id,
                        onZoomLimit = onZoomLimit,
                    )
                    is TableBlock -> TableBlockView(
                        block = block,
                        zoomState = zoomStateFor(block),
                        isCrownTarget = crownTarget == block.id,
                        onZoomLimit = onZoomLimit,
                        maxHeight = screenHeight * 0.7f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The image or table the crown should zoom: the one covering the viewport centre, or the nearest one that
 * is still well within view. Returns null when only text is in view, in which case the crown scrolls.
 */
private fun findZoomTarget(state: ScalingLazyListState, blocks: List<Block>): Block? {
    val info = state.layoutInfo
    val reach = info.viewportSize.height * 0.3f
    return info.visibleItemsInfo
        .mapNotNull { item ->
            val block = blocks.getOrNull(item.index - FIRST_BLOCK_INDEX) ?: return@mapNotNull null
            if (block is TextBlock) return@mapNotNull null
            val distance = (abs(item.offset) - item.size / 2f).coerceAtLeast(0f)
            if (distance > reach) null else block to distance
        }
        .minByOrNull { it.second }
        ?.first
}
