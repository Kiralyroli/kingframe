package com.kiroland.gallery.phone

import android.util.Base64
import com.kiroland.gallery.shared.PairRequest
import com.kiroland.gallery.shared.PairResponse
import com.kiroland.gallery.shared.PhotoList
import com.kiroland.gallery.shared.PhotoMeta
import com.kiroland.gallery.shared.Protocol
import com.kiroland.gallery.shared.TvInfo
import com.kiroland.gallery.shared.TvSettings
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UnauthorizedException : IOException("A TV nem ismeri ezt a telefont – párosítsd újra")

/** Blocking HTTP client for the TV's server; call from a background thread. */
class TvClient(private val host: String, private val port: Int, private val token: String? = null) {

    fun pair(pin: String, deviceName: String): PairResponse {
        val body = Protocol.json.encodeToString(PairRequest.serializer(), PairRequest(pin, deviceName))
        return Protocol.json.decodeFromString(PairResponse.serializer(), request("POST", "/pair", json = body))
    }

    fun info(timeoutMs: Int = 4000): TvInfo =
        Protocol.json.decodeFromString(TvInfo.serializer(), request("GET", "/info", timeoutMs = timeoutMs))

    fun list(): List<String> =
        Protocol.json.decodeFromString(PhotoList.serializer(), request("GET", "/photos")).ids

    fun upload(meta: PhotoMeta, image: File) {
        val header = Base64.encodeToString(
            Protocol.json.encodeToString(PhotoMeta.serializer(), meta).toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP,
        )
        request("PUT", "/photos/${meta.id}", file = image, headers = mapOf(Protocol.HEADER_META to header), timeoutMs = 60_000)
    }

    fun updateMeta(meta: PhotoMeta) {
        request("PUT", "/photos/${meta.id}/meta", json = Protocol.json.encodeToString(PhotoMeta.serializer(), meta))
    }

    fun getSettings(): TvSettings =
        Protocol.json.decodeFromString(TvSettings.serializer(), request("GET", "/settings", timeoutMs = 5000))

    fun putSettings(settings: TvSettings) {
        request("PUT", "/settings", json = Protocol.json.encodeToString(TvSettings.serializer(), settings), timeoutMs = 5000)
    }

    fun delete(id: String) {
        request("DELETE", "/photos/$id")
    }

    private fun request(
        method: String,
        path: String,
        json: String? = null,
        file: File? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 15_000,
    ): String {
        val conn = URL("http", host, port, path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = minOf(timeoutMs, 5000)
            conn.readTimeout = timeoutMs
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            when {
                json != null -> {
                    val bytes = json.toByteArray()
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setFixedLengthStreamingMode(bytes.size)
                    conn.outputStream.use { it.write(bytes) }
                }
                file != null -> {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "image/jpeg")
                    conn.setFixedLengthStreamingMode(file.length())
                    conn.outputStream.use { out -> file.inputStream().use { it.copyTo(out, 64 * 1024) } }
                }
            }
            val code = conn.responseCode
            if (code == 401) throw UnauthorizedException()
            if (code !in 200..299) {
                val msg = conn.errorStream?.use { it.readBytes().decodeToString() }
                throw IOException(msg?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            return if (code == 204) "" else conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }
}
