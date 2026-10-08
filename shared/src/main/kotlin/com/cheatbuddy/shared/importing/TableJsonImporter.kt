package com.cheatbuddy.shared.importing

import com.cheatbuddy.shared.model.TableBlock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Strict, bounded import for user-supplied tables, independent of the sync envelope. */
object TableJsonImporter {
    const val MAX_BYTES = 90 * 1024
    const val MAX_ROWS = 200
    const val MAX_COLUMNS = 8

    fun parse(raw: String, blockId: String): TableBlock {
        require(raw.length <= MAX_BYTES && raw.encodeToByteArray().size <= MAX_BYTES) {
            "JSON must be smaller than 90 KB. Split large tables into smaller ones."
        }
        val root = try {
            Json.parseToJsonElement(raw.removePrefix("\uFEFF"))
        } catch (_: SerializationException) {
            throw IllegalArgumentException("Invalid JSON. Use an array of rows or an object with rows and hasHeader.")
        }
        val rowsJson: JsonArray
        val hasHeader: Boolean
        when (root) {
            is JsonArray -> { rowsJson = root; hasHeader = true }
            is JsonObject -> {
                rowsJson = root["rows"] as? JsonArray
                    ?: throw IllegalArgumentException("The JSON object must contain a rows array.")
                hasHeader = root["hasHeader"]?.let {
                    (it as? JsonPrimitive)?.takeUnless { value -> value.isString }?.booleanOrNull
                        ?: throw IllegalArgumentException("hasHeader must be true or false.")
                } ?: true
            }
            else -> throw IllegalArgumentException("Use an array of rows or an object containing rows.")
        }
        require(rowsJson.isNotEmpty()) { "The table needs at least one row." }
        require(rowsJson.size <= MAX_ROWS) { "Import up to $MAX_ROWS rows at a time." }
        val rows = rowsJson.mapIndexed { index, element ->
            val row = element as? JsonArray
                ?: throw IllegalArgumentException("Row ${index + 1} must be an array of cells.")
            require(row.size in 1..MAX_COLUMNS) { "Row ${index + 1} must have 1 to $MAX_COLUMNS cells." }
            row.map { cell ->
                when (cell) {
                    JsonNull -> ""
                    is JsonPrimitive -> cell.content
                    else -> throw IllegalArgumentException("Cells must contain text, numbers, booleans or null.")
                }
            }
        }
        return TableBlock(blockId, rows, hasHeader).normalized()
    }
}
