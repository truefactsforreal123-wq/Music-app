package com.aura.player.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aura.player.di.AppContainer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The single media session for the whole app. Registering here is what makes
 * every headset work: Bluetooth AVRCP buttons, wired 3-button remotes,
 * keyboard media keys, the lock screen and the notification all route through
 * this session. Also handles audio focus, unplug auto-pause and stream caching.
 */
class PlaybackService : MediaSessionService() {

    private val container: AppContainer by lazy { (application as com.aura.player.AuraApplication).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var sleepJob: Job? = null

    companion object {
        const val CMD_SLEEP_TIMER = "aura.SET_SLEEP_TIMER"
        const val CMD_SKIP_SILENCE = "aura.SET_SKIP_SILENCE"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_ENABLED = "enabled"
    }

    override fun onCreate() {
        super.onCreate()

        val httpFactory: DataSource.Factory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Aura/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        val cacheFactory = CacheDataSource.Factory()
            .setCache(container.streamCache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // headphone unplug → pause, never speaker-blast
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exoPlayer = player

        scope.launch {
            player.skipSilenceEnabled = container.settings.skipSilence.firstOrNull() ?: false
        }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                val duration = player.duration
                scope.launch(Dispatchers.IO) {
                    container.library.recordPlay(id)
                    if (duration > 0) container.db.trackDao().backfillDuration(id, duration)
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {

        /**
         * The controller sends tracks as ids + metadata; the session fills in
         * playable URIs here — local file first (offline), then a resolved
         * stream URL with graceful skipping of dead tracks.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            return scope.future(Dispatchers.IO) { resolveItems(mediaItems) }
        }

        override fun onCustomCommand(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SLEEP_TIMER -> {
                    val minutes = args.getInt(EXTRA_MINUTES, 0)
                    setSleepTimer(minutes)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_SKIP_SILENCE -> {
                    exoPlayer?.skipSilenceEnabled = args.getBoolean(EXTRA_ENABLED, false)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }
    }

    private suspend fun resolveItems(items: List<MediaItem>): MutableList<MediaItem> {
        val resolved = mutableListOf<MediaItem>()
        for (item in items) {
            val trackId = item.mediaId
            if (trackId.isBlank()) continue
            val track = container.db.trackDao().get(trackId)
            val uri = withContext(Dispatchers.IO) { playableUri(trackId, track?.localPath) }
            if (uri == null) continue // unresolvable track → skip rather than fail the whole queue
            val metadata = (item.mediaMetadata.buildUpon()
                .apply {
                    if (!track?.title.isNullOrBlank()) setTitle(track!!.title)
                    if (!track?.artist.isNullOrBlank()) setArtist(track!!.artist)
                    track?.artUrl?.let { art -> setArtworkUri(android.net.Uri.parse(art)) }
                    if ((track?.durationMs ?: 0) > 0) setDurationMs(track!!.durationMs)
                })
                .build()
            resolved.add(
                MediaItem.Builder()
                    .setMediaId(trackId)
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build(),
            )
        }
        if (resolved.isEmpty()) {
            throw IllegalStateException("Could not resolve any track for playback (is the server reachable?)")
        }
        return resolved
    }

    /** Local download wins; otherwise ask the server for a fresh stream URL. */
    private suspend fun playableUri(trackId: String, localPath: String?): android.net.Uri? {
        if (!localPath.isNullOrBlank() && java.io.File(localPath).exists()) {
            return android.net.Uri.fromFile(java.io.File(localPath))
        }
        // Pass metadata as query params so the server can re-seed its row if its
        // ephemeral DB was reset (phone's Room DB is the source of truth).
        val track = container.db.trackDao().get(trackId)
        return runCatching {
                container.api.stream(
                    trackId,
                    track?.title,
                    track?.artist,
                    track?.durationMs?.takeIf { it > 0 },
                    track?.artUrl,
                ).url
            }
            .getOrNull()
            ?.let { android.net.Uri.parse(it) }
    }

    /** Sleep timer with a gentle 5-second fade before pause. */
    private fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) return
        sleepJob = scope.launch {
            delay(TimeUnit.MINUTES.toMillis(minutes.toLong()))
            val player = mediaSession?.player ?: return@launch
            repeat(10) {
                player.volume = (10 - it) / 10f
                delay(500)
            }
            player.pause()
            player.volume = 1f
        }
    }
}
