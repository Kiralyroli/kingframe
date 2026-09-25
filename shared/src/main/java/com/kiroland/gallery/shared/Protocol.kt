package com.kiroland.gallery.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phone → TV protocol over the local network. The TV runs a small HTTP server,
 * announces itself via NSD (mDNS) and the phone pushes resized photos plus metadata.
 *
 *   POST   /pair               PairRequest            → PairResponse      (no auth)
 *   GET    /info                                      → TvInfo
 *   GET    /photos                                    → PhotoList
 *   PUT    /photos/{id}        JPEG body, X-Meta hdr  → 204
 *   PUT    /photos/{id}/meta   PhotoMeta JSON         → 204
 *   DELETE /photos/{id}                               → 204
 *   GET    /settings                                  → TvSettings
 *   PUT    /settings           TvSettings JSON        → 204
 *
 * Every call except /pair needs "Authorization: Bearer <token>".
 */
object Protocol {
    const val SERVICE_TYPE = "_tvgallery._tcp."
    const val DEFAULT_PORT = 8765
    const val HEADER_META = "X-Meta"
    /** 2: favourite/hidden flags and /settings. */
    const val VERSION = 2

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

@Serializable
data class PhotoMeta(
    val id: String,
    /** Capture time as written by the camera, local wall-clock time: "2025-07-10T16:21:45". */
    val takenLocal: String? = null,
    /** UTC offset of the capture time if the camera recorded it: "+02:00". */
    val takenOffset: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Human readable place from reverse geocoding, e.g. "Siófok, Magyarország". */
    val place: String? = null,
    /** Place typed in by the user; wins over [place] when set. */
    val manualPlace: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val fileName: String? = null,
    val originalWidth: Int? = null,
    val originalHeight: Int? = null,
    val width: Int = 0,
    val height: Int = 0,
    val addedAt: Long = 0,
    /** Marked on the phone: shown more often on the TV. */
    val favorite: Boolean = false,
    /** Marked on the phone: kept, but not shown on the TV. */
    val hidden: Boolean = false,
) {
    val displayPlace: String? get() = manualPlace?.takeIf { it.isNotBlank() } ?: place
}

@Serializable
data class PairRequest(val pin: String, val deviceName: String)

@Serializable
data class PairResponse(val token: String, val tvName: String)

@Serializable
data class TvInfo(
    val name: String,
    val protocol: Int = Protocol.VERSION,
    /** Size the TV actually renders the slideshow at (UI resolution, landscape). */
    val screenWidth: Int,
    val screenHeight: Int,
    val photoCount: Int,
    val freeBytes: Long,
)

@Serializable
data class PhotoList(val ids: List<String>)

/** A town the TV shows the weather for (the TV has no GPS). */
@Serializable
data class WeatherPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** "Csongrád megye, Magyarország" – only used in search results. */
    val detail: String? = null,
) {
    val key: String get() = "$latitude,$longitude"
}

/**
 * The TV's slideshow settings, so the phone can show and change them. Enum-like values
 * travel as their names (see [TvOptions]) so either side can add values later.
 */
@Serializable
data class TvSettings(
    val intervalSec: Int = 15,
    val order: String = "RANDOM",
    val transition: String = "CROSSFADE",
    val transitionSpeed: String = "NORMAL",
    val motion: String = "KEN_BURNS",
    val onThisDay: Boolean = true,
    val showClock: Boolean = true,
    val showCamera: Boolean = false,
    val showWeather: Boolean = true,
    val weatherPlace: WeatherPlace? = null,
    val nightMode: String = "OFF",
    val nightStartHour: Int = 23,
    val nightEndHour: Int = 7,
)

data class TvOption(val value: String, val label: String)

/** Values and Hungarian labels for [TvSettings]; the TV's enums use the same names. */
object TvOptions {
    val intervals = listOf(10, 15, 30, 60, 120)
    val orders = listOf(TvOption("RANDOM", "Véletlen"), TvOption("CHRONOLOGICAL", "Időrendi"))
    val transitions = listOf(
        TvOption("CROSSFADE", "Áttűnés"), TvOption("SLIDE", "Csúsztatás"), TvOption("ZOOM", "Nagyítás"),
        TvOption("DIP_TO_BLACK", "Feketén át"), TvOption("MIXED", "Vegyes"),
    )
    val speeds = listOf(TvOption("FAST", "Gyors"), TvOption("NORMAL", "Normál"), TvOption("SLOW", "Lassú"))
    val motions = listOf(TvOption("KEN_BURNS", "Ken Burns"), TvOption("SUBTLE", "Finom"), TvOption("NONE", "Nincs"))
    val nightModes = listOf(TvOption("OFF", "Ki"), TvOption("DARK", "Sötét képernyő"), TvOption("CLOCK", "Csak óra"))

    fun intervalLabel(sec: Int) = if (sec < 60) "$sec mp" else "${sec / 60} perc"
    fun hourLabel(hour: Int) = "$hour:00"
    fun label(options: List<TvOption>, value: String) = options.firstOrNull { it.value == value }?.label ?: value
}