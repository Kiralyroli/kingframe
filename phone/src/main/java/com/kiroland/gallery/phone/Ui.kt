package com.kiroland.gallery.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.kiroland.gallery.shared.PhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun rememberThumbnail(file: File, key: Any): State<ImageBitmap?> = produceState<ImageBitmap?>(null, file, key) {
    value = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
    }
}

/** Media read + location access: needed to get GPS out of photos shared by Google Photos. */
val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

fun Context.hasLocationAccess(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

private val hungarian = Locale.forLanguageTag("hu-HU")
private val takenFormat = DateTimeFormatter.ofPattern("yyyy. MMMM d. HH:mm", hungarian)

fun PhotoMeta.takenText(): String? = takenLocal?.let {
    runCatching { LocalDateTime.parse(it).format(takenFormat) }.getOrNull()
}

/** "samsung SM-A528B", or just the model when it already starts with the maker. */
fun PhotoMeta.cameraText(): String? {
    val make = cameraMake?.trim().orEmpty()
    val model = cameraModel?.trim().orEmpty()
    val text = if (make.isNotEmpty() && model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
    return text.ifBlank { null }
}

fun formatAgo(millis: Long): String {
    if (millis == 0L) return "még soha"
    val min = (System.currentTimeMillis() - millis) / 60_000
    return when {
        min < 1 -> "az imént"
        min < 60 -> "$min perce"
        min < 24 * 60 -> "${min / 60} órája"
        else -> "${min / (24 * 60)} napja"
    }
}
