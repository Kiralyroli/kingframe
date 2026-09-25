package com.kiroland.gallery.phone

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

data class PlaceSuggestion(
    val photoId: String,
    val place: String,
    /** Positive: the photo with the known place was taken later. */
    val minutesApart: Long,
)

/**
 * Suggests a place for photos without one, from photos with a known place taken within
 * [maxHours] of them. Works entirely on the phone from the capture times.
 */
fun suggestPlaces(photos: List<LocalPhoto>, maxHours: Long = 6): Map<String, PlaceSuggestion> {
    fun time(p: LocalPhoto) = p.meta.takenLocal?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    val known = photos.filter { it.state != SyncState.PENDING_DELETE && it.meta.displayPlace != null }
        .mapNotNull { p -> time(p)?.let { it to p.meta.displayPlace!! } }
    if (known.isEmpty()) return emptyMap()

    return photos.asSequence()
        .filter { it.state != SyncState.PENDING_DELETE && it.meta.displayPlace == null }
        .mapNotNull { p ->
            val t = time(p) ?: return@mapNotNull null
            val (when_, place) = known.minByOrNull { abs(Duration.between(t, it.first).toMinutes()) } ?: return@mapNotNull null
            val minutes = Duration.between(t, when_).toMinutes()
            if (abs(minutes) > maxHours * 60) null else PlaceSuggestion(p.meta.id, place, minutes)
        }
        .associateBy { it.photoId }
}

/** "ugyanazon a napon, 2 órával később" */
fun PlaceSuggestion.reason(): String {
    val m = abs(minutesApart)
    val span = when {
        m < 60 -> "$m perccel"
        m < 120 -> "1 órával"
        else -> "${m / 60} órával"
    }
    return if (minutesApart >= 0) "Egy $span később készült kép alapján" else "Egy $span korábban készült kép alapján"
}
