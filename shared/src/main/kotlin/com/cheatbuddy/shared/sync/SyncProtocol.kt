package com.cheatbuddy.shared.sync

/**
 * Wearable Data Layer contract shared by phone and watch.
 *
 * - Sheets travel as DataItems ([sheetPath]) holding the JSON from [SheetCodec].
 * - [MANIFEST_PATH] lists every sheet the phone owns, so the watch can detect deletions even if it missed the
 *   delete events.
 * - Images are pulled by the watch over a ChannelClient channel opened on [imagePath].
 * - The watch acknowledges an applied manifest revision via a message on [ACK_PATH].
 */
object SyncProtocol {
    const val ROOT = "/cheatbuddy"
    const val SHEET_PREFIX = "$ROOT/sheet/"
    const val MANIFEST_PATH = "$ROOT/manifest"
    const val IMAGE_PREFIX = "$ROOT/image/"
    const val ACK_PATH = "$ROOT/ack"

    /** DataMap keys. */
    const val KEY_JSON = "json"
    const val KEY_UPDATED_AT = "updatedAt"

    /** Capabilities declared in each app's `res/values/wear.xml`. */
    const val CAPABILITY_PHONE = "cheatbuddy_phone"
    const val CAPABILITY_WATCH = "cheatbuddy_watch"

    /** Max edge (px) of images sent to the watch. */
    const val WATCH_IMAGE_MAX_EDGE = 400

    /** Bumped on incompatible payload changes. Older watches ignore payloads with a higher major version. */
    const val SCHEMA_VERSION = 1

    fun sheetPath(sheetId: String) = "$SHEET_PREFIX$sheetId"
    fun imagePath(imageId: String) = "$IMAGE_PREFIX$imageId"

    fun sheetIdFromPath(path: String): String? =
        path.takeIf { it.startsWith(SHEET_PREFIX) }?.removePrefix(SHEET_PREFIX)?.takeIf(::isValidId)

    fun imageIdFromPath(path: String): String? =
        path.takeIf { it.startsWith(IMAGE_PREFIX) }?.removePrefix(IMAGE_PREFIX)?.takeIf(::isValidId)

    /** Ids are UUID-like; reject anything that could escape a directory when used as a file name. */
    fun isValidId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}
