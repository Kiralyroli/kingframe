package com.kiroland.gallery.phone

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Pushes pending changes to the TV. Runs right after a share, when the app opens, and
 * every 15 minutes on Wi-Fi, so photos shared away from home arrive once back.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext.phoneApp
        val conn = app.connection
        val repo = app.repo
        val saved = conn.tv.value ?: return@withContext Result.success()

        conn.updateStatus { it.copy(running = true, message = "Kapcsolódás a TV-hez…") }
        try {
            fillMissingPlaces(repo)

            val tv = reach(saved) ?: run {
                conn.updateStatus { it.copy(running = false, message = "A TV nem érhető el (be van kapcsolva, ugyanazon a Wi-Fi-n?)") }
                return@withContext if (runAttemptCount < 3) Result.retry() else Result.success()
            }
            val client = TvClient(tv.host, tv.port, tv.token)
            val info = client.info()
            if (info.screenWidth != tv.screenWidth || info.screenHeight != tv.screenHeight || tv != saved) {
                conn.save(tv.copy(screenWidth = info.screenWidth, screenHeight = info.screenHeight))
            }
            val onTv = client.list().toSet()

            val snapshot = repo.photos.value
            val total = snapshot.count { it.state != SyncState.ON_TV && it.state != SyncState.MISSING }
            var done = 0
            fun progress() = conn.updateStatus { it.copy(message = "Átküldés: $done / $total") }

            for (p in snapshot) {
                val id = p.meta.id
                when (p.state) {
                    SyncState.PENDING_DELETE -> {
                        if (id in onTv) client.delete(id)
                        repo.remove(id); done++; progress()
                    }
                    SyncState.PENDING_UPLOAD -> {
                        val file = repo.outboxFile(id)
                        if (file.exists()) {
                            client.upload(repo.get(id)?.meta ?: p.meta, file)
                            repo.markOnTv(id)
                        } else repo.markMissing(id)
                        done++; progress()
                    }
                    SyncState.PENDING_META -> {
                        val meta = repo.get(id)?.meta ?: p.meta
                        if (id in onTv) { client.updateMeta(meta); repo.markMetaSent(id) } else repo.markMissing(id)
                        done++; progress()
                    }
                    SyncState.ON_TV -> if (id !in onTv) repo.markMissing(id)
                    SyncState.MISSING -> Unit
                }
            }
            conn.updateStatus {
                it.copy(running = false, lastSuccessAt = System.currentTimeMillis(), message = null, needsRepair = false)
            }
            Result.success()
        } catch (e: UnauthorizedException) {
            conn.updateStatus { it.copy(running = false, message = e.message, needsRepair = true) }
            Result.failure()
        } catch (e: IOException) {
            conn.updateStatus { it.copy(running = false, message = "Hiba: ${e.message}") }
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    /** Last known address first; if the TV got a new IP, find it again by name. */
    private suspend fun reach(tv: TvConnection): TvConnection? {
        if (runCatching { TvClient(tv.host, tv.port, tv.token).info(timeoutMs = 3000) }.isSuccess) return tv
        val found = withTimeoutOrNull(10_000) {
            TvDiscovery.discover(applicationContext)
                .map { list -> list.firstOrNull { it.name == tv.name } }
                .filterNotNull()
                .first()
        } ?: return null
        val moved = tv.copy(host = found.host, port = found.port)
        return moved.takeIf { runCatching { TvClient(it.host, it.port, it.token).info() }.isSuccess }
    }

    /** Geocoding needs internet; photos shared offline get their place name later. */
    private suspend fun fillMissingPlaces(repo: PhotoRepository) {
        val processor = PhotoProcessor(applicationContext)
        repo.photos.value
            .filter { it.meta.place == null && it.meta.latitude != null && it.meta.longitude != null }
            .forEach { p ->
                processor.placeName(p.meta.latitude!!, p.meta.longitude!!)?.let { repo.setPlace(p.meta.id, it) }
            }
    }

    companion object {
        private val wifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(wifi)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(wifi).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("sync-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
