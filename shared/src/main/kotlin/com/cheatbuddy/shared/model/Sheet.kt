package com.cheatbuddy.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A cheat sheet: an ordered list of [Block]s. The phone owns it; the watch keeps a read-only copy. */
@Serializable
data class Sheet(
    val id: String,
    val title: String,
    /** Epoch millis of the last edit. Doubles as the sheet's version for sync. */
    val updatedAt: Long,
    val blocks: List<Block> = emptyList(),
) {
    /** All image ids referenced by this sheet, in block order. */
    val imageIds: List<String> get() = blocks.filterIsInstance<ImageBlock>().map { it.imageId }
}

@Serializable
sealed interface Block {
    val id: String
}

@Serializable
@SerialName("image")
data class ImageBlock(
    override val id: String,
    /** Id of the image file. Image content never changes for an id, so ids are safe cache keys. */
    val imageId: String,
    /** Pixel size of the watch-sized image, used to reserve layout space before decoding. */
    val width: Int,
    val height: Int,
) : Block {
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

@Serializable
@SerialName("text")
data class TextBlock(
    override val id: String,
    val text: String,
    val bold: Boolean = false,
    val size: TextSize = TextSize.Medium,
) : Block

@Serializable
enum class TextSize {
    @SerialName("small") Small,
    @SerialName("medium") Medium,
    @SerialName("large") Large,
}

@Serializable
@SerialName("table")
data class TableBlock(
    override val id: String,
    /** Row-major cells. Rows are normalized to the same column count by [normalized]. */
    val rows: List<List<String>>,
    /** If true, the first row is rendered as a (sticky) header. */
    val hasHeader: Boolean = true,
) : Block {
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    /** Returns a copy where every row has exactly [columnCount] cells. */
    fun normalized(): TableBlock {
        val columns = columnCount
        if (rows.all { it.size == columns }) return this
        return copy(rows = rows.map { row -> row + List(columns - row.size) { "" } })
    }

    val headerRow: List<String>? get() = if (hasHeader) rows.firstOrNull() else null
    val bodyRows: List<List<String>> get() = if (hasHeader) rows.drop(1) else rows
}
