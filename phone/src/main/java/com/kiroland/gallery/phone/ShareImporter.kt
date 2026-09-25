package com.kiroland.gallery.phone

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.util.Collections

enum class ShareMode {
    /** Add these photos to what is already on the TV. */
    ADD,
    /** These photos are the whole album: add the new ones, remove what is not among them. */
    ALBUM,
}

data class ImportState(
    val total: Int = 0,
    val processed: Int = 0,
    val added: Int = 0,
    val alreadyThere: Int = 0,
    val withPlace: Int = 0,
    val failed: Int = 0,
    val removed: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    val lastError: String? = null,
)

/**
 * Reads and prepares shared photos. Must finish while the share screen is open:
 * the permission to read the shared URIs ends with it.
 */
object ShareImporter {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state

    fun start(context: Context, uris: List<Uri>, mode: ShareMode) {
        if (_state.value.running) return
        val app = context.phoneApp
        val list = uris.distinct()
        _state.value = ImportState(total = list.size, running = true)

        app.appScope.launch {
            val processor = PhotoProcessor(app)
            val target = app.connection.targetSize
            val keep = Collections.synchronizedSet(mutableSetOf<String>())
            val gate = Semaphore(3)

            coroutineScope {
                list.forEach { uri ->
                    launch(Dispatchers.IO) {
                        gate.withPermit { importOne(app, processor, uri, target, keep) }
                    }
                }
            }

            // Only prune when every photo was read, so a read error never deletes from the TV.
            val removed = if (mode == ShareMode.ALBUM && _state.value.failed == 0) app.repo.keepOnly(keep) else 0
            _state.update { it.copy(removed = removed, running = false, finished = true) }
            SyncWorker.syncNow(app)
        }
    }

    fun reset() {
        if (!_state.value.running) _state.value = ImportState()
    }

    private suspend fun importOne(
        app: PhoneApp,
        processor: PhotoProcessor,
        uri: Uri,
        target: Pair<Int, Int>,
        keep: MutableSet<String>,
    ) {
        try {
            val item = processor.inspect(uri)
            keep += item.id
            val hasPlace = item.meta.latitude != null
            val isNew = when {
                app.repo.cancelDelete(item.id) || app.repo.isKnown(item.id) -> {
                    if (hasPlace && app.repo.get(item.id)?.meta?.latitude == null) {
                        val place = processor.placeName(item.meta.latitude!!, item.meta.longitude!!)
                        app.repo.addLocation(item.id, item.meta.latitude!!, item.meta.longitude!!, place)
                    }
                    false
                }
                else -> {
                    val place = if (hasPlace) processor.placeName(item.meta.latitude!!, item.meta.longitude!!) else null
                    val prepared = withContext(Dispatchers.IO) {
                        processor.prepare(item, target, app.repo.outboxFile(item.id), app.repo.thumbFile(item.id))
                    }
                    // Keep what the user set for this photo earlier (it may be a re-share).
                    val earlier = app.repo.get(item.id)?.meta
                    app.repo.addPrepared(
                        prepared.copy(
                            place = place,
                            manualPlace = earlier?.manualPlace,
                            favorite = earlier?.favorite ?: false,
                            hidden = earlier?.hidden ?: false,
                        )
                    )
                    true
                }
            }
            _state.update {
                it.copy(
                    processed = it.processed + 1,
                    added = it.added + if (isNew) 1 else 0,
                    alreadyThere = it.alreadyThere + if (isNew) 0 else 1,
                    withPlace = it.withPlace + if (hasPlace) 1 else 0,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(processed = it.processed + 1, failed = it.failed + 1, lastError = e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
