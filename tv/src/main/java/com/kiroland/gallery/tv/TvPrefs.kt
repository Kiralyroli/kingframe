package com.kiroland.gallery.tv

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.kiroland.gallery.shared.TvSettings
import com.kiroland.gallery.shared.TvOptions
import com.kiroland.gallery.shared.WeatherPlace
import java.security.SecureRandom

enum class PhotoOrder { RANDOM, CHRONOLOGICAL }

enum class TransitionStyle(val label: String) {
    CROSSFADE("Áttűnés"),
    SLIDE("Csúsztatás"),
    ZOOM("Nagyítás"),
    DIP_TO_BLACK("Feketén át"),
    MIXED("Vegyes"),
}

enum class TransitionSpeed(val label: String, val millis: Int) {
    FAST("Gyors", 800),
    NORMAL("Normál", 1500),
    SLOW("Lassú", 2600),
}

enum class MotionStyle(val label: String) {
    /** Slow zoom plus a gentle drift (Ken Burns). */
    KEN_BURNS("Ken Burns"),
    /** Barely noticeable zoom only. */
    SUBTLE("Finom"),
    /** Photos stand still (panoramas still pan, they would not fit otherwise). */
    NONE("Nincs"),
}

enum class NightMode(val label: String) {
    OFF("Ki"),
    /** Black screen during the night hours. */
    DARK("Sötét képernyő"),
    /** Only a dim clock during the night hours. */
    CLOCK("Csak óra"),
}

/** TV-side settings plus pairing state (PIN shown on screen, tokens of paired phones). */
class TvPrefs(private val context: Context) {
    private val sp = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private var failedAttempts = 0

    private val _pin = MutableStateFlow(newPin())
    /** Short-lived pairing code; changes after every successful or 5 failed pairings. */
    val pin: StateFlow<String> = _pin

    private val _intervalSec = MutableStateFlow(sp.getInt("interval", 15))
    val intervalSec: StateFlow<Int> = _intervalSec

    private val _order = MutableStateFlow(enumPref("order", PhotoOrder.RANDOM))
    val order: StateFlow<PhotoOrder> = _order

    private val _showCamera = MutableStateFlow(sp.getBoolean("showCamera", false))
    val showCamera: StateFlow<Boolean> = _showCamera

    private val _transition = MutableStateFlow(enumPref("transition", TransitionStyle.CROSSFADE))
    val transition: StateFlow<TransitionStyle> = _transition

    private val _transitionSpeed = MutableStateFlow(enumPref("transitionSpeed", TransitionSpeed.NORMAL))
    val transitionSpeed: StateFlow<TransitionSpeed> = _transitionSpeed

    private val _motion = MutableStateFlow(enumPref("motion", MotionStyle.KEN_BURNS))
    val motion: StateFlow<MotionStyle> = _motion

    fun setTransition(v: TransitionStyle) { sp.edit().putString("transition", v.name).apply(); _transition.value = v }
    fun setTransitionSpeed(v: TransitionSpeed) { sp.edit().putString("transitionSpeed", v.name).apply(); _transitionSpeed.value = v }
    fun setMotion(v: MotionStyle) { sp.edit().putString("motion", v.name).apply(); _motion.value = v }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        sp.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private val _showWeather = MutableStateFlow(sp.getBoolean("showWeather", true))
    val showWeather: StateFlow<Boolean> = _showWeather

    fun setShowWeather(show: Boolean) {
        sp.edit().putBoolean("showWeather", show).apply(); _showWeather.value = show
    }

    private val _weatherPlace = MutableStateFlow(
        sp.getString("weatherPlace", null)?.let { name ->
            WeatherPlace(
                name,
                Double.fromBits(sp.getLong("weatherLat", 0)),
                Double.fromBits(sp.getLong("weatherLon", 0)),
            )
        }
    )
    /** Where the weather is for; the TV has no GPS, so the user picks a town once. */
    val weatherPlace: StateFlow<WeatherPlace?> = _weatherPlace

    fun setWeatherPlace(place: WeatherPlace?) {
        sp.edit().apply {
            if (place == null) remove("weatherPlace").remove("weatherLat").remove("weatherLon")
            else putString("weatherPlace", place.name)
                .putLong("weatherLat", place.latitude.toRawBits())
                .putLong("weatherLon", place.longitude.toRawBits())
        }.apply()
        _weatherPlace.value = place?.copy(detail = null)
    }

    private val _showClock = MutableStateFlow(sp.getBoolean("showClock", true))
    val showClock: StateFlow<Boolean> = _showClock

    fun setShowClock(show: Boolean) {
        sp.edit().putBoolean("showClock", show).apply(); _showClock.value = show
    }

    private val _onThisDay = MutableStateFlow(sp.getBoolean("onThisDay", true))
    /** Photos taken on today's date in earlier years come up more often. */
    val onThisDay: StateFlow<Boolean> = _onThisDay

    fun setOnThisDay(on: Boolean) {
        sp.edit().putBoolean("onThisDay", on).apply(); _onThisDay.value = on
    }

    private val _nightMode = MutableStateFlow(enumPref("nightMode", NightMode.OFF))
    val nightMode: StateFlow<NightMode> = _nightMode
    private val _nightStart = MutableStateFlow(sp.getInt("nightStart", 23))
    val nightStart: StateFlow<Int> = _nightStart
    private val _nightEnd = MutableStateFlow(sp.getInt("nightEnd", 7))
    val nightEnd: StateFlow<Int> = _nightEnd

    fun setNightMode(v: NightMode) { sp.edit().putString("nightMode", v.name).apply(); _nightMode.value = v }
    fun setNightStart(hour: Int) { val h = Math.floorMod(hour, 24); sp.edit().putInt("nightStart", h).apply(); _nightStart.value = h }
    fun setNightEnd(hour: Int) { val h = Math.floorMod(hour, 24); sp.edit().putInt("nightEnd", h).apply(); _nightEnd.value = h }

    /** True between the night start and end hours (the range may wrap past midnight). */
    fun isNight(hour: Int): Boolean {
        if (_nightMode.value == NightMode.OFF) return false
        val start = _nightStart.value
        val end = _nightEnd.value
        return when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }

    /** Everything the phone's "TV beállításai" screen shows. */
    fun snapshot() = TvSettings(
        intervalSec = _intervalSec.value,
        order = _order.value.name,
        transition = _transition.value.name,
        transitionSpeed = _transitionSpeed.value.name,
        motion = _motion.value.name,
        onThisDay = _onThisDay.value,
        showClock = _showClock.value,
        showCamera = _showCamera.value,
        showWeather = _showWeather.value,
        weatherPlace = _weatherPlace.value,
        nightMode = _nightMode.value.name,
        nightStartHour = _nightStart.value,
        nightEndHour = _nightEnd.value,
    )

    /** Applies settings sent by the phone; unknown values are ignored. */
    fun apply(s: TvSettings) {
        if (s.intervalSec in TvOptions.intervals) setInterval(s.intervalSec)
        enumOf<PhotoOrder>(s.order)?.let(::setOrder)
        enumOf<TransitionStyle>(s.transition)?.let(::setTransition)
        enumOf<TransitionSpeed>(s.transitionSpeed)?.let(::setTransitionSpeed)
        enumOf<MotionStyle>(s.motion)?.let(::setMotion)
        setOnThisDay(s.onThisDay)
        setShowClock(s.showClock)
        setShowCamera(s.showCamera)
        setShowWeather(s.showWeather)
        s.weatherPlace?.copy(detail = null).let { if (it != _weatherPlace.value) setWeatherPlace(it) }
        enumOf<NightMode>(s.nightMode)?.let(::setNightMode)
        setNightStart(s.nightStartHour)
        setNightEnd(s.nightEndHour)
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String): T? = enumValues<T>().firstOrNull { it.name == name }

    private val _pairedCount = MutableStateFlow(tokens().size)
    val pairedCount: StateFlow<Int> = _pairedCount

    val tvName: String
        get() = Settings.Global.getString(context.contentResolver, "device_name")
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    fun setInterval(sec: Int) {
        sp.edit().putInt("interval", sec).apply(); _intervalSec.value = sec
    }

    fun setOrder(order: PhotoOrder) {
        sp.edit().putString("order", order.name).apply(); _order.value = order
    }

    fun setShowCamera(show: Boolean) {
        sp.edit().putBoolean("showCamera", show).apply(); _showCamera.value = show
    }

    private fun tokens(): Set<String> = sp.getStringSet("tokens", emptySet())!!

    fun isAuthorized(token: String?): Boolean = token != null && token in tokens()

    /** Returns a new token when [pin] matches, null otherwise. */
    @Synchronized
    fun pair(pin: String): String? {
        if (pin != _pin.value) {
            if (++failedAttempts >= 5) {
                failedAttempts = 0
                _pin.value = newPin()
            }
            return null
        }
        failedAttempts = 0
        _pin.value = newPin()
        val token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val all = tokens() + token
        sp.edit().putStringSet("tokens", all).apply()
        _pairedCount.value = all.size
        return token
    }

    fun forgetPhones() {
        sp.edit().remove("tokens").apply()
        _pairedCount.value = 0
    }

    private fun newPin() = "%06d".format(random.nextInt(1_000_000))
}
