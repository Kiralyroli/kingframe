package com.kiroland.gallery.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.kiroland.gallery.shared.PhotoMeta
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Turns a photo shared from Google Photos into what the TV needs: metadata read from the
 * original EXIF (GPS, capture time) and a JPEG sized for the TV screen.
 */
class PhotoProcessor(private val context: Context) {
    private val resolver = context.contentResolver

    /** EXIF of the shared photo plus its stable id; cheap, no pixel decoding. */
    class Inspected(val uri: Uri, val id: String, val meta: PhotoMeta, val rotation: Int)

    fun inspect(sharedUri: Uri): Inspected {
        val uri = originalUri(sharedUri)
        val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) } ?: error("A kép nem nyitható meg")
        val fileName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

        val taken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
        val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
        val w = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
        val h = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)

        // The same photo arrives under a different URI (and often a different file name)
        // every time it is shared, so the id comes from what the camera recorded.
        val id = if (taken != null) {
            hash("$taken|${exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)}|$make|$model|${w}x$h")
        } else {
            resolver.openInputStream(uri)?.use { hashStream(it) } ?: error("A kép nem olvasható")
        }
        val latLong = exif.latLong?.takeUnless { it[0] == 0.0 && it[1] == 0.0 }

        val meta = PhotoMeta(
            id = id,
            takenLocal = taken?.let(::exifDateToIso),
            takenOffset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
            latitude = latLong?.get(0),
            longitude = latLong?.get(1),
            cameraMake = make,
            cameraModel = model,
            fileName = fileName,
            originalWidth = w.takeIf { it > 0 },
            originalHeight = h.takeIf { it > 0 },
        )
        return Inspected(uri, id, meta, exif.rotationDegrees)
    }

    /** Decodes straight to TV size and writes the outbox JPEG and the list thumbnail. */
    fun prepare(item: Inspected, target: Pair<Int, Int>, outFile: File, thumbFile: File): PhotoMeta {
        val (screenW, screenH) = target
        val source = ImageDecoder.createSource(resolver, item.uri)
        var outW = 0
        var outH = 0
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Whether info.size is reported rotated or not, its long and short sides are right;
            // EXIF (stored size + rotation) tells which of them is the displayed width.
            val long = maxOf(info.size.width, info.size.height)
            val short = minOf(info.size.width, info.size.height)
            val rotated = item.rotation == 90 || item.rotation == 270
            val storedW = item.meta.originalWidth ?: info.size.width
            val storedH = item.meta.originalHeight ?: info.size.height
            val landscape = (if (rotated) storedH else storedW) >= (if (rotated) storedW else storedH)
            val w = if (landscape) long else short
            val h = if (landscape) short else long
            val scale = targetScale(w, h, screenW, screenH)
            outW = (info.size.width * scale).roundToInt().coerceAtLeast(1)
            outH = (info.size.height * scale).roundToInt().coerceAtLeast(1)
            decoder.setTargetSize(outW, outH)
        }
        try {
            val tmp = File(outFile.path + ".tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            tmp.renameTo(outFile)

            val thumbScale = 360f / maxOf(bitmap.width, bitmap.height)
            val thumb = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * thumbScale).roundToInt().coerceAtLeast(1),
                (bitmap.height * thumbScale).roundToInt().coerceAtLeast(1),
                true,
            )
            thumbFile.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            thumb.recycle()
            return item.meta.copy(width = bitmap.width, height = bitmap.height, addedAt = System.currentTimeMillis())
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun placeName(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("hu-HU"))
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
                .distinct().joinToString(", ").ifEmpty { null }
        }
    }

    /**
     * Google Photos hands over MediaStore URIs whose GPS tags are redacted unless we ask
     * for the original (needs ACCESS_MEDIA_LOCATION). Falls back to the shared stream.
     */
    private fun originalUri(shared: Uri): Uri {
        if (shared.authority != MediaStore.AUTHORITY) return shared
        val original = MediaStore.setRequireOriginal(shared)
        return runCatching { resolver.openInputStream(original)?.close(); original }.getOrDefault(shared)
    }

    companion object {
        /**
         * Scale so the photo covers the screen with 25 % headroom for the slow zoom.
         * Portrait photos are shown whole, so only their height matters. Never upscales.
         */
        fun targetScale(w: Int, h: Int, screenW: Int, screenH: Int): Float {
            val headroom = 1.25f
            // Same rule as the TV's framing: below 1.2 the photo is shown whole.
            val aspect = w.toFloat() / h
            val scale = if (aspect < 1.2f) {
                screenH * headroom / h
            } else {
                maxOf(screenW * headroom / w, screenH * headroom / h)
            }
            // Keep very long panoramas decodable on a TV with little memory.
            val maxPixels = 16_000_000f
            val pixelCap = kotlin.math.sqrt(maxPixels / (w.toFloat() * h))
            return minOf(1f, scale, pixelCap, 8192f / maxOf(w, h))
        }

        private fun exifDateToIso(exif: String): String? {
            // "2025:07:10 16:21:45" → "2025-07-10T16:21:45"
            val m = Regex("""(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2}):(\d{2})""").find(exif) ?: return null
            val (y, mo, d, hh, mm, ss) = m.destructured
            return "$y-$mo-${d}T$hh:$mm:$ss"
        }

        private fun hash(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).toHex()

        private fun hashStream(input: java.io.InputStream): String {
            val md = MessageDigest.getInstance("SHA-1")
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
            return md.digest().toHex()
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }.take(24)
    }
}
