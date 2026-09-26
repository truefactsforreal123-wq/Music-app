package com.aura.player.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Long-lived connection to the playback session. Created once per process. */
class PlayerConnection(context: Context) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    private val future = MediaController.Builder(
        context.applicationContext,
        SessionToken(context.applicationContext, ComponentName(context.applicationContext, PlaybackService::class.java)),
    ).buildAsync()

    init {
        future.addListener({ _controller.value = runCatching { future.get() }.getOrNull() }, MoreExecutors.directExecutor())
    }

    fun release() {
        MediaController.releaseFuture(future)
        _controller.value = null
    }
}
