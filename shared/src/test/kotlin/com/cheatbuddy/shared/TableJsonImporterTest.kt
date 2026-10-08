package com.cheatbuddy.shared

import com.cheatbuddy.shared.importing.TableJsonImporter
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.sync.SheetCodec
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TableJsonImporterTest {
    @Test fun `row arrays retain unicode and normalize uneven rows`() {
        val table = TableJsonImporter.parse("""[["Term","Meaning"],["Größe"],[5,true,null]]""", "table-id")
        assertThat(table.id).isEqualTo("table-id")
        assertThat(table.hasHeader).isTrue()
        assertThat(table.rows).containsExactly(listOf("Term", "Meaning", ""), listOf("Größe", "", ""), listOf("5", "true", "")).inOrder()
    }

    @Test fun `object format supports tables without a header and survives watch serialization`() {
        val table = TableJsonImporter.parse("""{"rows":[["a",2],["b",3]],"hasHeader":false}""", "t")
        assertThat(table.hasHeader).isFalse()
        val sheet = Sheet("s", "Imported", 1, listOf(table))
        assertThat(SheetCodec.decodeSheet(SheetCodec.encodeSheet(sheet))).isEqualTo(sheet)
    }

    @Test fun `malformed structures do not silently discard data`() {
        listOf("not json", "{}", "[]", "[[]]", "[1]", "[[{}]]", """{"rows":[[1]],"hasHeader":"false"}""").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { TableJsonImporter.parse(raw, "t") }
        }
    }

    @Test fun `large imports are rejected before creating expensive tables`() {
        val wide = "[[" + List(9) { "1" }.joinToString(",") + "]]"
        val long = "[" + List(201) { "[1]" }.joinToString(",") + "]"
        val large = "[[\"" + "x".repeat(TableJsonImporter.MAX_BYTES) + "\"]]"
        listOf(wide, long, large).forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { TableJsonImporter.parse(raw, "t") }
        }
    }
}
