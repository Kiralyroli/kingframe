package com.kiroland.gallery.tv

import android.util.Base64
import com.kiroland.gallery.shared.PairRequest
import com.kiroland.gallery.shared.PairResponse
import com.kiroland.gallery.shared.PhotoList
import com.kiroland.gallery.shared.PhotoMeta
import com.kiroland.gallery.shared.Protocol
import com.kiroland.gallery.shared.TvInfo
import com.kiroland.gallery.shared.TvSettings
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.serialization.KSerializer

/** HTTP endpoint the phone app talks to; see [Protocol] for the routes. */
class GalleryServer(
    port: Int,
    private val app: TvApp,
    private val screenSize: () -> Pair<Int, Int>,
) : NanoHTTPD(port) {

    private val store get() = app.store
    private val prefs get() = app.prefs

    override fun serve(session: IHTTPSession): Response = try {
        route(session)
    } catch (e: Exception) {
        text(Status.INTERNAL_ERROR, e.message ?: e.javaClass.simpleName)
    }

    private fun route(s: IHTTPSession): Response {
        val parts = s.uri.trim('/').split('/').filter { it.isNotEmpty() }
        if (s.method == Method.POST && parts == listOf("pair")) return pair(s)

        val token = s.headers["authorization"]?.removePrefix("Bearer ")?.trim()
        if (!prefs.isAuthorized(token)) return text(Status.UNAUTHORIZED, "Nincs párosítva")

        return when {
            s.method == Method.GET && parts == listOf("info") -> {
                val (w, h) = screenSize()
                json(TvInfo.serializer(), TvInfo(prefs.tvName, Protocol.VERSION, w, h, store.photos.value.size, store.freeBytes))
            }
            s.method == Method.GET && parts == listOf("photos") ->
                json(PhotoList.serializer(), PhotoList(store.photos.value.map { it.id }))

            s.method == Method.PUT && parts.size == 2 && parts[0] == "photos" -> {
                val id = parts[1]
                if (!PhotoStore.isValidId(id)) return text(Status.BAD_REQUEST, "Hibás azonosító")
                val metaHeader = s.headers[Protocol.HEADER_META.lowercase()]
                    ?: return text(Status.BAD_REQUEST, "Hiányzó metaadat")
                val meta = Protocol.json.decodeFromString(
                    PhotoMeta.serializer(),
                    String(Base64.decode(metaHeader, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8),
                ).copy(id = id)
                val length = contentLength(s)
                if (length <= 0 || length > MAX_IMAGE_BYTES) return text(Status.BAD_REQUEST, "Hibás méret")
                store.put(meta, s.inputStream, length)
                empty()
            }
            s.method == Method.PUT && parts.size == 3 && parts[0] == "photos" && parts[2] == "meta" -> {
                val meta = Protocol.json.decodeFromString(PhotoMeta.serializer(), body(s)).copy(id = parts[1])
                if (store.updateMeta(meta)) empty() else text(Status.NOT_FOUND, "Nincs ilyen kép")
            }
            s.method == Method.GET && parts == listOf("settings") ->
                json(TvSettings.serializer(), prefs.snapshot())
            s.method == Method.PUT && parts == listOf("settings") -> {
                prefs.apply(Protocol.json.decodeFromString(TvSettings.serializer(), body(s)))
                empty()
            }
            s.method == Method.DELETE && parts.size == 2 && parts[0] == "photos" -> {
                store.delete(parts[1]); empty()
            }
            else -> text(Status.NOT_FOUND, "Ismeretlen kérés")
        }
    }

    private fun pair(s: IHTTPSession): Response {
        val req = Protocol.json.decodeFromString(PairRequest.serializer(), body(s))
        val token = prefs.pair(req.pin.trim()) ?: return text(Status.FORBIDDEN, "Hibás kód")
        return json(PairResponse.serializer(), PairResponse(token, prefs.tvName))
    }

    private fun contentLength(s: IHTTPSession) = s.headers["content-length"]?.toLongOrNull() ?: -1

    private fun body(s: IHTTPSession): String {
        val length = contentLength(s)
        require(length in 0..MAX_JSON_BYTES) { "Hibás méret" }
        val bytes = ByteArray(length.toInt())
        var read = 0
        while (read < bytes.size) {
            val n = s.inputStream.read(bytes, read, bytes.size - read)
            if (n < 0) break
            read += n
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun <T> json(serializer: KSerializer<T>, value: T): Response =
        newFixedLengthResponse(Status.OK, "application/json", Protocol.json.encodeToString(serializer, value))

    private fun text(status: Status, msg: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)

    private fun empty(): Response = newFixedLengthResponse(Status.NO_CONTENT, "text/plain", "")

    companion object {
        private const val MAX_IMAGE_BYTES = 60L * 1024 * 1024
        private const val MAX_JSON_BYTES = 64L * 1024
    }
}
