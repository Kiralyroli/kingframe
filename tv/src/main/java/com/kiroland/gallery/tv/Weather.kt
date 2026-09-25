package com.kiroland.gallery.tv

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.kiroland.gallery.shared.Geocoding
import com.kiroland.gallery.shared.Protocol
import com.kiroland.gallery.shared.WeatherPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL


@Serializable
data class Weather(
    val temperature: Double,
    val code: Int,
    val isDay: Boolean,
    val max: Double? = null,
    val min: Double? = null,
    val fetchedAt: Long,
    val placeKey: String,
)

/**
 * Current weather from Open-Meteo (free, no API key; data CC BY 4.0). Refreshed at most
 * every 30 minutes while the slideshow runs; the last result is kept across restarts.
 */
class WeatherService(context: Context, private val prefs: TvPrefs) {
    private val sp = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private val _weather = MutableStateFlow(
        sp.getString("last", null)?.let { runCatching { Protocol.json.decodeFromString<Weather>(it) }.getOrNull() }
    )
    val weather: StateFlow<Weather?> = _weather

    suspend fun refreshIfStale() = withContext(Dispatchers.IO) {
        val place = prefs.weatherPlace.value ?: return@withContext
        val last = _weather.value
        if (last != null && last.placeKey == place.key && System.currentTimeMillis() - last.fetchedAt < REFRESH_MS) return@withContext
        runCatching { fetch(place) }
            .onSuccess {
                _weather.value = it
                sp.edit().putString("last", Protocol.json.encodeToString(Weather.serializer(), it)).apply()
            }
    }

    /** The weather to show for [place], or null when there is none fresh enough. */
    fun usable(weather: Weather?, place: WeatherPlace?): Weather? =
        weather?.takeIf { place != null && it.placeKey == place.key && System.currentTimeMillis() - it.fetchedAt < MAX_AGE_MS }

    private fun fetch(place: WeatherPlace): Weather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val root = getJson(url)
        val current = root["current"]!!.jsonObject
        val daily = root["daily"]?.jsonObject
        return Weather(
            temperature = current["temperature_2m"]!!.jsonPrimitive.double,
            code = current["weather_code"]!!.jsonPrimitive.int,
            isDay = current["is_day"]?.jsonPrimitive?.int != 0,
            max = daily?.get("temperature_2m_max")?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull,
            min = daily?.get("temperature_2m_min")?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull,
            fetchedAt = System.currentTimeMillis(),
            placeKey = place.key,
        )
    }

    companion object {
        private const val REFRESH_MS = 30 * 60_000L
        private const val MAX_AGE_MS = 3 * 60 * 60_000L

        /** Town search, off the main thread. */
        suspend fun search(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) { Geocoding.search(query) }

        private fun getJson(url: String): JsonObject {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                return Protocol.json.parseToJsonElement(conn.inputStream.use { it.readBytes().decodeToString() }).jsonObject
            } finally {
                conn.disconnect()
            }
        }
    }
}

/** WMO weather code → Hungarian label and icon. */
fun describeWeather(code: Int, isDay: Boolean): Pair<String, ImageVector> = when (code) {
    0 -> (if (isDay) "Napos" else "Tiszta ég") to (if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay)
    1 -> "Többnyire derült" to (if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay)
    2 -> "Részben felhős" to Icons.Outlined.WbCloudy
    3 -> "Borult" to Icons.Outlined.Cloud
    45, 48 -> "Köd" to Icons.Outlined.Dehaze
    in 51..57 -> "Szitálás" to Icons.Outlined.Grain
    66, 67 -> "Ónos eső" to Icons.Outlined.AcUnit
    in 61..65 -> "Eső" to Icons.Outlined.Umbrella
    in 80..82 -> "Zápor" to Icons.Outlined.Umbrella
    in 71..77, 85, 86 -> "Havazás" to Icons.Outlined.AcUnit
    in 95..99 -> "Zivatar" to Icons.Outlined.Thunderstorm
    else -> "" to Icons.Outlined.Cloud
}
