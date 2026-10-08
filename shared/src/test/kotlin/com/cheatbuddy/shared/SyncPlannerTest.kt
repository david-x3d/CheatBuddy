package com.cheatbuddy.shared

import com.cheatbuddy.shared.model.ImageBlock
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TextBlock
import com.cheatbuddy.shared.sync.LocalSheetInfo
import com.cheatbuddy.shared.sync.ManifestEntry
import com.cheatbuddy.shared.sync.SyncManifest
import com.cheatbuddy.shared.sync.SyncPlanner
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncPlannerTest {

    private fun sheet(id: String, updatedAt: Long, vararg images: String) = Sheet(
        id = id,
        title = id,
        updatedAt = updatedAt,
        blocks = listOf(TextBlock("t-$id", "x")) + images.map { ImageBlock("b-$it", it, 10, 10) },
    )

    private fun local(vararg sheets: Sheet) = sheets.associate { it.id to LocalSheetInfo(it.updatedAt, it.imageIds) }

    @Test
    fun `first sync inserts everything and requests all images`() {
        val a = sheet("a", 1, "i1")
        val b = sheet("b", 2, "i2", "i3")
        val plan = SyncPlanner.planPull(
            manifest = SyncManifest.of(listOf(a, b)),
            remoteSheets = mapOf("a" to a, "b" to b),
            local = emptyMap(),
            localImageIds = emptySet(),
        )
        assertThat(plan.upserts).containsExactly(a, b)
        assertThat(plan.deletes).isEmpty()
        assertThat(plan.order).containsExactly("a", "b").inOrder()
        assertThat(plan.missingImageIds).containsExactly("i1", "i2", "i3")
        assertThat(plan.pendingSheetIds).isEmpty()
        assertThat(plan.isUpToDate).isFalse()
    }

    @Test
    fun `unchanged state produces an empty plan`() {
        val a = sheet("a", 1, "i1")
        val plan = SyncPlanner.planPull(SyncManifest.of(listOf(a)), mapOf("a" to a), local(a), setOf("i1"))
        assertThat(plan.isUpToDate).isTrue()
        assertThat(plan.orphanImageIds).isEmpty()
    }

    @Test
    fun `sheets missing from the manifest are deleted and their images become orphans`() {
        val a = sheet("a", 1, "i1")
        val b = sheet("b", 1, "i2")
        val plan = SyncPlanner.planPull(SyncManifest.of(listOf(a)), mapOf("a" to a), local(a, b), setOf("i1", "i2"))
        assertThat(plan.deletes).containsExactly("b")
        assertThat(plan.orphanImageIds).containsExactly("i2")
        assertThat(plan.missingImageIds).isEmpty()
    }

    @Test
    fun `edited sheet replaces the local copy and swaps images`() {
        val old = sheet("a", 1, "i1")
        val new = sheet("a", 2, "i9")
        val plan = SyncPlanner.planPull(SyncManifest.of(listOf(new)), mapOf("a" to new), local(old), setOf("i1"))
        assertThat(plan.upserts).containsExactly(new)
        assertThat(plan.missingImageIds).containsExactly("i9")
        assertThat(plan.orphanImageIds).containsExactly("i1")
    }

    @Test
    fun `manifest ahead of sheet item keeps local copy and marks it pending`() {
        val stored = sheet("a", 1, "i1")
        val manifest = SyncManifest(revision = "r", entries = listOf(ManifestEntry("a", 5), ManifestEntry("new", 6)))
        val plan = SyncPlanner.planPull(manifest, mapOf("a" to stored), local(stored), setOf("i1"))
        assertThat(plan.upserts).isEmpty()
        assertThat(plan.deletes).isEmpty()
        assertThat(plan.pendingSheetIds).containsExactly("a", "new")
        // Images of the kept copy must not be garbage collected.
        assertThat(plan.orphanImageIds).isEmpty()
        assertThat(plan.order).containsExactly("a")
    }

    @Test
    fun `push plan writes only changed sheets and removes deleted ones`() {
        val a = sheet("a", 1)
        val b = sheet("b", 3)
        val plan = SyncPlanner.planPush(listOf(a, b), published = mapOf("a" to 1, "b" to 2, "gone" to 1))
        assertThat(plan.upserts).containsExactly(b)
        assertThat(plan.deletes).containsExactly("gone")
        assertThat(plan.manifest.entries.map { it.id }).containsExactly("a", "b").inOrder()
    }
}
