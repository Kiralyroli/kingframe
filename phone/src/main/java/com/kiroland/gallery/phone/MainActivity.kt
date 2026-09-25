package com.kiroland.gallery.phone

import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiroland.gallery.shared.Brand
import com.kiroland.gallery.shared.KingFrameWordmark
import com.kiroland.gallery.shared.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GalleryTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { HomeScreen() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SyncWorker.syncNow(this)
    }

    private enum class Filter(val label: String) { ALL("Mind"), NO_PLACE("Nincs hely"), FAVORITES("Kedvencek"), HIDDEN("Rejtett") }

    @Composable
    private fun HomeScreen() {
        val app = phoneApp
        val tv by app.connection.tv.collectAsState()
        val status by app.connection.status.collectAsState()
        val photos by app.repo.photos.collectAsState()
        var showPairing by remember { mutableStateOf(false) }
        var showSuggestions by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<String?>(null) }
        var selection by remember { mutableStateOf(emptySet<String>()) }
        var filter by remember { mutableStateOf(Filter.ALL) }
        var hasLocation by remember { mutableStateOf(hasLocationAccess()) }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasLocation = hasLocationAccess()
        }
        LaunchedEffect(Unit) { if (!hasLocation) permission.launch(mediaPermissions) }
        BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

        val live = photos.filter { it.state != SyncState.PENDING_DELETE }
        val suggestions = remember(photos) { suggestPlaces(photos) }
        val visible = when (filter) {
            Filter.ALL -> live
            Filter.NO_PLACE -> live.filter { it.meta.displayPlace == null }
            Filter.FAVORITES -> live.filter { it.meta.favorite }
            Filter.HIDDEN -> live.filter { it.meta.hidden }
        }
        val groups = remember(visible) { groupByMonth(visible) }
        val selecting = selection.isNotEmpty()

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (selecting) {
                SelectionBar(
                    selection = selection,
                    photos = live,
                    onClear = { selection = emptySet() },
                    onSelectAll = { selection = visible.map { it.meta.id }.toSet() },
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        if (!selecting) Header()
                        val connected = tv
                        if (connected == null) PairCard { showPairing = true }
                        else TvCard(connected, status, onRepair = { showPairing = true }, onUnpair = { app.connection.save(null) })
                        if (!hasLocation) PermissionCard { permission.launch(mediaPermissions) }
                        if (suggestions.isNotEmpty()) SuggestionCard(suggestions.size) { showSuggestions = true }
                        if (photos.isNotEmpty()) Stats(photos)
                        if (photos.isEmpty()) HowTo()
                        else FilterRow(filter, live) { filter = it }
                    }
                }
                if (photos.isNotEmpty() && visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Nincs ilyen kép.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                groups.forEach { group ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-${group.title}") { GroupHeader(group) }
                    items(group.photos, key = { it.meta.id }) { p ->
                        PhotoTile(
                            p,
                            selecting = selecting,
                            isSelected = p.meta.id in selection,
                            onClick = {
                                if (selecting) selection = selection.toggle(p.meta.id) else selected = p.meta.id
                            },
                            onLongClick = { selection = selection.toggle(p.meta.id) },
                        )
                    }
                }
            }
        }

        if (showPairing) PairingSheet(onDismiss = { showPairing = false })
        if (showSuggestions) SuggestionsSheet(photos, suggestions, onDismiss = { showSuggestions = false })
        selected?.let { id ->
            photos.firstOrNull { it.meta.id == id }?.let { PhotoSheet(it, suggestions[id], onDismiss = { selected = null }) }
                ?: run { selected = null }
        }
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    private class MonthGroup(val title: String, val places: String?, val photos: List<LocalPhoto>)

    private val monthFormat = DateTimeFormatter.ofPattern("yyyy. MMMM", Locale.forLanguageTag("hu-HU"))

    /** Newest month first; the header lists the month's places ("2025. augusztus · Siófok, Szántód"). */
    private fun groupByMonth(photos: List<LocalPhoto>): List<MonthGroup> =
        photos.groupBy { p -> p.meta.takenLocal?.let { runCatching { YearMonth.from(LocalDateTime.parse(it)) }.getOrNull() } }
            .entries
            .sortedWith(compareByDescending<Map.Entry<YearMonth?, List<LocalPhoto>>> { it.key != null }.thenByDescending { it.key })
            .map { (month, list) ->
                val places = list.mapNotNull { it.meta.displayPlace?.substringBefore(',') }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).joinToString(", ") { it.key }
                MonthGroup(
                    title = month?.format(monthFormat) ?: "Ismeretlen dátum",
                    places = places.ifEmpty { null },
                    photos = list.sortedByDescending { it.meta.takenLocal ?: "" },
                )
            }

    @Composable
    private fun GroupHeader(group: MonthGroup) {
        Row(Modifier.padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
            Text(group.title, style = MaterialTheme.typography.titleMedium)
            group.places?.let {
                Text(
                    "  ·  $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    private fun FilterRow(current: Filter, photos: List<LocalPhoto>, onPick: (Filter) -> Unit) {
        val counts = mapOf(
            Filter.ALL to photos.size,
            Filter.NO_PLACE to photos.count { it.meta.displayPlace == null },
            Filter.FAVORITES to photos.count { it.meta.favorite },
            Filter.HIDDEN to photos.count { it.meta.hidden },
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Filter.entries.filter { it == Filter.ALL || it == current || (counts[it] ?: 0) > 0 }.forEach { f ->
                val on = f == current
                val pill = RoundedCornerShape(50)
                Row(
                    Modifier.clip(pill)
                        .background(if (on) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, pill)
                        .clickable { onPick(f) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(f.label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "  ${counts[f] ?: 0}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    /** Top bar while photos are selected: place, favourite, hide and delete for all of them. */
    @Composable
    private fun SelectionBar(selection: Set<String>, photos: List<LocalPhoto>, onClear: () -> Unit, onSelectAll: () -> Unit) {
        val repo = phoneApp.repo
        val chosen = photos.filter { it.meta.id in selection }
        val allFavorite = chosen.isNotEmpty() && chosen.all { it.meta.favorite }
        val allHidden = chosen.isNotEmpty() && chosen.all { it.meta.hidden }
        var askPlace by remember { mutableStateOf(false) }
        var askDelete by remember { mutableStateOf(false) }

        fun done() {
            SyncWorker.syncNow(this@MainActivity)
            onClear()
        }

        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Kijelölés megszüntetése") }
            Text("${selection.size} kijelölve", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, "Összes kijelölése") }
            IconButton(onClick = { askPlace = true }) { Icon(Icons.Outlined.EditLocationAlt, "Hely megadása") }
            IconButton(onClick = { repo.setFlags(selection, favorite = !allFavorite); done() }) {
                Icon(if (allFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline, "Kedvenc", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { repo.setFlags(selection, hidden = !allHidden); done() }) {
                Icon(if (allHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (allHidden) "Megjelenítés" else "Elrejtés")
            }
            IconButton(onClick = { askDelete = true }) {
                Icon(Icons.Outlined.Delete, "Törlés a TV-ről", tint = MaterialTheme.colorScheme.error)
            }
        }

        if (askPlace) {
            var place by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { askPlace = false },
                title = { Text("Hely ${selection.size} képhez") },
                text = {
                    OutlinedTextField(
                        value = place, onValueChange = { place = it },
                        placeholder = { Text("Például Balaton") }, singleLine = true,
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { repo.setManualPlace(selection, place); askPlace = false; done() }) { Text("Mentés") }
                },
                dismissButton = { TextButton(onClick = { askPlace = false }) { Text("Mégse") } },
            )
        }
        if (askDelete) {
            AlertDialog(
                onDismissRequest = { askDelete = false },
                title = { Text("${selection.size} kép törlése a TV-ről?") },
                text = { Text("A Google Fotókban megmaradnak, csak a TV-ről tűnnek el.") },
                confirmButton = {
                    TextButton(onClick = { selection.forEach(repo::requestDelete); askDelete = false; done() }) {
                        Text("Törlés", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Mégse") } },
            )
        }
    }

    @Composable
    private fun SuggestionCard(count: Int, onOpen: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().csendCard().clickable(onClick = onOpen).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                IconRow(
                    Icons.Outlined.AutoFixHigh, "$count képhez tudok helyet javasolni",
                    "Ugyanazon a napon készült képek alapján", iconTint = MaterialTheme.colorScheme.primary,
                )
            }
            Text("Megnézem", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SuggestionsSheet(photos: List<LocalPhoto>, suggestions: Map<String, PlaceSuggestion>, onDismiss: () -> Unit) {
        val repo = phoneApp.repo
        fun accept(list: Collection<PlaceSuggestion>) {
            list.forEach { repo.setManualPlace(it.photoId, it.place) }
            SyncWorker.syncNow(this@MainActivity)
        }
        LaunchedEffect(suggestions.isEmpty()) { if (suggestions.isEmpty()) onDismiss() }
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Helyjavaslatok", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Ezeken a képeken nincs hely, de ugyanazon a napon, pár órán belül készült képeken van.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AccentButton("Mindet elfogadom (${suggestions.size})", Modifier.fillMaxWidth()) { accept(suggestions.values) }
                suggestions.values.sortedBy { it.place }.forEach { s ->
                    val photo = photos.firstOrNull { it.meta.id == s.photoId } ?: return@forEach
                    val thumb by rememberThumbnail(repo.thumbFile(photo.meta.id), photo.meta.id)
                    Row(Modifier.fillMaxWidth().csendCard().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.place, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.reason(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { accept(listOf(s)) }) {
                            Icon(Icons.Filled.Check, "Elfogadom", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header() {
        val app = phoneApp
        val theme by app.theme.collectAsState()
        Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(KingFrameWordmark, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Google Fotók hellyel és időponttal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Sötét → Világos → Rendszer szerint
            IconButton(onClick = {
                val next = ThemeMode.entries[(theme.ordinal + 1) % ThemeMode.entries.size]
                app.setTheme(next)
                Toast.makeText(this@MainActivity, "Téma: ${next.label}", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    when (theme) {
                        ThemeMode.DARK -> Icons.Outlined.DarkMode
                        ThemeMode.LIGHT -> Icons.Outlined.LightMode
                        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                    },
                    "Téma: ${theme.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun PairCard(onPair: () -> Unit) {
        Column(Modifier.fillMaxWidth().csendCard().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(40.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Kösd össze a TV-vel", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A TV-n nyisd meg a KingFrame-et, ott látod a kódot.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            AccentButton("TV hozzáadása", Modifier.fillMaxWidth(), onClick = onPair)
        }
    }

    @Composable
    private fun TvCard(tv: TvConnection, status: SyncStatus, onRepair: () -> Unit, onUnpair: () -> Unit) {
        val (label, dot) = when {
            status.running -> (status.message ?: "Szinkronizálás…") to MaterialTheme.colorScheme.primary
            status.needsRepair -> "Újra kell párosítani" to Brand.Danger
            status.message != null -> "Nem érhető el" to Brand.Warning
            else -> "Kapcsolódva" to Brand.Success
        }
        Column(Modifier.fillMaxWidth().csendCard()) {
            Row(Modifier.padding(start = 16.dp, top = 14.dp, end = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tv.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(dot)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "$label  ·  ${formatAgo(status.lastSuccessAt)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (status.running) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { SyncWorker.syncNow(this@MainActivity) }) {
                        Icon(Icons.Filled.Refresh, "Szinkronizálás", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { startActivity(Intent(this@MainActivity, TvSettingsActivity::class.java)) }) {
                    Icon(Icons.Outlined.Tune, "TV beállításai", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (!status.running && status.message != null) {
                Text(
                    status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${tv.host}  ·  ${tv.screenWidth}×${tv.screenHeight}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = if (status.needsRepair) onRepair else onUnpair) {
                    Text(if (status.needsRepair) "Újrapárosítás" else "Leválasztás")
                }
            }
        }
    }

    @Composable
    private fun PermissionCard(onGrant: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().selectedFrame(true).clickable(onClick = onGrant).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                IconRow(Icons.Filled.LocationOn, "Engedély kell a helyadatokhoz", "Fotók és videók → Mindet engedélyez", iconTint = MaterialTheme.colorScheme.primary)
            }
            Text("Megadás", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }

    @Composable
    private fun Stats(photos: List<LocalPhoto>) {
        val onTv = photos.count { it.state == SyncState.ON_TV || it.state == SyncState.PENDING_META }
        val waiting = photos.count { it.state == SyncState.PENDING_UPLOAD || it.state == SyncState.PENDING_DELETE }
        val missing = photos.count { it.state == SyncState.MISSING }
        val withPlace = photos.count { it.meta.displayPlace != null && it.state != SyncState.PENDING_DELETE }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("$onTv", "a TV-n", Modifier.weight(1f))
                StatTile("$withPlace", "hellyel", Modifier.weight(1f))
                if (missing > 0) StatTile("$missing", "újra megosztandó", Modifier.weight(1f), accent = MaterialTheme.colorScheme.error)
                else StatTile("$waiting", "várakozik", Modifier.weight(1f), accent = if (waiting > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            if (missing > 0) {
                Text(
                    "A figyelmeztető jelű képek eltűntek a TV-ről. Oszd meg őket újra a Google Fotókból.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    @Composable
    private fun HowTo() {
        Column(Modifier.fillMaxWidth().csendCard().padding(20.dp)) {
            Text("Képek küldése a TV-re", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            StepRow(1, "Nyisd meg az albumot a Google Fotókban")
            StepRow(2, "Nyomd hosszan az első képet, és húzd lefelé az ujjad")
            StepRow(3, "Megosztás → KingFrame → Hozzáadás")
            Spacer(Modifier.height(8.dp))
            Text(
                "Ha az album változik, oszd meg újra az egészet, és válaszd a „Teljes album – frissítés” lehetőséget.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun PhotoTile(p: LocalPhoto, selecting: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
        val bitmap by rememberThumbnail(phoneApp.repo.thumbFile(p.meta.id), p.meta.id)
        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier.aspectRatio(1f).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, shape),
        ) {
            bitmap?.let {
                Image(
                    it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    alpha = if (p.meta.hidden) 0.4f else 1f,
                )
            }
            // Place caption only where there is one; photos without a place are one tap away
            // in the "Nincs hely" filter, a label on every tile would just be noise.
            p.meta.displayPlace?.let { place ->
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .padding(start = 8.dp, end = 8.dp, top = 26.dp, bottom = 6.dp),
                ) {
                    Text(
                        place.substringBefore(','),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Top-left: selection tick while selecting, otherwise favourite / hidden marks.
            Row(Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    selecting -> Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (isSelected) Brand.Amber else Brand.Night0.copy(alpha = 0.45f))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (isSelected) Icon(Icons.Filled.Check, null, tint = Brand.OnAmber, modifier = Modifier.size(14.dp)) }
                    else -> {
                        if (p.meta.favorite) TileBadge(Icons.Filled.Star)
                        if (p.meta.hidden) TileBadge(Icons.Outlined.VisibilityOff)
                    }
                }
            }
            val sync = when (p.state) {
                SyncState.PENDING_UPLOAD, SyncState.PENDING_META -> Icons.Filled.Refresh
                SyncState.MISSING -> Icons.Filled.Warning
                else -> null
            }
            sync?.let { Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { TileBadge(it) } }
        }
    }

    @Composable
    private fun TileBadge(icon: ImageVector) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Brand.Night0.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Brand.Amber, modifier = Modifier.size(13.dp)) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PhotoSheet(p: LocalPhoto, suggestion: PlaceSuggestion?, onDismiss: () -> Unit) {
        val repo = phoneApp.repo
        val bitmap by rememberThumbnail(repo.thumbFile(p.meta.id), p.meta.id)
        var place by remember(p.meta.id) { mutableStateOf(p.meta.manualPlace ?: "") }
        val m = p.meta

        fun flags(favorite: Boolean? = null, hidden: Boolean? = null) {
            repo.setFlags(listOf(m.id), favorite, hidden)
            SyncWorker.syncNow(this@MainActivity)
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                bitmap?.let {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            it, null,
                            Modifier.heightIn(max = 300.dp)
                                .aspectRatio(it.width.toFloat() / it.height, matchHeightConstraintsFirst = it.height > it.width)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Column {
                    Text(m.displayPlace ?: "Ismeretlen hely", style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        val (text, color) = stateLabel(p.state)
                        Dot(color)
                        Spacer(Modifier.width(7.dp))
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip(
                        if (m.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline, "Kedvenc", m.favorite,
                        Modifier.weight(1f),
                    ) { flags(favorite = !m.favorite) }
                    ToggleChip(
                        Icons.Outlined.VisibilityOff, if (m.hidden) "Rejtve a TV-n" else "Elrejtés", m.hidden,
                        Modifier.weight(1f),
                    ) { flags(hidden = !m.hidden) }
                }
                Column(Modifier.fillMaxWidth().csendCard().padding(vertical = 4.dp)) {
                    IconRow(Icons.Filled.DateRange, m.takenText() ?: "Ismeretlen időpont", "Készült")
                    if (m.latitude != null) IconRow(Icons.Filled.Place, "%.5f, %.5f".format(m.latitude, m.longitude), "GPS-koordináta")
                    m.cameraText()?.let { IconRow(Icons.Filled.Info, it, "Fényképezőgép") }
                    m.fileName?.let { IconRow(Icons.Filled.List, it, "Fájlnév") }
                }
                suggestion?.let { s ->
                    Row(
                        Modifier.fillMaxWidth().selectedFrame(true).clickable {
                            repo.setManualPlace(m.id, s.place)
                            SyncWorker.syncNow(this@MainActivity)
                            onDismiss()
                        }.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            IconRow(Icons.Outlined.AutoFixHigh, "Javaslat: ${s.place}", s.reason(), iconTint = MaterialTheme.colorScheme.primary)
                        }
                        Text("Elfogadom", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text(if (m.place != null) "Saját helynév" else "Hely megadása kézzel") },
                    placeholder = { m.place?.let { Text(it) } },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentButton("Mentés", Modifier.weight(1f)) {
                        if (place.trim() != (m.manualPlace ?: "")) {
                            repo.setManualPlace(m.id, place)
                            SyncWorker.syncNow(this@MainActivity)
                        }
                        onDismiss()
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = {
                            repo.requestDelete(m.id)
                            SyncWorker.syncNow(this@MainActivity)
                            onDismiss()
                        },
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Törlés", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    /** On/off pill with the amber frame when on (favourite, hidden). */
    @Composable
    private fun ToggleChip(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Row(
            modifier.selectedFrame(on).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }

    @Composable
    private fun stateLabel(state: SyncState): Pair<String, Color> = when (state) {
        SyncState.ON_TV -> "A TV-n van" to Brand.Success
        SyncState.PENDING_UPLOAD -> "Átküldésre vár" to MaterialTheme.colorScheme.primary
        SyncState.PENDING_META -> "Módosítás átküldésre vár" to MaterialTheme.colorScheme.primary
        SyncState.PENDING_DELETE -> "Törlésre vár" to MaterialTheme.colorScheme.error
        SyncState.MISSING -> "Nincs a TV-n – oszd meg újra" to MaterialTheme.colorScheme.error
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PairingSheet(onDismiss: () -> Unit) {
        val found by remember { TvDiscovery.discover(this) }.collectAsState(initial = emptyList())
        var chosen by remember { mutableStateOf<FoundTv?>(null) }
        var manualOpen by remember { mutableStateOf(false) }
        var manual by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        // Pick the only TV on the network automatically.
        LaunchedEffect(found) { if (chosen == null && manual.isBlank() && found.size == 1) chosen = found.first() }

        fun target(): Pair<String, Int>? {
            chosen?.let { return it.host to it.port }
            val text = manual.trim().takeIf { it.isNotEmpty() } ?: return null
            return text.substringBefore(':') to (text.substringAfter(':', "").toIntOrNull() ?: Protocol.DEFAULT_PORT)
        }

        fun pair() {
            val (host, port) = target() ?: return
            busy = true; error = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val deviceName = Settings.Global.getString(contentResolver, "device_name") ?: Build.MODEL
                        val pair = TvClient(host, port).pair(pin, deviceName)
                        val info = TvClient(host, port, pair.token).info()
                        TvConnection(pair.tvName, host, port, pair.token, info.screenWidth, info.screenHeight)
                    }
                }
                busy = false
                result.onSuccess {
                    phoneApp.connection.save(it)
                    phoneApp.connection.updateStatus { s -> s.copy(message = null, needsRepair = false) }
                    SyncWorker.syncNow(this@MainActivity)
                    onDismiss()
                }.onFailure {
                    error = if (it.message?.contains("kód") == true) "Hibás kód. Nézd meg újra a TV-n."
                    else "Nem sikerült: ${it.message ?: it.javaClass.simpleName}"
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { if (!busy) onDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("TV hozzáadása", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "A telefon és a TV legyen ugyanazon a Wi-Fi-n, a TV-n pedig legyen nyitva a KingFrame.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionLabel("TV-k a hálózaton")
                if (found.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Keresés…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                found.forEach { tv ->
                    TvOption(tv, selected = chosen == tv) { chosen = tv; manual = ""; manualOpen = false }
                }
                if (manualOpen) {
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it; chosen = null },
                        label = { Text("Cím (a TV-n látod)") },
                        placeholder = { Text("192.168.1.20:8765") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TextButton(onClick = { manualOpen = true }) { Text("Cím megadása kézzel") }
                }
                SectionLabel("Párosító kód")
                PinField(pin) { pin = it; error = null }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(6.dp))
                AccentButton(
                    if (busy) "Párosítás…" else "Párosítás",
                    Modifier.fillMaxWidth(),
                    enabled = !busy && pin.length == 6 && target() != null,
                ) { pair() }
            }
        }
    }

    @Composable
    private fun TvOption(tv: FoundTv, selected: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().selectedFrame(selected).clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(36.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.name, style = MaterialTheme.typography.titleSmall)
                Text("${tv.host}:${tv.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }

    /** Six digits on underlined slots, like on the TV; the next slot's line is amber. */
    @Composable
    private fun PinField(value: String, onChange: (String) -> Unit) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            decorationBox = {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                    repeat(6) { i ->
                        if (i == 3) Spacer(Modifier.width(18.dp)) else if (i > 0) Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                value.getOrNull(i)?.toString() ?: " ",
                                style = MaterialTheme.typography.displaySmall,
                            )
                            Box(
                                Modifier.fillMaxWidth().height(2.dp).background(
                                    if (i == value.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                            )
                        }
                    }
                }
            },
        )
    }
}
