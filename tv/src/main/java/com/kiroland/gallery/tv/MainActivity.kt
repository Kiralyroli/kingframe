package com.kiroland.gallery.tv

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kiroland.gallery.shared.Brand
import com.kiroland.gallery.shared.KingFrameWordmark
import com.kiroland.gallery.shared.TvOptions
import kotlinx.coroutines.delay

private val intervalSteps = TvOptions.intervals

/** Setup screen: pairing code, status and slideshow settings, driven by the remote's D-pad. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServerService.start(this)
        setContent { SetupScreen() }
    }

    @Composable
    private fun SetupScreen() {
        val app = tvApp
        val prefs = app.prefs
        val pin by prefs.pin.collectAsState()
        val server by ServerService.state.collectAsState()
        val photos by app.store.photos.collectAsState()
        val interval by prefs.intervalSec.collectAsState()
        val order by prefs.order.collectAsState()
        val showCamera by prefs.showCamera.collectAsState()
        val showClock by prefs.showClock.collectAsState()
        val paired by prefs.pairedCount.collectAsState()
        val transition by prefs.transition.collectAsState()
        val speed by prefs.transitionSpeed.collectAsState()
        val motion by prefs.motion.collectAsState()
        val showWeather by prefs.showWeather.collectAsState()
        val weatherPlace by prefs.weatherPlace.collectAsState()
        val onThisDay by prefs.onThisDay.collectAsState()
        val nightMode by prefs.nightMode.collectAsState()
        val nightStart by prefs.nightStart.collectAsState()
        val nightEnd by prefs.nightEnd.collectAsState()

        // Values the system can change behind our back; refresh while visible.
        var ip by remember { mutableStateOf<String?>(null) }
        var isScreensaver by remember { mutableStateOf(true) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    ip = ServerService.localIpv4(this@MainActivity)
                    isScreensaver = runCatching {
                        Settings.Secure.getString(contentResolver, "screensaver_components")
                    }.getOrNull()?.contains(packageName) == true
                    delay(3000)
                }
            }
        }

        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

        Row(
            Modifier.fillMaxSize().background(Brand.Night0).padding(horizontal = 52.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(52.dp),
        ) {
            // Left: identity, pairing, status
            Column(Modifier.weight(0.4f).fillMaxHeight()) {
                Text(KingFrameWordmark, style = tvText(26.sp))
                Text("Google Fotók hellyel és időponttal", style = tvText(13.sp, color = Brand.TextLow))
                Spacer(Modifier.height(44.dp))
                Text("Párosító kód", style = tvText(13.sp, color = Brand.TextLow))
                Spacer(Modifier.height(6.dp))
                PinSlots(pin)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Telefonon: KingFrame → TV hozzáadása,\nmajd válaszd ki: ${prefs.tvName}",
                    style = tvText(13.sp, color = Brand.TextMid).copy(lineHeight = 20.sp),
                )
                Spacer(Modifier.weight(1f))
                if (!isScreensaver) {
                    Text(
                        "Még nem ez a képernyővédő. Google TV-n a számítógépről:",
                        style = tvText(12.sp, color = Brand.Warning),
                    )
                    Text(
                        "adb shell settings put secure screensaver_components $packageName/.PhotoDreamService",
                        style = tvText(10.5.sp, color = Brand.TextLow).copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    )
                }
                StatusLine(
                    dot = if (server.running) Brand.Success else Brand.Danger,
                    parts = listOf(
                        if (server.running) "Fogadásra kész" else "A szerver nem fut",
                        "${photos.size} kép",
                        "$paired telefon",
                    ),
                )
                Text(
                    buildString {
                        append(if (server.running) "${ip ?: "nincs hálózat"}:${server.port}" else "—")
                        append("  ·  %.1f GB szabad".format(app.store.freeBytes / 1e9))
                    },
                    style = tvText(11.5.sp, color = Brand.TextFaint),
                    modifier = Modifier.padding(start = 15.dp, top = 2.dp),
                )
            }

            // Right: settings
            Column(Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                SettingRow(
                    Icons.Filled.PlayArrow, "Diavetítés indítása", "Előnézet a képernyővédőről",
                    Modifier.focusRequester(first), accentIcon = true,
                ) { startActivity(Intent(this@MainActivity, SlideshowActivity::class.java)) }

                SectionLabel("Diavetítés")
                Choice(Icons.Filled.Refresh, "Időköz", "Mennyi ideig látszik egy kép", formatInterval(interval),
                    { prefs.setInterval(intervalSteps.step(interval, -1)) }, { prefs.setInterval(intervalSteps.step(interval, 1)) })
                Choice(Icons.Filled.List, "Sorrend", "Véletlenszerű vagy időrendi",
                    if (order == PhotoOrder.RANDOM) "Véletlen" else "Időrendi",
                    { prefs.setOrder(order.step(-1)) }, { prefs.setOrder(order.step(1)) })
                Choice(Icons.Filled.Star, "Áttűnés", "Hogyan vált a következő képre", transition.label,
                    { prefs.setTransition(transition.step(-1)) }, { prefs.setTransition(transition.step(1)) })
                Choice(Icons.Filled.KeyboardArrowRight, "Áttűnés sebessége", "Milyen gyorsan vált", speed.label,
                    { prefs.setTransitionSpeed(speed.step(-1)) }, { prefs.setTransitionSpeed(speed.step(1)) })
                Choice(Icons.Filled.Search, "Mozgás", "Lassú nagyítás a képen", motion.label,
                    { prefs.setMotion(motion.step(-1)) }, { prefs.setMotion(motion.step(1)) })

                Choice(Icons.Outlined.Cake, "Ezen a napon", "Régi képek az évfordulójukon gyakrabban", if (onThisDay) "Be" else "Ki",
                    { prefs.setOnThisDay(!onThisDay) }, { prefs.setOnThisDay(!onThisDay) })

                SectionLabel("Megjelenítés")
                Choice(Icons.Filled.DateRange, "Óra", "Idő a jobb felső sarokban", if (showClock) "Be" else "Ki",
                    { prefs.setShowClock(!showClock) }, { prefs.setShowClock(!showClock) })
                Choice(Icons.Outlined.WbSunny, "Időjárás", "Hőmérséklet és ég az óra mellett", if (showWeather) "Be" else "Ki",
                    { prefs.setShowWeather(!showWeather) }, { prefs.setShowWeather(!showWeather) })
                SettingRow(
                    Icons.Filled.Place, "Időjárás helyszíne",
                    if (weatherPlace == null) "Állítsd be, hogy megjelenjen az időjárás" else "Módosításhoz nyomd meg",
                    value = weatherPlace?.name ?: "Nincs",
                ) { startActivity(Intent(this@MainActivity, WeatherPlaceActivity::class.java)) }
                Choice(Icons.Filled.Info, "Kamera adatai", "Fényképezőgép a felirat alatt", if (showCamera) "Be" else "Ki",
                    { prefs.setShowCamera(!showCamera) }, { prefs.setShowCamera(!showCamera) })

                SectionLabel("Éjszaka")
                Choice(Icons.Outlined.Bedtime, "Éjszakai mód", "Mi látszik éjszaka", nightMode.label,
                    { prefs.setNightMode(nightMode.step(-1)) }, { prefs.setNightMode(nightMode.step(1)) })
                if (nightMode != NightMode.OFF) {
                    Choice(Icons.Outlined.DarkMode, "Éjszaka kezdete", "Ettől az órától", TvOptions.hourLabel(nightStart),
                        { prefs.setNightStart(nightStart - 1) }, { prefs.setNightStart(nightStart + 1) })
                    Choice(Icons.Outlined.LightMode, "Éjszaka vége", "Eddig az óráig", TvOptions.hourLabel(nightEnd),
                        { prefs.setNightEnd(nightEnd - 1) }, { prefs.setNightEnd(nightEnd + 1) })
                }

                SectionLabel("Eszköz")
                if (!isScreensaver) {
                    SettingRow(Icons.Filled.Settings, "Képernyővédő beállítása", "Rendszerbeállítások megnyitása") {
                        runCatching { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }.onFailure {
                            Toast.makeText(this@MainActivity, "Ezen a TV-n nem érhető el – használd az adb parancsot", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                Confirm(Icons.Filled.Lock, "Telefonok leválasztása", "Utána újra párosítani kell") { prefs.forgetPhones() }
                Confirm(Icons.Filled.Delete, "Összes kép törlése", "A képek törlődnek a TV-ről") { app.store.clear() }
            }
        }
    }

    /** A setting with a value: OK and right step forward, left steps back. */
    @Composable
    private fun Choice(icon: ImageVector, title: String, subtitle: String, value: String, previous: () -> Unit, next: () -> Unit) {
        SettingRow(icon, title, subtitle, value = value, onPrevious = previous, onNext = next, onClick = next)
    }

    /** Asks for a second press before doing anything destructive. */
    @Composable
    private fun Confirm(icon: ImageVector, title: String, subtitle: String, action: () -> Unit) {
        var armed by remember { mutableStateOf(false) }
        LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }
        SettingRow(
            icon,
            if (armed) "Biztosan? Nyomd meg újra" else title,
            subtitle,
            danger = true,
        ) { if (armed) { action(); armed = false } else armed = true }
    }

    private fun formatInterval(sec: Int) = TvOptions.intervalLabel(sec)
}

/** Neighbour of [current] in the list, wrapping around. */
private fun <T> List<T>.step(current: T, by: Int): T = this[((indexOf(current).coerceAtLeast(0) + by) % size + size) % size]

private inline fun <reified T : Enum<T>> T.step(by: Int): T = enumValues<T>().toList().step(this, by)
