package com.cheatbuddy.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tile
import androidx.wear.tiles.timeline
import androidx.wear.tiles.timelineEntry
import com.cheatbuddy.wear.MainActivity
import com.cheatbuddy.wear.data.SheetRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Tile that shows the last viewed sheet and opens it with one tap. Reads only local data. */
@AndroidEntryPoint
class LastSheetTileService : Material3TileService() {

    @Inject lateinit var repository: SheetRepository

    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val lastId = repository.lastViewedSheetId.first()
        val sheet = lastId?.let { repository.getSheet(it) }
        val openSheet = clickable(launchAction(sheet?.id))
        val openApp = clickable(launchAction(null))

        val layout = primaryLayout(
            titleSlot = { text("Cheat Buddy".layoutString) },
            mainSlot = {
                if (sheet != null) {
                    titleCard(
                        onClick = openSheet,
                        title = { text(sheet.title.layoutString, maxLines = 2) },
                        content = { text("${sheet.blocks.size} blocks · tap to open".layoutString) },
                    )
                } else {
                    text("Open a sheet to pin it here".layoutString, maxLines = 3)
                }
            },
            bottomSlot = {
                textEdgeButton(onClick = openApp) { text("All sheets".layoutString) }
            },
        )
        return tile(timeline = timeline(timelineEntry(layout)))
    }

    private fun launchAction(sheetId: String?): ActionBuilders.LaunchAction {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)
        if (sheetId != null) {
            activity.addKeyToExtraMapping(
                MainActivity.EXTRA_SHEET_ID,
                ActionBuilders.AndroidStringExtra.Builder().setValue(sheetId).build(),
            )
        }
        return ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build()
    }
}
