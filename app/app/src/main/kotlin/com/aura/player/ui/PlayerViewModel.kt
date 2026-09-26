package com.aura.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.aura.player.data.db.DownloadState
import com.aura.player.data.db.TrackEntity
import com.aura.player.di.AppContainer
import com.aura.player.player.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.os.Bundle

/**
 * Single shared player state for the whole UI. Reads from the MediaController
 * (never owns the player) and publishes lightweight state flows so the UI
 * recomposes only when values actually change.
 */
class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    private val _player = MutableStateFlow<MediaController?>(null)
    val player: StateFlow<MediaController?> = _player

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackId: StateFlow<String?> = _currentTrackId

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    private var tickerJob: Job? = null
    private var listener: Player.Listener? = null

    init {
        viewModelScope.launch {
            var current: MediaController? = null
            container.playerConnection.controller.collect { controller ->
                listener?.let { old -> current?.removeListener(old) }
                current = controller
                _player.value = controller
                if (controller != null) {
                    val l = buildListener()
                    listener = l
                    controller.addListener(l)
                    syncFrom(controller)
                    startTicker()
                }
            }
        }
        viewModelScope.launch {
            container.settings.skipSilence.collect { enabled ->
                sendSkipSilence(enabled)
            }
        }
    }

    private fun buildListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentTrackId.value = mediaItem?.mediaId
            _durationMs.value = _player.value?.duration ?: 0L
            _positionMs.value = 0L
        }

        override fun onPlaybackParametersChanged(parameters: androidx.media3.common.PlaybackParameters) {
            _speed.value = parameters.speed
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffle.value = enabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }
    }

    private fun syncFrom(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _currentTrackId.value = controller.currentMediaItem?.mediaId
        _durationMs.value = controller.duration.coerceAtLeast(0)
        _positionMs.value = controller.currentPosition.coerceAtLeast(0)
        _shuffle.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        _speed.value = controller.playbackParameters.speed
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val p = _player.value
                if (p != null && p.isPlaying) {
                    _positionMs.value = p.currentPosition.coerceAtLeast(0)
                    val d = p.duration
                    if (d > 0 && _durationMs.value != d) _durationMs.value = d
                }
                delay(500)
            }
        }
    }

    // ---------------- Queue building ----------------

    fun playTracks(tracks: List<TrackEntity>, startIndex: Int = 0, shuffle: Boolean = false) {
        val controller = _player.value ?: return
        val items = tracks.map { it.toMediaItem() }
        controller.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        if (shuffle) controller.shuffleModeEnabled = true
        controller.prepare()
        controller.play()
    }

    fun addToQueue(tracks: List<TrackEntity>, playNext: Boolean) {
        val controller = _player.value ?: return
        val items = tracks.map { it.toMediaItem() }
        if (controller.mediaItemCount == 0) {
            controller.setMediaItems(items)
            controller.prepare()
            controller.play()
            return
        }
        val index = if (playNext) controller.currentMediaItemIndex + 1 else controller.mediaItemCount
        controller.addMediaItems(index, items)
    }

    private fun TrackEntity.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artUrl?.let { android.net.Uri.parse(it) })
                    .setDurationMs(durationMs)
                    .build(),
            )
            .build()

    // ---------------- Transport ----------------

    fun toggle() {
        val p = _player.value ?: return
        if (p.isPlaying) p.pause() else {
            if (p.mediaItemCount == 0) return
            p.play()
        }
    }

    fun next() = _player.value?.seekToNextMediaItem()
    fun previous() = _player.value?.seekToPreviousMediaItem()

    /** Full stop: halts playback, clears the queue and dismisses the media notification. */
    fun stopPlayback() {
        val p = _player.value ?: return
        p.stop()
        p.clearMediaItems()
        _currentTrackId.value = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
    }

    fun seekTo(positionMs: Long) {
        _player.value?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun seekToIndex(index: Int, positionMs: Long = 0) {
        _player.value?.seekTo(index, positionMs)
    }

    fun toggleShuffle() {
        val p = _player.value ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val p = _player.value ?: return
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        _player.value?.setPlaybackSpeed(speed)
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { container.settings.setSkipSilence(enabled) }
        sendSkipSilence(enabled)
    }

    private fun sendSkipSilence(enabled: Boolean) {
        val args = Bundle().apply { putBoolean(PlaybackService.EXTRA_ENABLED, enabled) }
        _player.value?.sendCustomCommand(SessionCommand(PlaybackService.CMD_SKIP_SILENCE, Bundle.EMPTY), args)
    }

    fun setSleepTimer(minutes: Int) {
        val p = _player.value ?: return
        val args = Bundle().apply { putInt(PlaybackService.EXTRA_MINUTES, minutes) }
        p.sendCustomCommand(SessionCommand(PlaybackService.CMD_SLEEP_TIMER, Bundle.EMPTY), args)
    }

    // ---------------- Library actions ----------------

    fun toggleLike(track: TrackEntity) {
        viewModelScope.launch {
            container.library.setLiked(track.id, !track.isLiked)
        }
    }

    fun download(track: TrackEntity) {
        viewModelScope.launch { container.downloads.enqueue(track.id, track.title) }
    }

    fun downloadAll(tracks: List<TrackEntity>) {
        viewModelScope.launch { container.downloads.enqueueAll(tracks.filter { it.downloadState != DownloadState.DONE }) }
    }

    fun deleteDownload(track: TrackEntity) {
        viewModelScope.launch { container.downloads.deleteLocal(track.id) }
    }

    override fun onCleared() {
        listener?.let { l -> _player.value?.removeListener(l) }
        super.onCleared()
    }
}
