package com.cheatbuddy.wear.data

import com.cheatbuddy.shared.model.Block
import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.sync.SheetCodec
import com.cheatbuddy.shared.sync.SyncPayloadException
import com.cheatbuddy.wear.data.db.SheetEntity

/** Lightweight data for the home list. */
data class SheetSummary(
    val id: String,
    val title: String,
    val blockCount: Int,
    val firstImageId: String?,
    val previewText: String?,
)

fun Sheet.toEntity(position: Int): SheetEntity = SheetEntity(
    id = id,
    title = title,
    updatedAt = updatedAt,
    position = position,
    blocksJson = SheetCodec.encodeBlocks(blocks),
    blockCount = blocks.size,
    firstImageId = blocks.firstNotNullOfOrNull { (it as? ImageBlock)?.imageId },
    previewText = blocks.firstNotNullOfOrNull { block ->
        when (block) {
            is TextBlock -> block.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            is TableBlock -> block.rows.firstOrNull()?.filter { it.isNotBlank() }?.joinToString(" · ")
            else -> null
        }?.take(80)
    },
)

fun SheetEntity.toSummary() = SheetSummary(id, title, blockCount, firstImageId, previewText)

fun SheetEntity.decodeBlocks(): List<Block> = try {
    SheetCodec.decodeBlocks(blocksJson)
} catch (_: SyncPayloadException) {
    emptyList()
}

fun SheetEntity.toSheet(): Sheet = Sheet(id = id, title = title, updatedAt = updatedAt, blocks = decodeBlocks())
