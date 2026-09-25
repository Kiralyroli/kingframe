package com.kiroland.gallery.phone

import android.content.Context
import com.kiroland.gallery.shared.PhotoMeta
import com.kiroland.gallery.shared.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

enum class SyncState {
    /** Prepared JPEG waits in the outbox. */
    PENDING_UPLOAD,
    ON_TV,
    /** Only the metadata changed (e.g. place edited). */
    PENDING_META,
    PENDING_DELETE,
    /** The TV lost it (reset/cleared) and the prepared file is gone: share it again. */
    MISSING,
}

@Serializable
data class LocalPhoto(val meta: PhotoMeta, val state: SyncState)

/**
 * The phone's view of the TV album. Only the small thumbnail stays on the phone for good;
 * the prepared full-size JPEG is deleted once the TV has it.
 */
class PhotoRepository(context: Context) {
    private val indexFile = File(context.filesDir, "photos.json")
    private val outboxDir = File(context.filesDir, "outbox").apply { mkdirs() }
    private val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }
    private val serializer = ListSerializer(LocalPhoto.serializer())

    private val _photos = MutableStateFlow(load())
    val photos: StateFlow<List<LocalPhoto>> = _photos

    fun outboxFile(id: String) = File(outboxDir, "$id.jpg")
    fun thumbFile(id: String) = File(thumbDir, "$id.jpg")

    fun get(id: String): LocalPhoto? = _photos.value.firstOrNull { it.meta.id == id }

    /** True when there is nothing to prepare again for this photo. */
    fun isKnown(id: String): Boolean = get(id)?.let {
        when (it.state) {
            SyncState.ON_TV, SyncState.PENDING_META -> true
            SyncState.PENDING_UPLOAD -> outboxFile(id).exists()
            SyncState.PENDING_DELETE, SyncState.MISSING -> false
        }
    } ?: false

    /** Brings a photo back that was queued for deletion, without re-uploading it. */
    @Synchronized
    fun cancelDelete(id: String): Boolean {
        val p = get(id) ?: return false
        if (p.state != SyncState.PENDING_DELETE) return false
        replace(p.copy(state = SyncState.ON_TV))
        return true
    }

    @Synchronized
    fun addPrepared(meta: PhotoMeta) = replace(LocalPhoto(meta, SyncState.PENDING_UPLOAD))

    @Synchronized
    fun markOnTv(id: String) {
        val p = get(id) ?: return
        outboxFile(id).delete()
        replace(p.copy(state = SyncState.ON_TV))
    }

    @Synchronized
    fun markMissing(id: String) {
        val p = get(id) ?: return
        replace(p.copy(state = if (outboxFile(id).exists()) SyncState.PENDING_UPLOAD else SyncState.MISSING))
    }

    @Synchronized
    fun setManualPlace(id: String, place: String?) {
        val p = get(id) ?: return
        val meta = p.meta.copy(manualPlace = place?.trim()?.takeIf { it.isNotEmpty() })
        replace(p.copy(meta = meta, state = if (p.state == SyncState.ON_TV) SyncState.PENDING_META else p.state))
    }

    /** Same place for several photos at once (e.g. a whole trip). */
    @Synchronized
    fun setManualPlace(ids: Collection<String>, place: String?) = ids.forEach { setManualPlace(it, place) }

    /** Favourite / hidden flags; only the metadata goes to the TV. */
    @Synchronized
    fun setFlags(ids: Collection<String>, favorite: Boolean? = null, hidden: Boolean? = null) {
        ids.forEach { id ->
            val p = get(id) ?: return@forEach
            val meta = p.meta.copy(favorite = favorite ?: p.meta.favorite, hidden = hidden ?: p.meta.hidden)
            if (meta != p.meta) replace(p.copy(meta = meta, state = if (p.state == SyncState.ON_TV) SyncState.PENDING_META else p.state))
        }
    }

    @Synchronized
    fun setPlace(id: String, place: String) {
        val p = get(id) ?: return
        replace(p.copy(meta = p.meta.copy(place = place), state = if (p.state == SyncState.ON_TV) SyncState.PENDING_META else p.state))
    }

    /**
     * A photo shared again now carries GPS it lacked before (e.g. the owner turned on
     * location sharing). Only the metadata goes to the TV, not the image.
     */
    @Synchronized
    fun addLocation(id: String, latitude: Double, longitude: Double, place: String?): Boolean {
        val p = get(id) ?: return false
        if (p.meta.latitude != null) return false
        val meta = p.meta.copy(latitude = latitude, longitude = longitude, place = place)
        replace(p.copy(meta = meta, state = if (p.state == SyncState.ON_TV) SyncState.PENDING_META else p.state))
        return true
    }

    @Synchronized
    fun markMetaSent(id: String) {
        val p = get(id) ?: return
        if (p.state == SyncState.PENDING_META) replace(p.copy(state = SyncState.ON_TV))
    }

    /** Queues a delete on the TV; photos the TV never got are dropped right away. */
    @Synchronized
    fun requestDelete(id: String) {
        val p = get(id) ?: return
        if (p.state == SyncState.PENDING_UPLOAD || p.state == SyncState.MISSING) remove(id)
        else replace(p.copy(state = SyncState.PENDING_DELETE))
    }

    /** "Whole album" share: everything not in [keep] goes away from the TV. */
    @Synchronized
    fun keepOnly(keep: Set<String>): Int {
        val drop = _photos.value.filter { it.meta.id !in keep && it.state != SyncState.PENDING_DELETE }
        drop.forEach { requestDelete(it.meta.id) }
        return drop.size
    }

    @Synchronized
    fun remove(id: String) {
        outboxFile(id).delete()
        thumbFile(id).delete()
        _photos.value = _photos.value.filterNot { it.meta.id == id }
        persist()
    }

    @Synchronized
    fun clearAll() {
        outboxDir.listFiles()?.forEach { it.delete() }
        thumbDir.listFiles()?.forEach { it.delete() }
        _photos.value = emptyList()
        persist()
    }

    private fun replace(photo: LocalPhoto) {
        val list = _photos.value
        val i = list.indexOfFirst { it.meta.id == photo.meta.id }
        _photos.value = if (i >= 0) list.toMutableList().also { it[i] = photo } else list + photo
        persist()
    }

    private fun load(): List<LocalPhoto> = runCatching {
        Protocol.json.decodeFromString(serializer, indexFile.readText())
    }.getOrDefault(emptyList())

    private fun persist() {
        val tmp = File(indexFile.path + ".tmp")
        tmp.writeText(Protocol.json.encodeToString(serializer, _photos.value))
        tmp.renameTo(indexFile)
    }
}
