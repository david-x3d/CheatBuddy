package com.cheatbuddy.shared

import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.model.TextSize
import com.cheatbuddy.shared.sync.SheetCodec
import com.cheatbuddy.shared.sync.SyncManifest
import com.cheatbuddy.shared.sync.SyncPayloadException
import com.cheatbuddy.shared.sync.SyncProtocol
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SheetCodecTest {

    private val sheet = Sheet(
        id = "s1",
        title = "Git",
        updatedAt = 1_700_000_000_000,
        blocks = listOf(
            TextBlock("b1", "Undo last commit", bold = true, size = TextSize.Large),
            ImageBlock("b2", imageId = "img-1", width = 400, height = 300),
            TableBlock("b3", rows = listOf(listOf("Cmd", "Effect"), listOf("git reset --soft HEAD~1", "keep changes"))),
        ),
    )

    @Test
    fun `sheet round-trips with all block types`() {
        val decoded = SheetCodec.decodeSheet(SheetCodec.encodeSheet(sheet))
        assertThat(decoded).isEqualTo(sheet)
    }

    @Test
    fun `blocks are tagged with a stable type discriminator`() {
        val json = SheetCodec.encodeBlocks(sheet.blocks)
        assertThat(json).contains("\"type\":\"text\"")
        assertThat(json).contains("\"type\":\"image\"")
        assertThat(json).contains("\"type\":\"table\"")
        assertThat(json).contains("\"size\":\"large\"")
        assertThat(SheetCodec.decodeBlocks(json)).isEqualTo(sheet.blocks)
    }

    @Test
    fun `unknown fields from newer phones are ignored`() {
        val raw = """{"v":1,"sheet":{"id":"s","title":"T","updatedAt":5,"color":"red",
            "blocks":[{"type":"text","id":"b","text":"hi","font":"serif"}]}}"""
        val decoded = SheetCodec.decodeSheet(raw)
        assertThat(decoded.blocks.single()).isEqualTo(TextBlock("b", "hi"))
    }

    @Test
    fun `newer schema version is rejected`() {
        val raw = SheetCodec.encodeSheet(sheet).replace("\"v\":1", "\"v\":${SyncProtocol.SCHEMA_VERSION + 1}")
        assertThrows(SyncPayloadException::class.java) { SheetCodec.decodeSheet(raw) }
    }

    @Test
    fun `malformed json raises SyncPayloadException`() {
        assertThrows(SyncPayloadException::class.java) { SheetCodec.decodeSheet("{not json") }
        assertThrows(SyncPayloadException::class.java) { SheetCodec.decodeManifest("""{"revision":1}""") }
    }

    @Test
    fun `manifest round-trips and revision depends on content and order`() {
        val a = sheet
        val b = sheet.copy(id = "s2", updatedAt = 7)
        val manifest = SyncManifest.of(listOf(a, b))
        assertThat(SheetCodec.decodeManifest(SheetCodec.encodeManifest(manifest))).isEqualTo(manifest)

        assertThat(SyncManifest.of(listOf(a, b)).revision).isEqualTo(manifest.revision)
        assertThat(SyncManifest.of(listOf(b, a)).revision).isNotEqualTo(manifest.revision)
        assertThat(SyncManifest.of(listOf(a, b.copy(updatedAt = 8))).revision).isNotEqualTo(manifest.revision)
    }

    @Test
    fun `table normalization pads short rows`() {
        val table = TableBlock("t", rows = listOf(listOf("a", "b", "c"), listOf("d"))).normalized()
        assertThat(table.rows[1]).containsExactly("d", "", "").inOrder()
        assertThat(table.headerRow).containsExactly("a", "b", "c").inOrder()
        assertThat(table.bodyRows).hasSize(1)
    }

    @Test
    fun `paths round-trip and reject unsafe ids`() {
        assertThat(SyncProtocol.sheetIdFromPath(SyncProtocol.sheetPath("abc-1"))).isEqualTo("abc-1")
        assertThat(SyncProtocol.imageIdFromPath(SyncProtocol.imagePath("img_2"))).isEqualTo("img_2")
        assertThat(SyncProtocol.imageIdFromPath("/cheatbuddy/image/../../etc")).isNull()
        assertThat(SyncProtocol.sheetIdFromPath("/other/sheet/x")).isNull()
    }
}
