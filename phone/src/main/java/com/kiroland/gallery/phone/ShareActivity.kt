package com.kiroland.gallery.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.kiroland.gallery.shared.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Target of "Share → KingFrame" in Google Photos, shown as a sheet over Photos. */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uris = sharedUris(intent)
        if (savedInstanceState == null) ShareImporter.reset()
        setContent {
            GalleryTheme {
                // No Surface here (the window is see-through), so set the text color by hand.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { ShareSheet(uris) }
            }
        }
    }

    private fun sharedUris(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }

    @Composable
    private fun ShareSheet(uris: List<Uri>) {
        val app = phoneApp
        val state by ShareImporter.state.collectAsState()
        val tv by app.connection.tv.collectAsState()
        val existing by app.repo.photos.collectAsState()
        var hasLocation by remember { mutableStateOf(hasLocationAccess()) }
        var pendingMode by remember { mutableStateOf<ShareMode?>(null) }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasLocation = hasLocationAccess()
            pendingMode?.let { ShareImporter.start(this, uris, it) }
        }
        fun begin(mode: ShareMode) {
            if (hasLocationAccess()) ShareImporter.start(this, uris, mode)
            else { pendingMode = mode; permission.launch(mediaPermissions) }
        }

        LaunchedEffect(state.finished) {
            if (state.finished && state.failed == 0) { delay(2200); finish() }
        }

        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null) { if (!state.running) finish() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("KingFrame", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(
                                if (uris.size == 1) "1 kép érkezett" else "${uris.size} kép érkezett",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        ThumbStack(uris)
                    }
                    if (tv == null) Notice("Még nincs TV párosítva. Előkészítem a képeket, és a párosítás után átküldöm őket.")
                    if (!hasLocation) Notice("A hely kiolvasásához engedély kell: Fotók és videók → Mindet engedélyez.")

                    when {
                        uris.isEmpty() -> Text("Nem érkezett kép.")
                        state.finished -> Done(state)
                        state.running -> Working(state)
                        else -> Choice(showAlbum = existing.isNotEmpty(), onAdd = { begin(ShareMode.ADD) }, onAlbum = { begin(ShareMode.ALBUM) })
                    }
                }
            }
        }
    }

    @Composable
    private fun Choice(showAlbum: Boolean, onAdd: () -> Unit, onAlbum: () -> Unit) {
        AccentButton("Hozzáadás a TV-hez", Modifier.fillMaxWidth(), onClick = onAdd)
        if (showAlbum) {
            Row(
                Modifier.fillMaxWidth().csendCard()
                    .clickable(onClick = onAlbum).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Teljes album – frissítés", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A TV-n csak ezek maradnak: az újakat átküldöm, a többit törlöm.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    @Composable
    private fun Working(state: ImportState) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (state.total == 0) 0f else state.processed.toFloat() / state.total },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text("${state.processed}/${state.total}", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Képek előkészítése…", style = MaterialTheme.typography.titleMedium)
                Text("Hagyd nyitva, amíg végez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Counters(state)
    }

    @Composable
    private fun ColumnScope.Done(state: ImportState) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Brand.Amber), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Brand.OnAmber, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(if (state.failed == 0) "Kész!" else "Kész, hibákkal", style = MaterialTheme.typography.titleLarge)
                Text("A képek a háttérben kerülnek át a TV-re.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Counters(state)
        if (state.failed > 0) {
            Text("Nem sikerült: ${state.failed} (${state.lastError})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.End)) { Text("Bezárás") }
    }

    @Composable
    private fun Counters(state: ImportState) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Counter("${state.added}", "új")
            Counter("${state.alreadyThere}", "már megvolt")
            Counter("${state.withPlace}", "hellyel", Icons.Filled.LocationOn)
            if (state.removed > 0) Counter("${state.removed}", "törölve")
        }
    }

    @Composable
    private fun Counter(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(value, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun Notice(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
        )
    }

    /** Up to three overlapping previews of what was shared. */
    @Composable
    private fun ThumbStack(uris: List<Uri>) {
        val shown = uris.take(3)
        Box(Modifier.size(width = (56 + (shown.size - 1).coerceAtLeast(0) * 22).dp, height = 56.dp)) {
            shown.forEachIndexed { i, uri ->
                val thumb by produceState<ImageBitmap?>(null, uri) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { contentResolver.loadThumbnail(uri, Size(160, 160), null).asImageBitmap() }.getOrNull()
                    }
                }
                Box(
                    Modifier.offset(x = (i * 22).dp).size(56.dp).clip(RoundedCornerShape(14.dp))
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
        }
    }
}
