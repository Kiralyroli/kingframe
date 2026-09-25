package com.kiroland.gallery.tv

import android.content.Context
import com.kiroland.gallery.shared.PhotoMeta
import com.kiroland.gallery.shared.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream

/**
 * Photos received from the phone: photos/<id>.jpg plus photos/<id>.json.
 * The flow updates immediately so a running slideshow picks up changes live.
 */
class PhotoStore(context: Context) {
    private val dir = File(context.filesDir, "photos").apply { mkdirs() }

    private val _photos = MutableStateFlow(load())
    val photos: StateFlow<List<PhotoMeta>> = _photos

    fun imageFile(id: String) = File(dir, "$id.jpg")
    private fun metaFile(id: String) = File(dir, "$id.json")

    val freeBytes: Long get() = dir.usableSpace

    private fun load(): List<PhotoMeta> =
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { Protocol.json.decodeFromString<PhotoMeta>(f.readText()) }.getOrNull()
                ?.takeIf { imageFile(it.id).exists() }
        }.sortedBy { it.addedAt }

    @Synchronized
    fun put(meta: PhotoMeta, image: InputStream, length: Long) {
        require(isValidId(meta.id))
        val tmp = File(dir, "${meta.id}.jpg.part")
        tmp.outputStream().use { out ->
            var remaining = length
            val buf = ByteArray(64 * 1024)
            while (remaining > 0) {
                val n = image.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                remaining -= n
            }
            check(remaining == 0L) { "Hiányos feltöltés" }
        }
        check(tmp.renameTo(imageFile(meta.id)) || (imageFile(meta.id).delete() && tmp.renameTo(imageFile(meta.id))))
        writeMeta(meta)
    }

    @Synchronized
    fun updateMeta(meta: PhotoMeta): Boolean {
        if (!isValidId(meta.id) || !imageFile(meta.id).exists()) return false
        writeMeta(meta)
        return true
    }

    private fun writeMeta(meta: PhotoMeta) {
        metaFile(meta.id).writeText(Protocol.json.encodeToString(PhotoMeta.serializer(), meta))
        _photos.value = _photos.value.filterNot { it.id == meta.id } + meta
    }

    @Synchronized
    fun delete(id: String) {
        if (!isValidId(id)) return
        imageFile(id).delete()
        metaFile(id).delete()
        _photos.value = _photos.value.filterNot { it.id == id }
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        _photos.value = emptyList()
    }

    companion object {
        private val ID = Regex("[A-Za-z0-9_-]{1,64}")
        fun isValidId(id: String) = ID.matches(id)
    }
}
