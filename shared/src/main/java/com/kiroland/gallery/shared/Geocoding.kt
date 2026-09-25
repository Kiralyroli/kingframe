package com.kiroland.gallery.shared

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Town search in Hungarian via Open-Meteo's free geocoding API (data CC BY 4.0). Blocking. */
object Geocoding {
    fun search(query: String): List<WeatherPlace> {
        if (query.isBlank()) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=hu&format=json&name=" +
            URLEncoder.encode(query.trim(), "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        val body = try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
        val results = Protocol.json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        return results.map { it.jsonObject }.map { r ->
            WeatherPlace(
                name = r["name"]!!.jsonPrimitive.content,
                latitude = r["latitude"]!!.jsonPrimitive.double,
                longitude = r["longitude"]!!.jsonPrimitive.double,
                detail = listOfNotNull(r["admin1"]?.jsonPrimitive?.content, r["country"]?.jsonPrimitive?.content)
                    .distinct().joinToString(", ").ifEmpty { null },
            )
        }.distinctBy { it.name to it.detail } // a town and its district share a name
    }
}
