package com.cheatbuddy.mobile.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cheatbuddy.mobile.ui.theme.WatchAccent
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.model.TextSize
import java.io.File

/** Watch palette, mirrored from the wear module so the preview matches the real thing. */
private object Watch {
    val onSurface = Color(0xFFE6EFEB)
    val surfaceContainer = Color(0xFF161D1B)
    val surfaceContainerHigh = Color(0xFF212A27)
    val primaryContainer = Color(0xFF0F4A3C)
    val onPrimaryContainer = Color(0xFFB8F5E2)
}

/**
 * A round watch mockup that renders a sheet the way the watch does: true black, mint accent, rounded
 * images, tinted tables with a header row. Scrollable, and updated live while editing.
 */
@Composable
fun WatchPreview(sheet: Sheet, imageFile: (String) -> File?, modifier: Modifier = Modifier, size: Dp = 300.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF3A3F3D), Color(0xFF151817), Color(0xFF2B302E))))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.Black)
                .border(1.dp, Color(0xFF2A2F2D), CircleShape),
        ) {
            val inset = size * 0.1f
            LazyColumn(
                contentPadding = PaddingValues(start = inset, end = inset, top = 44.dp, bottom = inset * 1.6f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        sheet.title.ifBlank { "Untitled" },
                        color = WatchAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }
                items(sheet.blocks, key = { it.id }) { block ->
                    when (block) {
                        is TextBlock -> PreviewText(block)
                        is ImageBlock -> PreviewImage(block, imageFile(block.imageId))
                        is TableBlock -> PreviewTable(block)
                    }
                }
            }
            // Time text and edge fades, like the curved TimeText and edge scaling on the watch.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0f)))),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text("10:08", color = Watch.onSurface, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black))),
            )
        }
    }
}

@Composable
private fun PreviewText(block: TextBlock) {
    val size = when (block.size) {
        TextSize.Small -> 12.sp
        TextSize.Medium -> 15.sp
        TextSize.Large -> 20.sp
    }
    Text(
        block.text.ifBlank { " " },
        color = Watch.onSurface,
        fontSize = size,
        lineHeight = size * 1.3f,
        fontWeight = if (block.bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

@Composable
private fun PreviewImage(block: ImageBlock, file: File?) {
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(block.aspectRatio.coerceIn(0.8f, 3f))
            .clip(RoundedCornerShape(16.dp))
            .background(Watch.surfaceContainer),
    )
}

@Composable
private fun PreviewTable(block: TableBlock) {
    val table = block.normalized()
    if (table.columnCount == 0) return
    val cell = TextStyle(color = Watch.onSurface, fontSize = 13.sp)
    val header = TextStyle(color = Watch.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Same rule as the watch: widest single-line cell per column, clamped to a readable range.
    val widths = remember(table) {
        (0 until table.columnCount).map { column ->
            val widest = table.rows.withIndex().maxOf { (rowIndex, row) ->
                val style = if (table.hasHeader && rowIndex == 0) header else cell
                measurer.measure(row[column], style, maxLines = 1).size.width
            }
            with(density) { widest.toDp() + 16.dp }.coerceIn(44.dp, 150.dp)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).horizontalScroll(rememberScrollState()),
    ) {
        table.rows.forEachIndexed { index, row ->
            val isHeader = table.hasHeader && index == 0
            val bodyIndex = if (table.hasHeader) index - 1 else index
            val tint = when {
                isHeader -> Watch.primaryContainer
                bodyIndex % 2 == 0 -> Watch.surfaceContainer
                else -> Watch.surfaceContainerHigh
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { column, text ->
                    Box(
                        Modifier
                            .width(widths[column])
                            .fillMaxHeight()
                            .heightIn(min = 26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(tint)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(text, style = if (isHeader) header else cell, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
