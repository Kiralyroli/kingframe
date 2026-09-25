package com.kiroland.gallery.sharetest

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Job
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val incoming = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming.value = sharedUris(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen(incoming.value) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = sharedUris(intent)
    }

    private fun sharedUris(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }

    @Composable
    private fun Screen(shared: List<Uri>) {
        val scope = rememberCoroutineScope()
        val reports = remember { mutableStateListOf<PhotoReport>() }
        var loading by remember { mutableStateOf(0) }
        var job by remember { mutableStateOf<Job?>(null) }

        fun analyze(uris: List<Uri>) {
            job?.cancel()
            reports.clear()
            loading = uris.size
            job = scope.launch {
                uris.distinct().forEach { uri ->
                    reports += ExifReader.read(this@MainActivity, uri)
                    loading--
                }
                loading = 0
            }
        }

        var permText by remember { mutableStateOf(permissionSummary()) }
        var lastUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
        val permission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            permText = permissionSummary()
            if (lastUris.isNotEmpty()) analyze(lastUris)
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
            if (it.isNotEmpty()) { lastUris = it; analyze(it) }
        }
        LaunchedEffect(Unit) { permission.launch(mediaPermissions) }
        LaunchedEffect(shared) { if (shared.isNotEmpty()) { lastUris = shared; analyze(shared) } }
        val resumes = resumeCount.intValue
        LaunchedEffect(resumes) {
            val now = permissionSummary()
            if (now != permText) {
                permText = now
                if (lastUris.isNotEmpty()) analyze(lastUris)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("TV Galéria – helyadat teszt", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "A Google Fotókban jelölj ki képeket → Megosztás → „TV Galéria teszt”. " +
                        "Próbáld ki csak felhőben lévő, régi képpel és kézzel beállított helyű képpel is.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Engedélyek: $permText",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!hasMediaLocation()) {
                    TextButton(onClick = { openAppSettings() }) {
                        Text("Engedélyek beállítása (Fotók és videók → Mindig engedélyez)")
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Kép választása") }
                    Button(
                        enabled = reports.isNotEmpty(),
                        onClick = { copyReport(reports) },
                    ) { Text("Eredmény másolása") }
                }
            }
            if (reports.isNotEmpty() || loading > 0) {
                item {
                    val withGps = reports.count { it.hasGps }
                    Text(
                        buildString {
                            append("${reports.size} kép, ebből $withGps-ben van GPS")
                            if (loading > 0) append(" · még $loading feldolgozás alatt…")
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            items(reports, key = { it.uri.toString() }) { ReportCard(it) }
        }
    }

    @Composable
    private fun ReportCard(r: PhotoReport) {
        var expanded by remember { mutableStateOf(false) }
        val ok = Color(0xFF2E7D32)
        val bad = Color(0xFFC62828)
        Card(colors = CardDefaults.cardColors()) {
            Column(Modifier.padding(12.dp)) {
                r.thumbnail?.let {
                    Image(
                        it.asImageBitmap(), null,
                        Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(
                    if (r.hasGps) "✔ Van helyadat" else "✘ Nincs helyadat",
                    color = if (r.hasGps) ok else bad,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(r.toText(), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                if (r.allTags.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "EXIF mezők elrejtése" else "Összes EXIF mező (${r.allTags.size})")
                    }
                    if (expanded) {
                        Text(
                            r.allTags.joinToString("\n") { (k, v) -> "$k = $v" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    private val mediaPermissions: Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun hasMediaLocation() = granted(Manifest.permission.ACCESS_MEDIA_LOCATION)

    private fun permissionSummary(): String =
        mediaPermissions.joinToString(", ") { "${it.substringAfterLast('.')}=${if (granted(it)) "igen" else "nem"}" }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    private val resumeCount = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        // Returning from system settings: re-run the analysis with the new permissions.
        resumeCount.intValue++
    }

    private fun copyReport(reports: List<PhotoReport>) {
        val text = buildString {
            appendLine("TV Galéria teszt – ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            appendLine("Engedélyek: ${permissionSummary()}")
            appendLine("${reports.size} kép, GPS: ${reports.count { it.hasGps }}")
            appendLine()
            reports.forEach { appendLine(it.toText()) }
        }
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("TV Galéria teszt", text))
        Toast.makeText(this, "Vágólapra másolva", Toast.LENGTH_SHORT).show()
    }
}
