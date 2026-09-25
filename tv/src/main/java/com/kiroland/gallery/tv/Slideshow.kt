package com.kiroland.gallery.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiroland.gallery.shared.Brand
import com.kiroland.gallery.shared.PhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

private class Slide(
    val meta: PhotoMeta,
    val image: ImageBitmap,
    /** Tiny copy of the image; stretched up it doubles as a soft blurred backdrop. */
    val backdrop: ImageBitmap,
    val framing: Framing,
    val durationMs: Long,
    val overlayOnLeft: Boolean,
    /** Concrete transition into this slide ("Mixed" is resolved when the slide is loaded). */
    val transition: TransitionStyle,
    /** Distinguishes two showings of the same photo, so even a one-photo album animates. */
    val serial: Int,
)

private enum class Framing { COVER, FIT, PAN }

/**
 * Picks what to show next.
 * - Photos that arrive while the slideshow runs jump the queue, so a photo shared from
 *   the phone shows up on the TV right away.
 * - Hidden photos are skipped; favourites come up twice as often.
 * - "On this day": photos taken on today's date in earlier years are mixed in every
 *   few slides.
 */
private class Playlist {
    private var known = emptySet<String>()
    private val fresh = ArrayDeque<String>()
    private val queue = ArrayDeque<String>()
    private var byId = emptyMap<String, PhotoMeta>()
    private var order = PhotoOrder.RANDOM
    private var onThisDay = true

    fun update(photos: List<PhotoMeta>, order: PhotoOrder, onThisDay: Boolean) {
        val shown = photos.filterNot { it.hidden }
        val ids = shown.map { it.id }.toSet()
        if (known.isNotEmpty()) (ids - known).forEach { fresh.addLast(it) }
        known = ids
        byId = shown.associateBy { it.id }
        fresh.retainAll { it in ids }
        queue.retainAll { it in ids }
        if (order != this.order || onThisDay != this.onThisDay) {
            this.order = order
            this.onThisDay = onThisDay
            queue.clear()
        }
    }

    fun next(): PhotoMeta? {
        fresh.removeFirstOrNull()?.let { id -> byId[id]?.let { return it } }
        if (queue.isEmpty()) refill()
        return queue.removeFirstOrNull()?.let { byId[it] }
    }

    fun peek(): PhotoMeta? {
        fresh.firstOrNull()?.let { return byId[it] }
        if (queue.isEmpty()) refill()
        return queue.firstOrNull()?.let { byId[it] }
    }

    private fun refill() {
        val all = byId.values.toList()
        val base = when (order) {
            PhotoOrder.RANDOM -> (all + all.filter { it.favorite }).shuffled().map { it.id }.spreadDuplicates()
            PhotoOrder.CHRONOLOGICAL -> all.sortedBy { it.takenLocal ?: "" }.map { it.id }
        }
        val memories = if (onThisDay) all.filter { yearsAgo(it) != null }.shuffled().map { it.id } else emptyList()
        if (memories.isEmpty() || memories.size == all.size) {
            queue += base
            return
        }
        // Early in each round, every third slide is one of today's memories – each memory
        // once, so a single anniversary photo doesn't come back all day.
        var m = 0
        base.forEachIndexed { i, id ->
            if (i % 3 == 2 && m < memories.size) queue += memories[m++]
            queue += id
        }
    }

    /** Avoids the same favourite twice in a row after shuffling. */
    private fun List<String>.spreadDuplicates(): List<String> {
        val list = toMutableList()
        for (i in 1 until list.size) {
            if (list[i] == list[i - 1]) {
                val j = (i + 1 until list.size).firstOrNull { list[it] != list[i] } ?: continue
                list[i] = list[j].also { list[j] = list[i] }
            }
        }
        return list
    }
}

/** How many years ago today this photo was taken, or null if it isn't an anniversary. */
fun yearsAgo(meta: PhotoMeta, today: LocalDate = LocalDate.now()): Int? {
    val taken = meta.takenLocal?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() } ?: return null
    if (taken.monthValue != today.monthValue || taken.dayOfMonth != today.dayOfMonth) return null
    return (today.year - taken.year).takeIf { it > 0 }
}

@Composable
fun Slideshow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.tvApp
    val photos by app.store.photos.collectAsState()
    val intervalSec by app.prefs.intervalSec.collectAsState()
    val order by app.prefs.order.collectAsState()
    val showCamera by app.prefs.showCamera.collectAsState()
    val showClock by app.prefs.showClock.collectAsState()
    val transition by app.prefs.transition.collectAsState()
    val speed by app.prefs.transitionSpeed.collectAsState()
    val motion by app.prefs.motion.collectAsState()

    val playlist = remember { Playlist() }
    val onThisDay by app.prefs.onThisDay.collectAsState()
    LaunchedEffect(photos, order, onThisDay) { playlist.update(photos, order, onThisDay) }
    var current by remember { mutableStateOf<Slide?>(null) }

    // Night mode: checked every half minute; while it lasts no photos are loaded at all.
    val nightMode by app.prefs.nightMode.collectAsState()
    val nightStart by app.prefs.nightStart.collectAsState()
    val nightEnd by app.prefs.nightEnd.collectAsState()
    var night by remember { mutableStateOf(false) }
    LaunchedEffect(nightMode, nightStart, nightEnd) {
        while (true) {
            night = app.prefs.isNight(LocalTime.now().hour)
            delay(30_000)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight

        LaunchedEffect(screenW, screenH) {
            var preloaded: Slide? = null
            var serial = 0
            while (true) {
                if (app.prefs.isNight(LocalTime.now().hour)) {
                    current = null
                    preloaded = null
                    delay(30_000)
                    continue
                }
                val meta = playlist.next()
                if (meta == null) {
                    current = null
                    delay(1000)
                    continue
                }
                serial++
                val left = serial % 2 == 0
                val slide = preloaded?.takeIf { it.meta == meta }
                    ?: loadSlide(app.store, meta, screenW, screenH, intervalSec, left, transition, serial)
                preloaded = null
                if (slide == null) {
                    delay(200)
                    continue
                }
                current = slide
                coroutineScope {
                    val next = playlist.peek()
                    val job = async {
                        next?.let { loadSlide(app.store, it, screenW, screenH, intervalSec, !left, transition, serial + 1) }
                    }
                    delay(slide.durationMs)
                    preloaded = job.await()
                }
            }
        }

        if (night) {
            NightScreen(nightMode)
        } else if (photos.isEmpty()) {
            EmptyState()
        } else {
            AnimatedContent(
                targetState = current,
                contentKey = { it?.serial ?: -1 },
                transitionSpec = { transitionFor(targetState?.transition ?: TransitionStyle.CROSSFADE, speed.millis) },
                label = "slide",
            ) { slide ->
                if (slide != null) SlideView(slide, showCamera, onThisDay, motion, speed.millis)
            }
            TopCorner(showClock)
        }
    }
}

/** Night hours: black, or only a dim clock that wanders a little against burn-in. */
@Composable
private fun NightScreen(mode: NightMode) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (mode != NightMode.CLOCK) return@Box
        var now by remember { mutableStateOf(LocalDateTime.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = LocalDateTime.now()
                delay(1000L * (60 - now.second).coerceAtLeast(1))
            }
        }
        val dx = ((now.minute * 37) % 200 - 100).dp
        val dy = ((now.minute * 53) % 120 - 60).dp
        Text(
            now.format(timeFormat),
            style = tvText(72.sp, FontWeight.Light, Brand.TextHigh.copy(alpha = 0.28f)),
            modifier = Modifier.offset(x = dx, y = dy),
        )
    }
}
private fun transitionFor(style: TransitionStyle, ms: Int): ContentTransform {
    val ease = tween<Float>(ms, easing = FastOutSlowInEasing)
    val (enter, exit) = when (style) {
        TransitionStyle.CROSSFADE, TransitionStyle.MIXED ->
            fadeIn(tween(ms)) to fadeOut(tween(ms))
        TransitionStyle.SLIDE ->
            slideInHorizontally(tween(ms, easing = FastOutSlowInEasing)) { it } to
                slideOutHorizontally(tween(ms, easing = FastOutSlowInEasing)) { -it }
        TransitionStyle.ZOOM ->
            (scaleIn(ease, initialScale = 1.15f) + fadeIn(tween(ms))) to
                (scaleOut(ease, targetScale = 0.94f) + fadeOut(tween(ms)))
        TransitionStyle.DIP_TO_BLACK ->
            fadeIn(tween(ms / 2, delayMillis = ms / 2)) to fadeOut(tween(ms / 2))
    }
    // Every slide is full screen, so no size animation.
    return ContentTransform(enter, exit, sizeTransform = null)
}

/**
 * Weather (if set up) and time in the top corner: "☀ 21°  Napos · 24° / 12°  |  18:33".
 * Drifts a few pixels every minute against burn-in.
 */
@Composable
private fun TopCorner(showClock: Boolean) {
    val app = LocalContext.current.tvApp
    val showWeather by app.prefs.showWeather.collectAsState()
    val place by app.prefs.weatherPlace.collectAsState()
    val latest by app.weather.weather.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L * (60 - now.second).coerceAtLeast(1))
        }
    }
    LaunchedEffect(showWeather, place) {
        if (!showWeather || place == null) return@LaunchedEffect
        while (true) {
            app.weather.refreshIfStale()
            delay(5 * 60_000L)
        }
    }
    // Recomposed every minute with the clock, so stale data disappears on its own.
    val weather = if (showWeather) app.weather.usable(latest, place) else null
    if (!showClock && weather == null) return

    val drift = (now.minute % 6) * 3
    Box(
        Modifier.fillMaxSize().graphicsLayer().padding(top = (36 + drift).dp, end = (52 + drift).dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            weather?.let { w ->
                val (label, icon) = describeWeather(w.code, w.isDay)
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = Brand.TextHigh, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${w.temperature.roundToInt()}°", style = overlayText(30.sp, FontWeight.Light))
                    }
                    val range = if (w.max != null && w.min != null) "${w.max.roundToInt()}° / ${w.min.roundToInt()}°" else null
                    Text(
                        listOfNotNull(label.ifEmpty { null }, range).joinToString("  ·  "),
                        style = overlayText(13.sp, FontWeight.Normal, Brand.TextHigh.copy(alpha = 0.75f)),
                    )
                }
                if (showClock) {
                    Spacer(Modifier.width(20.dp))
                    Box(Modifier.width(1.dp).height(44.dp).background(Brand.TextHigh.copy(alpha = 0.35f)))
                    Spacer(Modifier.width(20.dp))
                }
            }
            if (showClock) Text(now.format(timeFormat), style = overlayText(40.sp, FontWeight.Light))
        }
    }
}

/**
 * One slide. The photo is drawn once into its own layer; the slow zoom/pan only changes
 * that layer's transform, which the GPU applies with sub-pixel precision. Nothing is
 * redrawn per frame, which keeps even slow TV chips smooth.
 */
@Composable
private fun SlideView(slide: Slide, showCamera: Boolean, onThisDay: Boolean, motion: MotionStyle, fadeMs: Int) {
    val progress = remember(slide) { Animatable(0f) }
    LaunchedEffect(slide) {
        progress.animateTo(1f, tween((slide.durationMs + fadeMs).toInt(), easing = LinearEasing))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val img = slide.image
        val direction = if (slide.overlayOnLeft) 1f else -1f

        when (slide.framing) {
            Framing.COVER -> {
                val s = maxOf(w / img.width, h / img.height)
                val dw = img.width * s
                val dh = img.height * s
                val (zoom, drift) = when (motion) {
                    MotionStyle.KEN_BURNS -> 0.08f to 0.8f
                    MotionStyle.SUBTLE -> 0.03f to 0f
                    MotionStyle.NONE -> 0f to 0f
                }
                PhotoLayer(img, (w - dw) / 2, (h - dh) / 2, dw, dh) {
                    val t = progress.value
                    val scale = 1f + zoom * t
                    scaleX = scale
                    scaleY = scale
                    // Drift sideways, but never so far that an edge shows.
                    val room = (dw * scale - w) / 2
                    translationX = direction * (t - 0.5f) * 2f * room * drift
                }
            }
            Framing.FIT -> {
                Backdrop(slide.backdrop)
                val s = minOf(w / img.width, h / img.height)
                val dw = img.width * s
                val dh = img.height * s
                val zoom = when (motion) {
                    MotionStyle.KEN_BURNS -> 0.04f
                    MotionStyle.SUBTLE -> 0.02f
                    MotionStyle.NONE -> 0f
                }
                PhotoLayer(img, (w - dw) / 2, (h - dh) / 2, dw, dh) {
                    val scale = 1f + zoom * progress.value
                    scaleX = scale
                    scaleY = scale
                }
            }
            Framing.PAN -> {
                val dw = img.width * (h / img.height)
                PhotoLayer(img, 0f, 0f, dw, h) {
                    // Panoramas always glide end to end; they would not fit otherwise.
                    val eased = (1 - cos(Math.PI * progress.value.coerceIn(0f, 1f))).toFloat() / 2
                    translationX = -(dw - w) * eased
                }
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer()) { MetaOverlay(slide.meta, showCamera, onThisDay, slide.overlayOnLeft) }
    }
}

/** Draws [image] once at the given rect; [transform] animates the layer, not the pixels. */
@Composable
private fun PhotoLayer(
    image: ImageBitmap,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    transform: GraphicsLayerScope.() -> Unit,
) {
    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            transform()
        },
    ) {
        drawImage(
            image,
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

@Composable
private fun Backdrop(backdrop: ImageBitmap) {
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val aspect = size.width / size.height
        val (bw, bh) = if (backdrop.width.toFloat() / backdrop.height > aspect)
            (backdrop.height * aspect).toInt() to backdrop.height else backdrop.width to (backdrop.width / aspect).toInt()
        drawImage(
            backdrop,
            srcOffset = IntOffset((backdrop.width - bw) / 2, (backdrop.height - bh) / 2),
            srcSize = IntSize(bw.coerceAtLeast(1), bh.coerceAtLeast(1)),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            alpha = 0.45f,
            filterQuality = FilterQuality.Low,
        )
    }
}

private fun framingOf(width: Int, height: Int, screenW: Int, screenH: Int): Framing {
    val imageAspect = width.toFloat() / height
    val screenAspect = screenW.toFloat() / screenH
    // Landscape photos (incl. 4:3 phone shots) fill the screen; portrait/square ones are
    // shown whole. Keep in sync with PhotoProcessor.targetScale on the phone.
    return when {
        imageAspect > screenAspect * 1.5f -> Framing.PAN
        imageAspect < 1.2f -> Framing.FIT
        else -> Framing.COVER
    }
}

private val hungarian = Locale.forLanguageTag("hu-HU")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", hungarian)

private val captionDateFormat = DateTimeFormatter.ofPattern("yyyy. MMMM d.", hungarian)

/** "2025. augusztus 31." – the caption keeps only the day; the time adds little on a TV. */
fun formatTakenDate(meta: PhotoMeta): String? = meta.takenLocal?.let {
    runCatching { LocalDateTime.parse(it).format(captionDateFormat) }.getOrNull()
}

private val overlayShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 10f)

private fun overlayText(size: TextUnit, weight: FontWeight, color: Color = Brand.TextHigh) =
    TextStyle(fontFamily = Brand.Font, fontSize = size, fontWeight = weight, color = color, shadow = overlayShadow)

@Composable
private fun MetaOverlay(meta: PhotoMeta, showCamera: Boolean, onThisDay: Boolean, left: Boolean) {
    val place = meta.displayPlace
    val taken = formatTakenDate(meta)
    val camera = listOfNotNull(meta.cameraMake, meta.cameraModel)
        .let { parts -> if (parts.size == 2 && parts[1].startsWith(parts[0], ignoreCase = true)) listOf(parts[1]) else parts }
        .joinToString(" ").takeIf { showCamera && it.isNotBlank() }
    val memory = if (onThisDay) yearsAgo(meta)?.let { if (it == 1) "Tavaly ezen a napon" else "$it éve ezen a napon" } else null
    if (place == null && taken == null && camera == null && memory == null) return

    val align = if (left) Alignment.Start else Alignment.End
    val textAlign = if (left) TextAlign.Start else TextAlign.End
    // Quiet caption: place, then date underneath; a soft scrim only where the text sits.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f)))
                .padding(start = 56.dp, end = 56.dp, top = 72.dp, bottom = 40.dp),
            contentAlignment = if (left) Alignment.BottomStart else Alignment.BottomEnd,
        ) {
            Column(horizontalAlignment = align) {
                memory?.let {
                    Text(it, style = overlayText(14.sp, FontWeight.Medium, Brand.Amber), textAlign = textAlign, modifier = Modifier.padding(bottom = 4.dp))
                }
                place?.let { Text(it, style = overlayText(26.sp, FontWeight.Medium), textAlign = textAlign) }
                taken?.let {
                    Text(
                        it,
                        style = overlayText(16.sp, FontWeight.Normal, Brand.TextHigh.copy(alpha = 0.8f)),
                        textAlign = textAlign,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                camera?.let {
                    Text(
                        it,
                        style = overlayText(13.sp, FontWeight.Normal, Brand.TextHigh.copy(alpha = 0.55f)),
                        textAlign = textAlign,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().background(Brand.Night0),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Még nincs kép", style = tvText(28.sp, FontWeight.Medium))
        Spacer(Modifier.height(8.dp))
        Text(
            "Telefonon: Google Fotók  →  képek kijelölése  →  Megosztás  →  KingFrame",
            style = tvText(15.sp, color = Brand.TextMid),
        )
    }
}

private val concreteTransitions = TransitionStyle.entries.filter { it != TransitionStyle.MIXED }

private suspend fun loadSlide(
    store: PhotoStore,
    meta: PhotoMeta,
    screenW: Int,
    screenH: Int,
    intervalSec: Int,
    overlayOnLeft: Boolean,
    transition: TransitionStyle,
    serial: Int,
): Slide? = withContext(Dispatchers.IO) {
    runCatching {
        val file = store.imageFile(meta.id)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return@runCatching null
        // Never decode more than ~1.5x the screen height; panoramas stay wide.
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= screenH * 1.2 && bounds.outWidth / (sample * 2) >= screenW * 1.2) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        // Upload the texture ahead of time, so the first frame of the transition doesn't stall.
        bitmap.prepareToDraw()
        val backdrop = Bitmap.createScaledBitmap(bitmap, 48, (48f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1), true)
        val framing = framingOf(bitmap.width, bitmap.height, screenW, screenH)
        Slide(
            meta = meta,
            image = bitmap.asImageBitmap(),
            backdrop = backdrop.asImageBitmap(),
            framing = framing,
            durationMs = intervalSec * 1000L * (if (framing == Framing.PAN) 2 else 1),
            overlayOnLeft = overlayOnLeft,
            transition = if (transition == TransitionStyle.MIXED) concreteTransitions.random() else transition,
            serial = serial,
        )
    }.getOrNull()
}
