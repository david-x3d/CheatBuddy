package com.cheatbuddy.mobile.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheatbuddy.mobile.ui.preview.WatchPreview
import com.cheatbuddy.shared.model.Block
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class EditorMode { Edit, Preview }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(onBack: () -> Unit, viewModel: EditorViewModel = hiltViewModel()) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var mode by rememberSaveable { mutableStateOf(EditorMode.Edit) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addImage)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) viewModel.addImage(uri)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(end = 12.dp)) {
                        EditorMode.entries.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = mode == item,
                                onClick = { mode = item },
                                shape = SegmentedButtonDefaults.itemShape(index, EditorMode.entries.size),
                                icon = {
                                    Icon(
                                        if (item == EditorMode.Edit) Icons.Rounded.Edit else Icons.Rounded.Watch,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                label = { Text(if (item == EditorMode.Edit) "Edit" else "Watch") },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val current = sheet ?: return@Scaffold
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "editor-mode",
        ) { target ->
            when (target) {
                EditorMode.Edit -> Box(Modifier.fillMaxSize()) {
                    BlockList(current, viewModel)
                    AnimatedVisibility(
                        visible = importing,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                        modifier = Modifier.align(Alignment.Center),
                    ) { LoadingIndicator(Modifier.size(64.dp)) }
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    ) {
                        ToolbarAction(Icons.Rounded.Notes, "Add text", viewModel::addText)
                        ToolbarAction(Icons.Rounded.TableChart, "Add table", viewModel::addTable)
                        ToolbarAction(Icons.Rounded.PhotoLibrary, "Add image from gallery") {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        ToolbarAction(Icons.Rounded.CameraAlt, "Take a photo") {
                            val uri = viewModel.newCameraUri()
                            cameraUri = uri
                            takePicture.launch(uri)
                        }
                    }
                }
                EditorMode.Preview -> PreviewPane(current, viewModel)
            }
        }
    }
}

@Composable
private fun ToolbarAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description) }
}

@Composable
private fun BlockList(sheet: Sheet, viewModel: EditorViewModel) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    // Index 0 is the title field, so block indices are offset by one.
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveBlock(from.index - 1, to.index - 1)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title") {
            TextField(
                value = sheet.title,
                onValueChange = viewModel::setTitle,
                placeholder = { Text("Sheet title", style = MaterialTheme.typography.headlineMedium) },
                textStyle = MaterialTheme.typography.headlineMedium,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (sheet.blocks.isEmpty()) {
            item(key = "hint") {
                Text(
                    "Add text, a table or an image with the toolbar below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        items(sheet.blocks, key = { it.id }) { block ->
            ReorderableItem(reorderState, key = block.id) { isDragging ->
                val elevation = if (isDragging) 8.dp else 0.dp
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().shadow(elevation, MaterialTheme.shapes.large),
                ) {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                        ) {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                    onDragStopped = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                                ),
                            ) { Icon(Icons.Rounded.DragIndicator, contentDescription = "Reorder") }
                            Icon(block.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(block.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.removeBlock(block.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove block")
                            }
                        }
                        when (block) {
                            is TextBlock -> TextBlockEditor(block, viewModel::updateBlock)
                            is ImageBlock -> ImageBlockEditor(block, viewModel.imageFile(block.imageId))
                            is TableBlock -> TableBlockEditor(block, viewModel::updateBlock)
                        }
                    }
                }
            }
        }
    }
}

private val Block.icon: ImageVector get() = when (this) {
    is TextBlock -> Icons.Rounded.Notes
    is ImageBlock -> Icons.Rounded.Image
    is TableBlock -> Icons.Rounded.TableChart
}

private val Block.label: String get() = when (this) {
    is TextBlock -> "Text"
    is ImageBlock -> "Image"
    is TableBlock -> "Table"
}

@Composable
private fun PreviewPane(sheet: Sheet, viewModel: EditorViewModel) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WatchPreview(sheet, viewModel::imageFile)
        Spacer(Modifier.height(24.dp))
        Text("Live watch preview", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Scroll inside the watch face. On the watch, turn the crown to zoom images and tables.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
