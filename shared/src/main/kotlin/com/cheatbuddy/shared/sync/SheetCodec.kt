package com.cheatbuddy.shared.sync

import com.cheatbuddy.shared.model.Block
import com.cheatbuddy.shared.model.Sheet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** One entry of the [SyncManifest]: which sheet exists on the phone and in which version. */
@Serializable
data class ManifestEntry(
    val id: String,
    val updatedAt: Long,
)

/** Complete, ordered list of sheets on the phone. The watch mirrors exactly this set. */
@Serializable
data class SyncManifest(
    @SerialName("v") val schemaVersion: Int = SyncProtocol.SCHEMA_VERSION,
    val revision: String,
    val entries: List<ManifestEntry>,
) {
    companion object {
        fun of(sheets: List<Sheet>): SyncManifest {
            val entries = sheets.map { ManifestEntry(it.id, it.updatedAt) }
            return SyncManifest(revision = revisionOf(entries), entries = entries)
        }

        /** Stable content hash of the manifest entries (order matters). */
        fun revisionOf(entries: List<ManifestEntry>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            entries.forEach { digest.update("${it.id}:${it.updatedAt};".toByteArray()) }
            return digest.digest().take(12).joinToString("") { "%02x".format(it) }
        }
    }
}

@Serializable
private data class SheetEnvelope(
    @SerialName("v") val schemaVersion: Int = SyncProtocol.SCHEMA_VERSION,
    val sheet: Sheet,
)

/** Thrown when a payload cannot be decoded. Callers skip the item instead of crashing sync. */
class SyncPayloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** JSON (de)serialization for everything that crosses the Data Layer or is stored as JSON in Room. */
object SheetCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
        coerceInputValues = true
    }

    private val blockListSerializer = ListSerializer(Block.serializer())

    fun encodeSheet(sheet: Sheet): String = json.encodeToString(SheetEnvelope.serializer(), SheetEnvelope(sheet = sheet))

    fun decodeSheet(raw: String): Sheet {
        val envelope = decode(raw) { json.decodeFromString(SheetEnvelope.serializer(), it) }
        checkSchema(envelope.schemaVersion)
        return envelope.sheet
    }

    fun encodeManifest(manifest: SyncManifest): String = json.encodeToString(SyncManifest.serializer(), manifest)

    fun decodeManifest(raw: String): SyncManifest {
        val manifest = decode(raw) { json.decodeFromString(SyncManifest.serializer(), it) }
        checkSchema(manifest.schemaVersion)
        return manifest
    }

    fun encodeBlocks(blocks: List<Block>): String = json.encodeToString(blockListSerializer, blocks)

    fun decodeBlocks(raw: String): List<Block> = decode(raw) { json.decodeFromString(blockListSerializer, it) }

    private inline fun <T> decode(raw: String, block: (String) -> T): T = try {
        block(raw)
    } catch (e: SerializationException) {
        throw SyncPayloadException("Malformed payload: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw SyncPayloadException("Malformed payload: ${e.message}", e)
    }

    private fun checkSchema(version: Int) {
        if (version > SyncProtocol.SCHEMA_VERSION) {
            throw SyncPayloadException("Payload schema $version is newer than supported ${SyncProtocol.SCHEMA_VERSION}")
        }
    }
}
