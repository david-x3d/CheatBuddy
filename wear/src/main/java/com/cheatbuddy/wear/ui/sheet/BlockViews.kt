package com.cheatbuddy.wear.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.model.TextSize
import com.cheatbuddy.wear.ui.components.LocalAmbientState
import com.cheatbuddy.wear.ui.components.ZoomLimit
import com.cheatbuddy.wear.ui.components.ZoomPanBox
import com.cheatbuddy.wear.ui.components.ZoomState
import com.cheatbuddy.wear.ui.components.rememberLocalImage
import java.io.File
import java.util.Locale

private val BlockShape = RoundedCornerShape(18.dp)
private val CellShape = RoundedCornerShape(6.dp)

@Composable
fun TextBlockView(block: TextBlock, modifier: Modifier = Modifier) {
    val typography = MaterialTheme.typography
    val base = when (block.size) {
        TextSize.Small -> typography.bodySmall
        TextSize.Medium -> typography.bodyLarge
        TextSize.Large -> typography.titleLarge
    }
    Text(
        text = block.text,
        style = base.copy(fontWeight = if (block.bold) FontWeight.Bold else base.fontWeight),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Zoom badge; transformations stay in the graphics layer while pinching and panning. */
@Composable
private fun ZoomFrame(
    state: ZoomState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ambient = LocalAmbientState.current.isAmbient
    Box(
        modifier
            .fillMaxWidth()
            .padding(3.dp)
            .clip(RoundedCornerShape(15.dp)),
    ) {
        content()
        AnimatedVisibility(
            visible = state.isZoomed && !ambient,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
            Text(
                text = String.format(Locale.US, "%.1f×", state.scale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun ImageBlockView(
    block: ImageBlock,
    file: File?,
    zoomState: ZoomState,
    onZoomLimit: (ZoomLimit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ambient = LocalAmbientState.current
    if (ambient.isAmbient && ambient.lowBit) {
        // Low-bit ambient cannot show photos well; keep the layout stable with an outline.
        Box(
            modifier.fillMaxWidth().height(48.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, BlockShape),
        )
        return
    }
    ZoomFrame(zoomState, modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Fit width, but cap height so tall images don't take several screens.
            val maxImageHeight = maxWidth * 1.25f
            val naturalHeight = maxWidth / block.aspectRatio
            val (w, h) = if (naturalHeight > maxImageHeight) {
                maxImageHeight * block.aspectRatio to maxImageHeight
            } else {
                maxWidth to naturalHeight
            }
            val bitmap by rememberLocalImage(file)
            ZoomPanBox(state = zoomState, onZoomLimit = onZoomLimit, modifier = Modifier.fillMaxWidth()) {
                val current = bitmap
                if (current != null) {
                    Image(
                        bitmap = current,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = if (ambient.isAmbient) GrayscaleFilter else null,
                        modifier = Modifier.size(w, h).alpha(if (ambient.isAmbient) 0.5f else 1f),
                    )
                } else {
                    MissingImage(Modifier.size(w, h))
                }
            }
        }
    }
}

private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Shown for images that have not reached the watch yet. Not a spinner: content is never "loading". */
@Composable
private fun MissingImage(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                "Image syncs when\nyour phone is nearby",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun TableBlockView(
    block: TableBlock,
    zoomState: ZoomState,
    onZoomLimit: (ZoomLimit) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val table = remember(block) { block.normalized() }
    if (table.columnCount == 0) return
    val colors = MaterialTheme.colorScheme
    val cellStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface)
    val headerStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold)

    ZoomFrame(zoomState, modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widths = rememberColumnWidths(table, cellStyle, headerStyle, maxWidth)
            ZoomPanBox(
                state = zoomState,
                maxHeight = maxHeight,
                onZoomLimit = onZoomLimit,
                modifier = Modifier.fillMaxWidth(),
                header = table.headerRow?.let { header ->
                    {
                        // Opaque backing so body rows scrolling underneath don't show through the cell gaps.
                        Box(Modifier.background(colors.background).padding(bottom = 2.dp)) {
                            TableRow(header, widths, headerStyle, colors.primaryContainer)
                        }
                    }
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    table.bodyRows.forEachIndexed { index, row ->
                        val tint = if (index % 2 == 0) colors.surfaceContainer else colors.surfaceContainerHigh
                        TableRow(row, widths, cellStyle, tint)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, widths: List<Dp>, style: TextStyle, background: androidx.compose.ui.graphics.Color) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.forEachIndexed { column, text ->
            Box(
                Modifier
                    .width(widths[column])
                    .fillMaxHeight()
                    .clip(CellShape)
                    .background(background)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(text, style = style)
            }
        }
    }
}

/**
 * Column widths from the widest single-line cell, clamped to a readable range. Narrow tables stretch to the
 * available width; wide tables overflow and can be panned horizontally.
 */
@Composable
private fun rememberColumnWidths(table: TableBlock, cellStyle: TextStyle, headerStyle: TextStyle, available: Dp): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(table, cellStyle, headerStyle, available, density) {
        val gap = 2.dp
        val horizontalPadding = 16.dp
        val natural = (0 until table.columnCount).map { column ->
            val widest = table.rows.withIndex().maxOf { (rowIndex, row) ->
                val style = if (table.hasHeader && rowIndex == 0) headerStyle else cellStyle
                measurer.measure(row[column], style, maxLines = 1).size.width
            }
            with(density) { widest.toDp() + horizontalPadding }.coerceIn(44.dp, 150.dp)
        }
        val total = natural.fold(0.dp) { acc, w -> acc + w } + gap * (natural.size - 1)
        if (total < available) {
            val extra = (available - total) / natural.size
            natural.map { it + extra }
        } else {
            natural
        }
    }
}

@Composable
fun SheetTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
}

@Composable
fun EmptySheetHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "This sheet is empty",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
