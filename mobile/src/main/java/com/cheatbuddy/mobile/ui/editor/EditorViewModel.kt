package com.cheatbuddy.mobile.ui.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatbuddy.mobile.data.ImageRepository
import com.cheatbuddy.mobile.data.SheetRepository
import com.cheatbuddy.mobile.data.StorageFullException
import com.cheatbuddy.mobile.di.AppScope
import com.cheatbuddy.shared.model.Block
import com.cheatbuddy.shared.model.Sheet
import com.cheatbuddy.shared.model.TableBlock
import com.cheatbuddy.shared.model.TextBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.cheatbuddy.shared.importing.TableJsonImporter
import com.cheatbuddy.shared.sync.SheetCodec
import com.cheatbuddy.mobile.sync.PhoneSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Holds the sheet being edited. Edits apply instantly to [sheet] (driving the live watch preview) and are
 * autosaved shortly after the user pauses; each save schedules a push to the watch.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val sheets: SheetRepository,
    private val images: ImageRepository,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val sheetId: String = checkNotNull(savedStateHandle["sheetId"])

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val edits = MutableStateFlow<Sheet?>(null)
    @Volatile private var dirty = false

    init {
        viewModelScope.launch {
            _sheet.value = sheets.get(sheetId)
            edits.filterNotNull().debounce(AUTOSAVE_MS).collect { save(it) }
        }
    }

    private fun edit(transform: (Sheet) -> Sheet) {
        val current = _sheet.value ?: return
        val next = transform(current)
        _sheet.value = next
        dirty = true
        edits.value = next
    }

    private suspend fun save(sheet: Sheet) {
        if (!dirty) return
        try {
            dirty = false
            sheets.save(sheet)
        } catch (e: Exception) {
            dirty = true
            _messages.tryEmit(
                if (e.isOutOfSpace()) "Your phone is out of storage. Free up space to save changes." else "Could not save: ${e.message}",
            )
        }
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun updateBlock(block: Block) = edit { sheet ->
        sheet.copy(blocks = sheet.blocks.map { if (it.id == block.id) block else it })
    }

    fun removeBlock(id: String) = edit { sheet -> sheet.copy(blocks = sheet.blocks.filterNot { it.id == id }) }

    fun moveBlock(from: Int, to: Int) = edit { sheet ->
        if (from !in sheet.blocks.indices || to !in sheet.blocks.indices) return@edit sheet
        sheet.copy(blocks = sheet.blocks.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun addText() = edit { it.copy(blocks = it.blocks + TextBlock(newId(), "")) }

    fun addTable() = edit {
        it.copy(blocks = it.blocks + TableBlock(newId(), rows = listOf(listOf("Term", "Meaning"), listOf("", ""))))
    }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val block = images.import(uri)
                edit { it.copy(blocks = it.blocks + block) }
            } catch (_: StorageFullException) {
                _messages.tryEmit("Not enough storage to add this image.")
            } catch (e: IOException) {
                _messages.tryEmit(e.message ?: "This image could not be added.")
            } finally {
                _importing.value = false
            }
        }
    }

    fun importTableJson(blockId: String, raw: String) = importTable(blockId) { raw }

    fun importTableFile(blockId: String, uri: Uri) = importTable(blockId) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open this JSON file.")
        input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= TableJsonImporter.MAX_BYTES) { "JSON must be smaller than 90 KB." }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun importTable(blockId: String, read: () -> String) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val table = withContext(Dispatchers.IO) { TableJsonImporter.parse(read(), blockId) }
                val current = _sheet.value ?: return@launch
                if (current.blocks.none { it.id == blockId && it is TableBlock }) return@launch
                val next = current.copy(blocks = current.blocks.map { if (it.id == blockId) table else it })
                val fits = withContext(Dispatchers.Default) {
                    SheetCodec.encodeSheet(next).encodeToByteArray().size <= PhoneSyncEngine.MAX_ITEM_BYTES
                }
                require(fits) { "This sheet is too large to sync. Import the table into a smaller sheet." }
                updateBlock(table)
                _messages.tryEmit("Imported ${table.rows.size} rows and ${table.columnCount} columns.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Could not import this table.")
            } finally {
                _importing.value = false
            }
        }
    }

    fun newCameraUri(): Uri = images.newCameraUri()

    fun imageFile(imageId: String): File? = images.fullFile(imageId).takeIf { it.isFile }

    override fun onCleared() {
        // Flush a pending autosave when the editor closes.
        val latest = _sheet.value
        if (dirty && latest != null) appScope.launch { sheets.save(latest) }
    }

    private fun newId() = UUID.randomUUID().toString()

    private fun Throwable.isOutOfSpace() =
        this is StorageFullException || this is android.database.sqlite.SQLiteFullException ||
            message.orEmpty().contains("ENOSPC")

    private companion object {
        const val AUTOSAVE_MS = 400L
    }
}
