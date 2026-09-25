package com.kiroland.gallery.sharetest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class PhotoReport(
    val uri: Uri,
    val displayName: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val dateTimeOriginal: String?,
    val offsetTimeOriginal: String?,
    val latLong: DoubleArray?,
    val altitude: Double?,
    val place: String?,
    val make: String?,
    val model: String?,
    val xmpHasGps: Boolean,
    val allTags: List<Pair<String, String>>,
    val thumbnail: Bitmap?,
    val error: String?,
    val originalNote: String? = null,
    val mediaInfo: String? = null,
) {
    val hasGps get() = latLong != null

    fun toText(): String = buildString {
        appendLine("== ${displayName ?: uri.lastPathSegment}")
        appendLine("forrás: ${uri.authority}${originalNote?.let { " – $it" } ?: ""}")
        mediaInfo?.let { appendLine("médiatár: $it") }
        appendLine("típus: $mimeType, méret: ${sizeBytes?.let { "%.2f MB".format(it / 1_048_576.0) }}, ${width}x$height")
        appendLine("készült: ${dateTimeOriginal ?: "-"} ${offsetTimeOriginal ?: ""}")
        appendLine("GPS: ${latLong?.let { "%.5f, %.5f".format(it[0], it[1]) } ?: "NINCS"}")
        appendLine("hely: ${place ?: "-"}")
        appendLine("kamera: ${listOfNotNull(make, model).joinToString(" ").ifEmpty { "-" }}")
        appendLine("XMP-ben GPS: ${if (xmpHasGps) "igen" else "nem"}")
        appendLine("EXIF mezők száma: ${allTags.size}")
        error?.let { appendLine("HIBA: $it") }
    }
}

object ExifReader {

    private val allTagNames: List<String> by lazy {
        ExifInterface::class.java.fields
            .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
            .mapNotNull { it.get(null) as? String }
            .distinct()
            .sorted()
    }

    suspend fun read(context: Context, sharedUri: Uri): PhotoReport = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        // For MediaStore URIs the GPS tags are redacted unless we ask for the original,
        // which needs ACCESS_MEDIA_LOCATION. Fall back to the (redacted) shared stream.
        var originalNote: String? = null
        val uri = if (sharedUri.authority == MediaStore.AUTHORITY) {
            val original = MediaStore.setRequireOriginal(sharedUri)
            try {
                resolver.openInputStream(original)?.close()
                originalNote = "eredeti fájl (nem cenzúrázott)"
                original
            } catch (e: Exception) {
                originalNote = "csak cenzúrázott másolat: ${e.javaClass.simpleName}"
                sharedUri
            }
        } else sharedUri

        var displayName: String? = null
        var size: Long? = null
        var mediaInfo: String? = null
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                        ?.let { displayName = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }
                        ?.let { size = c.getLong(it) }
                    mediaInfo = listOf("relative_path", "owner_package_name", "latitude", "longitude")
                        .mapNotNull { col ->
                            c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }
                                ?.let { "$col=${c.getString(it)}" }
                        }.joinToString(", ").ifEmpty { null }
                }
            }
        }
        val mime = resolver.getType(uri)

        try {
            val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?: error("A fájl nem nyitható meg")
            val latLong = exif.latLong
            val altitude = exif.getAltitude(Double.NaN).takeUnless { it.isNaN() }
            val xmp = exif.getAttribute(ExifInterface.TAG_XMP)
            val tags = allTagNames.mapNotNull { tag ->
                exif.getAttribute(tag)?.takeIf { it.isNotBlank() && tag != ExifInterface.TAG_XMP }
                    ?.let { tag to it.take(120) }
            }
            PhotoReport(
                uri = sharedUri,
                displayName = displayName,
                sizeBytes = size,
                mimeType = mime,
                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 },
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 },
                dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                offsetTimeOriginal = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                latLong = latLong,
                altitude = altitude,
                place = latLong?.let { reverseGeocode(context, it[0], it[1]) },
                make = exif.getAttribute(ExifInterface.TAG_MAKE),
                model = exif.getAttribute(ExifInterface.TAG_MODEL),
                xmpHasGps = xmp?.contains("GPS", ignoreCase = true) == true,
                allTags = tags,
                thumbnail = decodeThumbnail(context, uri),
                error = null,
                originalNote = originalNote,
                mediaInfo = mediaInfo,
            )
        } catch (e: Exception) {
            PhotoReport(
                sharedUri, displayName, size, mime, null, null, null, null, null, null, null,
                null, null, false, emptyList(), null, e.message ?: e.javaClass.simpleName,
                originalNote, mediaInfo,
            )
        }
    }

    private fun decodeThumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return "(Geocoder nem elérhető)"
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
        }
        return address?.let {
            listOfNotNull(it.locality ?: it.subAdminArea ?: it.adminArea, it.countryName)
                .distinct().joinToString(", ")
        }
    }
}
