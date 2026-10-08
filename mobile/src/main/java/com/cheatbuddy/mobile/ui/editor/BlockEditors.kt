package com.cheatbuddy.mobile.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.model.TextSize
import java.io.File

@Composable
fun TextBlockEditor(block: TextBlock, onChange: (TextBlock) -> Unit) {
    val base = when (block.size) {
        TextSize.Small -> MaterialTheme.typography.bodyMedium
        TextSize.Medium -> MaterialTheme.typography.bodyLarge
        TextSize.Large -> MaterialTheme.typography.titleLarge
    }
    Column {
        TextField(
            value = block.text,
            onValueChange = { onChange(block.copy(text = it)) },
            placeholder = { Text("Write something…") },
            textStyle = base.copy(fontWeight = if (block.bold) FontWeight.Bold else FontWeight.Normal),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            FilterChip(
                selected = block.bold,
                onClick = { onChange(block.copy(bold = !block.bold)) },
                label = { Text("Bold") },
                leadingIcon = { Icon(Icons.Rounded.FormatBold, null, Modifier.size(18.dp)) },
            )
            SingleChoiceSegmentedButtonRow {
                TextSize.entries.forEachIndexed { index, size ->
                    SegmentedButton(
                        selected = block.size == size,
                        onClick = { onChange(block.copy(size = size)) },
                        shape = SegmentedButtonDefaults.itemShape(index, TextSize.entries.size),
                        icon = {},
                        label = { Text(size.label) },
                    )
                }
            }
        }
    }
}

private val TextSize.label get() = when (this) {
    TextSize.Small -> "S"
    TextSize.Medium -> "M"
    TextSize.Large -> "L"
}

@Composable
fun ImageBlockEditor(block: ImageBlock, file: File?) {
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .aspectRatio(block.aspectRatio.coerceIn(0.6f, 3f))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

private val CellWidth = 128.dp

@Composable
fun TableBlockEditor(block: TableBlock, onChange: (TableBlock) -> Unit) {
    val table = block.normalized()
    val colors = MaterialTheme.colorScheme

    fun setCell(row: Int, column: Int, value: String) = onChange(
        table.copy(rows = table.rows.mapIndexed { r, cells -> if (r == row) cells.mapIndexed { c, v -> if (c == column) value else v } else cells }),
    )

    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Header row", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = table.hasHeader, onCheckedChange = { onChange(table.copy(hasHeader = it)) })
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Column remove buttons.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(table.columnCount) { column ->
                    Box(Modifier.width(CellWidth), contentAlignment = Alignment.Center) {
                        if (table.columnCount > 1) {
                            IconButton(
                                onClick = { onChange(table.copy(rows = table.rows.map { it.filterIndexed { c, _ -> c != column } })) },
                                modifier = Modifier.size(28.dp),
                            ) { Icon(Icons.Rounded.Close, "Remove column", Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            table.rows.forEachIndexed { rowIndex, cells ->
                val isHeader = table.hasHeader && rowIndex == 0
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    cells.forEachIndexed { column, value ->
                        BasicTextField(
                            value = value,
                            onValueChange = { setCell(rowIndex, column, it) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isHeader) colors.onPrimaryContainer else colors.onSurface,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(colors.primary),
                            modifier = Modifier
                                .width(CellWidth)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isHeader -> colors.primaryContainer
                                        rowIndex % 2 == 0 -> colors.surfaceContainerHighest
                                        else -> colors.surfaceContainerHigh
                                    },
                                )
                                .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        )
                    }
                    if (table.rows.size > 1) {
                        IconButton(
                            onClick = { onChange(table.copy(rows = table.rows.filterIndexed { r, _ -> r != rowIndex })) },
                            modifier = Modifier.size(32.dp),
                        ) { Icon(Icons.Rounded.Close, "Remove row", Modifier.size(16.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onChange(table.copy(rows = table.rows + listOf(List(table.columnCount) { "" }))) }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Row")
            }
            FilledTonalButton(
                onClick = { onChange(table.copy(rows = table.rows.map { it + "" })) },
                enabled = table.columnCount < MAX_COLUMNS,
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Column")
            }
        }
    }
}

private const val MAX_COLUMNS = 8
