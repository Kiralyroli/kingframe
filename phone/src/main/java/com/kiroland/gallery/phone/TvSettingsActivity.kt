package com.kiroland.gallery.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kiroland.gallery.shared.Brand
import com.kiroland.gallery.shared.Geocoding
import com.kiroland.gallery.shared.TvOption
import com.kiroland.gallery.shared.TvOptions
import com.kiroland.gallery.shared.TvSettings
import com.kiroland.gallery.shared.WeatherPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The TV's slideshow settings, changed from the phone – easier than with the remote. */
class TvSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GalleryTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen() }
            }
        }
    }

    private sealed interface Sheet {
        data class Pick(val title: String, val options: List<TvOption>, val selected: String, val apply: (String) -> TvSettings) : Sheet
        data object Place : Sheet
    }

    @Composable
    private fun Screen() {
        val tv by phoneApp.connection.tv.collectAsState()
        var settings by remember { mutableStateOf<TvSettings?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var loadKey by remember { mutableStateOf(0) }
        var sheet by remember { mutableStateOf<Sheet?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(loadKey) {
            val conn = tv ?: run { error = "Nincs TV párosítva."; return@LaunchedEffect }
            error = null
            settings = null
            withContext(Dispatchers.IO) { runCatching { TvClient(conn.host, conn.port, conn.token).getSettings() } }
                .onSuccess { settings = it }
                .onFailure {
                    error = if (it.message?.contains("Ismeretlen") == true) "Frissítsd a TV-s alkalmazást, ez a verzió még nem tudja."
                    else "A TV nem érhető el. Be van kapcsolva, ugyanazon a Wi-Fi-n?"
                }
        }

        /** Shows the change at once and sends it; on failure reloads what the TV really has. */
        fun change(new: TvSettings) {
            val conn = tv ?: return
            settings = new
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { TvClient(conn.host, conn.port, conn.token).putSettings(new) } }
                    .onFailure { loadKey++ }
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.padding(start = 4.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vissza") }
                Column {
                    Text("TV beállításai", style = MaterialTheme.typography.titleLarge)
                    tv?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            val s = settings
            when {
                error != null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium)
                    AccentButton("Újra", Modifier.width(140.dp)) { loadKey++ }
                }
                s == null -> Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Kapcsolódás a TV-hez…")
                }
                else -> Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SectionLabel("Diavetítés")
                    Group {
                        val intervals = TvOptions.intervals.map { TvOption(it.toString(), TvOptions.intervalLabel(it)) }
                        PickRow(Icons.Outlined.Timer, "Időköz", "Mennyi ideig látszik egy kép", TvOptions.intervalLabel(s.intervalSec)) {
                            sheet = Sheet.Pick("Időköz", intervals, s.intervalSec.toString()) { s.copy(intervalSec = it.toInt()) }
                        }
                        Divider()
                        PickRow(Icons.Outlined.Shuffle, "Sorrend", "Véletlenszerű vagy időrendi", TvOptions.label(TvOptions.orders, s.order)) {
                            sheet = Sheet.Pick("Sorrend", TvOptions.orders, s.order) { s.copy(order = it) }
                        }
                        Divider()
                        PickRow(Icons.Outlined.Transform, "Áttűnés", "Hogyan vált a következő képre", TvOptions.label(TvOptions.transitions, s.transition)) {
                            sheet = Sheet.Pick("Áttűnés", TvOptions.transitions, s.transition) { s.copy(transition = it) }
                        }
                        Divider()
                        PickRow(Icons.Outlined.Speed, "Áttűnés sebessége", "Milyen gyorsan vált", TvOptions.label(TvOptions.speeds, s.transitionSpeed)) {
                            sheet = Sheet.Pick("Áttűnés sebessége", TvOptions.speeds, s.transitionSpeed) { s.copy(transitionSpeed = it) }
                        }
                        Divider()
                        PickRow(Icons.Outlined.ZoomIn, "Mozgás", "Lassú nagyítás a képen", TvOptions.label(TvOptions.motions, s.motion)) {
                            sheet = Sheet.Pick("Mozgás", TvOptions.motions, s.motion) { s.copy(motion = it) }
                        }
                        Divider()
                        SwitchRow(Icons.Outlined.Cake, "Ezen a napon", "Régi képek az évfordulójukon gyakrabban", s.onThisDay) { change(s.copy(onThisDay = it)) }
                    }

                    SectionLabel("Megjelenítés")
                    Group {
                        SwitchRow(Icons.Outlined.Schedule, "Óra", "Idő a jobb felső sarokban", s.showClock) { change(s.copy(showClock = it)) }
                        Divider()
                        SwitchRow(Icons.Outlined.WbSunny, "Időjárás", "Hőmérséklet és ég az óra mellett", s.showWeather) { change(s.copy(showWeather = it)) }
                        Divider()
                        PickRow(Icons.Outlined.LocationOn, "Időjárás helyszíne", "Melyik város időjárása", s.weatherPlace?.name ?: "Nincs") {
                            sheet = Sheet.Place
                        }
                        Divider()
                        SwitchRow(Icons.Outlined.CameraAlt, "Kamera adatai", "Fényképezőgép a felirat alatt", s.showCamera) { change(s.copy(showCamera = it)) }
                    }

                    SectionLabel("Éjszaka")
                    Group {
                        PickRow(Icons.Outlined.Bedtime, "Éjszakai mód", "Mi látszik éjszaka", TvOptions.label(TvOptions.nightModes, s.nightMode)) {
                            sheet = Sheet.Pick("Éjszakai mód", TvOptions.nightModes, s.nightMode) { s.copy(nightMode = it) }
                        }
                        if (s.nightMode != "OFF") {
                            val hours = (0..23).map { TvOption(it.toString(), TvOptions.hourLabel(it)) }
                            Divider()
                            PickRow(Icons.Outlined.DarkMode, "Éjszaka kezdete", "Ettől az órától", TvOptions.hourLabel(s.nightStartHour)) {
                                sheet = Sheet.Pick("Éjszaka kezdete", hours, s.nightStartHour.toString()) { s.copy(nightStartHour = it.toInt()) }
                            }
                            Divider()
                            PickRow(Icons.Outlined.LightMode, "Éjszaka vége", "Eddig az óráig", TvOptions.hourLabel(s.nightEndHour)) {
                                sheet = Sheet.Pick("Éjszaka vége", hours, s.nightEndHour.toString()) { s.copy(nightEndHour = it.toInt()) }
                            }
                        }
                    }
                    Text(
                        "A változás azonnal megjelenik a TV-n. Időjárásadatok: Open-Meteo.com",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        when (val sh = sheet) {
            is Sheet.Pick -> PickSheet(sh.title, sh.options, sh.selected, onDismiss = { sheet = null }) { value ->
                change(sh.apply(value)); sheet = null
            }
            Sheet.Place -> settings?.let { s ->
                PlaceSheet(s.weatherPlace, onDismiss = { sheet = null }) { place ->
                    change(s.copy(weatherPlace = place)); sheet = null
                }
            }
            null -> Unit
        }
    }

    @Composable
    private fun Group(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().csendCard()) { content() }
    }

    @Composable
    private fun Divider() = HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)

    @Composable
    private fun PickRow(icon: ImageVector, title: String, subtitle: String, value: String, onClick: () -> Unit) {
        Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            IconRow(icon, title, subtitle) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    @Composable
    private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Box(Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
            IconRow(icon, title, subtitle) {
                Switch(
                    checked = checked, onCheckedChange = onChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Brand.Amber, checkedThumbColor = Brand.OnAmber,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PickSheet(title: String, options: List<TvOption>, selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 6.dp))
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().selectedFrame(o.value == selected).clickable { onPick(o.value) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(o.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (o.value == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PlaceSheet(current: WeatherPlace?, onDismiss: () -> Unit, onPick: (WeatherPlace?) -> Unit) {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
        var status by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(query) {
            if (query.trim().length < 2) { results = emptyList(); status = null; return@LaunchedEffect }
            delay(350)
            status = "Keresés…"
            val found = withContext(Dispatchers.IO) { runCatching { Geocoding.search(query) } }
            results = found.getOrDefault(emptyList())
            status = when {
                found.isFailure -> "Nem sikerült keresni. Van internet?"
                results.isEmpty() -> "Nincs találat"
                else -> null
            }
        }
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Időjárás helyszíne", style = MaterialTheme.typography.headlineSmall)
                Text(
                    current?.let { "Most: ${it.name}" } ?: "Még nincs beállítva",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Város, például Szeged") },
                    singleLine = true, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                results.forEach { place ->
                    Box(Modifier.fillMaxWidth().csendCard().clickable { onPick(place.copy(detail = null)) }) {
                        IconRow(Icons.Outlined.LocationOn, place.name, place.detail)
                    }
                }
                if (current != null) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().csendCard().clickable { onPick(null) }) {
                        IconRow(Icons.Outlined.Close, "Helyszín törlése", "Az időjárás nem jelenik meg", iconTint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
