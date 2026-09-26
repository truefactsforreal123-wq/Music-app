package com.aura.player.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aura.player.data.api.ApiService
import com.aura.player.data.db.AuraDatabase
import com.aura.player.data.db.DownloadState
import com.aura.player.data.db.TrackEntity
import com.aura.player.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Enqueues/cancels download work and exposes aggregate progress for banners. */
class DownloadRepository(
    private val context: Context,
    private val api: ApiService,
    private val settings: SettingsRepository,
    private val db: AuraDatabase,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val trackDao get() = db.trackDao()

    suspend fun enqueue(trackId: String, title: String) = withContext(Dispatchers.IO) {
        trackDao.setDownloadProgress(trackId, DownloadState.QUEUED, 0)
        val wifiOnly = settings.wifiOnly.first()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_TRACK_ID to trackId,
                    DownloadWorker.KEY_TITLE to title,
                ),
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG_DOWNLOAD)
            .addTag("$TAG_PREFIX$trackId")
            .build()
        workManager.enqueueUniqueWork("$TAG_PREFIX$trackId", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun enqueueAll(tracks: List<TrackEntity>) = withContext(Dispatchers.IO) {
        for (track in tracks) enqueue(track.id, track.title)
    }

    fun cancel(trackId: String) {
        workManager.cancelUniqueWork("$TAG_PREFIX$trackId")
    }

    /** Deletes a local file and resets its state (server copy stays cached). */
    suspend fun deleteLocal(trackId: String) = withContext(Dispatchers.IO) {
        val track = trackDao.get(trackId)
        track?.localPath?.let { path -> File(path).delete() }
        trackDao.clearDownload(trackId)
    }

    suspend fun deleteAllLocal() = withContext(Dispatchers.IO) {
        for (track in trackDao.downloadedTracks()) {
            track.localPath?.let { path -> File(path).delete() }
            trackDao.clearDownload(track.id)
        }
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_DOWNLOAD)
    }

    /** Aggregate progress for the batch banner. */
    data class BatchProgress(val active: Int, val percent: Int)

    fun observeBatch(): Flow<BatchProgress> =
        combine(trackDao.observeActiveDownloadCount(), trackDao.observeTotalDownloadSize()) { active, _ ->
            if (active == 0) {
                BatchProgress(0, 0)
            } else {
                val rows = trackDao.activeDownloads()
                val meaningful = rows.filter { it.downloadState == DownloadState.DOWNLOADING }
                val avg = if (meaningful.isEmpty()) 0 else meaningful.sumOf { it.downloadProgress } / meaningful.size
                BatchProgress(active, avg.coerceIn(0, 100))
            }
        }
            .conflate()
            .flowOn(Dispatchers.IO)

    companion object {
        const val TAG_DOWNLOAD = "aura_download"
        const val TAG_PREFIX = "aura_track_"
    }
}
