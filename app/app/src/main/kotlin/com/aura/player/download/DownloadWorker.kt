package com.aura.player.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aura.player.AuraApplication
import com.aura.player.R
import com.aura.player.data.db.DownloadState
import kotlinx.coroutines.delay
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Two-phase download with real progress:
 *  1. PREPARING — the server materializes the audio file (indeterminate phase)
 *  2. DOWNLOADING — byte-for-byte transfer with Content-Length → exact percent
 * Progress is published to WorkManager (UI bars) and to the system notification.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as AuraApplication).container
    private val trackId get() = inputData.getString(KEY_TRACK_ID) ?: ""

    companion object {
        const val KEY_TRACK_ID = "trackId"
        const val KEY_TITLE = "title"
        const val PROGRESS = "progress"
        const val PHASE = "phase"
        const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID_BASE = 4200
    }

    override suspend fun doWork(): Result {
        if (trackId.isBlank()) return Result.failure()
        val track = container.library.track(trackId) ?: return Result.failure()

        if (track.downloadState == DownloadState.DONE && File(track.localPath ?: "").exists()) {
            return Result.success()
        }

        val title = inputData.getString(KEY_TITLE) ?: track.title
        createChannel()
        setForeground(foregroundInfo(title, -1))

        try {
            container.db.trackDao().setDownloadProgress(trackId, DownloadState.PREPARING, 0)
            setProgress(workDataOf(PROGRESS to -1, PHASE to "preparing"))

            // Phase 1: ask the server to materialize the file, poll until ready.
            // Body carries metadata so a host-side DB reset self-heals on this call.
            runCatching {
                container.api.prepare(
                    trackId,
                    com.aura.player.data.api.PrepareBody(
                        title = track.title,
                        artist = track.artist,
                        durationMs = track.durationMs,
                        artUrl = track.artUrl,
                        sourceUrl = track.sourceUrl,
                    ),
                )
            }
            var polls = 0
            while (true) {
                val status = runCatching { container.api.downloadStatus(trackId) }
                    .getOrElse { throw IOException("Server unreachable: ${it.message}") }
                if (status.ready) break
                status.error?.let { throw IOException(it) }
                if (++polls > 400) throw IOException("Server took too long to prepare the track")
                delay(1500)
            }

            // Phase 2: transfer the file with exact progress.
            container.db.trackDao().setDownloadProgress(trackId, DownloadState.DOWNLOADING, 0)
            val file = downloadFile(trackId)
            container.db.trackDao().markDownloaded(trackId, file.absolutePath, file.length())
            NotificationManagerCompat.from(applicationContext).cancel(NOTIF_ID_BASE + trackId.hashCode())
            return Result.success()
        } catch (t: Throwable) {
            if (isRetryable(t) && runAttemptCount < 3) return Result.retry()
            container.db.trackDao().markDownloadError(trackId, DownloadState.ERROR, t.message ?: "Download failed")
            NotificationManagerCompat.from(applicationContext).cancel(NOTIF_ID_BASE + trackId.hashCode())
            return Result.failure()
        }
    }

    private suspend fun downloadFile(trackId: String): File {
        val settings = container.settings
        val base = settings.serverUrlHttp()
        val url = "${base.scheme}://${base.host}:${base.port}/api/tracks/${java.net.URLEncoder.encode(trackId, "UTF-8")}/download"
        val client = container.okHttpClient
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Empty download body")
            val total = body.contentLength()
            val dir = File(applicationContext.filesDir, "audio").apply { mkdirs() }
            val ext = when (body.contentType()?.subtype) {
                "mpeg" -> "mp3"
                "mp4" -> "m4a"
                "webm" -> "webm"
                "flac" -> "flac"
                "ogg" -> "ogg"
                else -> "m4a"
            }
            val file = File(dir, "${trackId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.$ext")

            var lastPublish = 0L
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastPublish > 400) { // throttle: UI updates at ~2.5 Hz
                            lastPublish = now
                            val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
                            setProgress(workDataOf(PROGRESS to pct, PHASE to "downloading"))
                            updateNotification(pct)
                        }
                    }
                }
            }
            return file
        }
    }

    private fun updateNotification(percent: Int) {
        val title = inputData.getString(KEY_TITLE) ?: "Downloading"
        val builder = notificationBuilder(title)
            .setProgress(if (percent < 0) 0 else 100, if (percent < 0) 0 else percent, percent < 0)
            .setContentText(if (percent < 0) "Preparing…" else "$percent%")
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_BASE + trackId.hashCode(), builder.build())
        }
    }

    private fun notificationBuilder(title: String): NotificationCompat.Builder =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_aura)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

    private fun foregroundInfo(title: String, percent: Int): ForegroundInfo {
        val builder = notificationBuilder(title)
            .setProgress(0, 0, true)
            .setContentText("Downloading…")
        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID_BASE + trackId.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID_BASE + trackId.hashCode(), notification)
        }
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.notif_channel_downloads), NotificationManager.IMPORTANCE_LOW).apply {
                    description = applicationContext.getString(R.string.notif_channel_downloads_desc)
                },
            )
        }
    }

    private fun isRetryable(t: Throwable): Boolean =
        t is IOException || t is java.net.SocketTimeoutException
}
