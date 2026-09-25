package com.kiroland.gallery.tv

import com.kiroland.gallery.shared.WeatherPlace
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiroland.gallery.shared.Brand
import kotlinx.coroutines.delay

/** Pick the town the screensaver shows the weather for; typed with the remote or voice. */
class WeatherPlaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }

    @Composable
    private fun Screen() {
        val prefs = tvApp.prefs
        val current by prefs.weatherPlace.collectAsState()
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
        var status by remember { mutableStateOf<String?>(null) }

        // Search as the user types, with a short pause so we don't query every letter.
        LaunchedEffect(query) {
            if (query.trim().length < 2) { results = emptyList(); status = null; return@LaunchedEffect }
            delay(400)
            status = "Keresés…"
            val found = runCatching { WeatherService.search(query) }
            results = found.getOrDefault(emptyList())
            status = when {
                found.isFailure -> "Nem sikerült keresni. Van internet?"
                results.isEmpty() -> "Nincs találat"
                else -> null
            }
        }

        val field = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

        Column(
            Modifier.fillMaxSize().background(Brand.Night0).padding(horizontal = 52.dp, vertical = 36.dp),
        ) {
            Text("Időjárás helyszíne", style = tvText(24.sp, FontWeight.Medium))
            Text(
                current?.let { "Most: ${it.name}" } ?: "Még nincs beállítva",
                style = tvText(13.sp, color = Brand.TextLow),
            )
            Spacer(Modifier.height(24.dp))

            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val shape = RoundedCornerShape(10.dp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = tvText(20.sp),
                cursorBrush = SolidColor(Brand.Amber),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.width(520.dp).focusRequester(field),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brand.Night2, shape)
                            .border(1.dp, if (focused) Brand.Amber else Brand.NightLine, shape)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (query.isEmpty()) Text("Város neve, például Szeged", style = tvText(20.sp, color = Brand.TextLow))
                        inner()
                    }
                },
            )
            Text(
                "OK-val nyílik a billentyűzet; a távirányító mikrofonjával be is mondhatod.",
                style = tvText(12.sp, color = Brand.TextLow),
                modifier = Modifier.padding(top = 6.dp),
            )

            Column(Modifier.width(520.dp).weight(1f).padding(top = 12.dp).verticalScroll(rememberScrollState())) {
                status?.let { Text(it, style = tvText(14.sp, color = Brand.TextMid), modifier = Modifier.padding(12.dp)) }
                results.forEach { place ->
                    SettingRow(Icons.Filled.LocationOn, place.name, place.detail ?: "") {
                        prefs.setWeatherPlace(place)
                        finish()
                    }
                }
                if (current != null) {
                    SectionLabel("Beállítás")
                    SettingRow(Icons.Filled.Close, "Helyszín törlése", "Az időjárás nem jelenik meg", danger = true) {
                        prefs.setWeatherPlace(null)
                        finish()
                    }
                }
            }
            Text("Időjárásadatok: Open-Meteo.com (CC BY 4.0)", style = tvText(11.sp, color = Brand.TextFaint))
        }
    }
}
