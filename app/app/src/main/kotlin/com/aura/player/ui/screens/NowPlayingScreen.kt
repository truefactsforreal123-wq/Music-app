package com.aura.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.aura.player.data.db.DownloadState
import com.aura.player.data.db.TrackEntity
import com.aura.player.di.AppContainer
import com.aura.player.ui.PlayerViewModel
import com.aura.player.ui.components.formatDuration
import com.aura.player.ui.components.bounceClick
import com.aura.player.ui.components.glass
import com.aura.player.ui.theme.AuraShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Immersive Now Playing: blurred art backdrop, transport, queue + synced lyrics. */
@Composable
fun NowPlayingScreen(
    container: AppContainer,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    val currentId by playerViewModel.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val position by playerViewModel.positionMs.collectAsStateWithLifecycle()
    val duration by playerViewModel.durationMs.collectAsStateWithLifecycle()
    val shuffle by playerViewModel.shuffle.collectAsStateWithLifecycle()
    val repeatMode by playerViewModel.repeatMode.collectAsStateWithLifecycle()
    val track by container.library.observeTrack(currentId ?: "").collectAsState(initial = null)

    var tab by remember { mutableIntStateOf(0) } // 0 player, 1 queue, 2 lyrics

    Box(Modifier.fillMaxSize()) {
        val backdrop = track?.artUrl
        if (backdrop != null) {
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current).data(backdrop).crossfade(400).build(),
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close player")
                }
                Spacer(Modifier.weight(1f))
                TabPill("Queue", tab == 1) { tab = 1 }
                Spacer(Modifier.width(8.dp))
                TabPill("Lyrics", tab == 2) { tab = 2 }
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // Slide in the direction of the tab you moved to.
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { dir * it / 12 }) togetherWith
                        (fadeOut(tween(130)) + slideOutHorizontally(tween(130)) { -dir * it / 12 })
                },
                label = "nowPlayingTabs",
            ) { t ->
                when (t) {
                    1 -> QueueTab(playerViewModel)
                    2 -> LyricsTab(container, playerViewModel, track?.title ?: "", track?.artist ?: "")
                    else -> PlayerTab(container, playerViewModel, navController, track, isPlaying, position, duration, shuffle, repeatMode)
                }
            }        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .glass(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun PlayerTab(
    container: AppContainer,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    track: TrackEntity?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffle: Boolean,
    repeatMode: Int,
) {
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.5f))
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .glass(AuraShapes.extraLarge)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (track?.artUrl.isNullOrBlank()) {
                Icon(Icons.Filled.Lyrics, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current).data(track?.artUrl).crossfade(220).build(),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant, AuraShapes.extraLarge),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            track?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            track?.artist ?: "Play something from your library",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))

        var dragging by remember { mutableStateOf(false) }
        var dragPos by remember { mutableStateOf(0f) }
        val denom = if (duration > 0) duration.toFloat() else 1f
        // Glides between the 500 ms ticker updates instead of stepping jerkily.
        val smoothPos by animateFloatAsState(
            targetValue = (position.toFloat() / denom).coerceIn(0f, 1f),
            animationSpec = tween(500, easing = LinearEasing),
            label = "smoothSeek",
        )
        Slider(
            value = if (dragging) dragPos else smoothPos,
            onValueChange = { dragging = true; dragPos = it },
            onValueChangeFinished = {
                playerViewModel.seekTo((dragPos * duration).toLong())
                dragging = false
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { playerViewModel.previous() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
                    .bounceClick { playerViewModel.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (scaleIn(tween(160)) + fadeIn(tween(160))) togetherWith
                            (scaleOut(tween(110)) + fadeOut(tween(110)))
                    },
                    label = "playPause",
                ) { playing ->
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            IconButton(onClick = { playerViewModel.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { playerViewModel.cycleRepeat() }) {
                Icon(
                    if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(0.6f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (track != null) {
                val heartScale = remember { Animatable(1f) }
                LaunchedEffect(track.isLiked) {
                    if (track.isLiked) {
                        heartScale.snapTo(0.55f)
                        heartScale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        )
                    }
                }
                IconButton(onClick = { playerViewModel.toggleLike(track) }) {
                    Icon(
                        if (track.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (track.isLiked) "Unlike" else "Like",
                        tint = if (track.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer {
                            scaleX = heartScale.value
                            scaleY = heartScale.value
                        },
                    )
                }
                IconButton(
                    onClick = {
                        if (track.downloadState == DownloadState.DONE) playerViewModel.deleteDownload(track)
                        else playerViewModel.download(track)
                    },
                ) {
                    Icon(
                        if (track.downloadState == DownloadState.DONE) Icons.Filled.DownloadDone else Icons.Filled.DownloadForOffline,
                        contentDescription = if (track.downloadState == DownloadState.DONE) "Delete download" else "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { showSpeedDialog = true }) {
                Icon(Icons.Filled.Speed, contentDescription = "Playback speed", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showSleepDialog = true }) {
                Icon(Icons.Filled.Bedtime, contentDescription = "Sleep timer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                playerViewModel.stopPlayback()
                navController.popBackStack()
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Stop and close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showSpeedDialog) {
        ChoiceDialog(
            "Playback speed",
            listOf("0.75×" to 0.75f, "1×" to 1f, "1.25×" to 1.25f, "1.5×" to 1.5f, "2×" to 2f),
            onDismiss = { showSpeedDialog = false },
        ) { value ->
            playerViewModel.setSpeed(value)
            showSpeedDialog = false
        }
    }
    if (showSleepDialog) {
        ChoiceDialog(
            "Sleep timer",
            listOf("Off" to 0f, "15 min" to 15f, "30 min" to 30f, "45 min" to 45f, "60 min" to 60f),
            onDismiss = { showSleepDialog = false },
        ) { value ->
            playerViewModel.setSleepTimer(value.toInt())
            showSleepDialog = false
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, Float>>,
    onDismiss: () -> Unit,
    onPick: (Float) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background, // opaque — glass panel would show content through
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun QueueTab(playerViewModel: PlayerViewModel) {
    val controller by playerViewModel.player.collectAsStateWithLifecycle()
    val currentId by playerViewModel.currentTrackId.collectAsStateWithLifecycle()

    // Snapshot the queue whenever the timeline changes.
    val queue = remember(controller?.mediaItemCount, currentId) {
        controller?.let { c ->
            (0 until c.mediaItemCount).mapNotNull { i ->
                val item = c.getMediaItemAt(i)
                item.mediaMetadata.title?.toString()?.let { title -> Triple(i, item.mediaId, title) }
            }
        } ?: emptyList()
    }

    if (queue.isEmpty()) {
        Text(
            "Queue is empty",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 40.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        itemsIndexed(queue, key = { _, q -> "${q.second}-${q.first}" }) { _, (index, id, title) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { playerViewModel.seekToIndex(index, 0) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Karaoke-style synced lyrics via LRCLIB (falls back to plain text). */
@Composable
private fun LyricsTab(container: AppContainer, playerViewModel: PlayerViewModel, title: String, artist: String) {
    val position by playerViewModel.positionMs.collectAsStateWithLifecycle()
    val duration by playerViewModel.durationMs.collectAsStateWithLifecycle()

    var lyrics by remember(title, artist) { mutableStateOf<Pair<String?, String?>?>(null) }
    LaunchedEffect(title, artist, duration) {
        lyrics = withContext(Dispatchers.IO) {
            runCatching {
                val res = container.api.lyrics(title = title, artist = artist, durationMs = duration)
                res.syncedLyrics to res.plainLyrics
            }.getOrNull()
        }
    }

    val synced = lyrics?.first
    val plain = lyrics?.second

    if (synced != null) {
        val lines = remember(synced) { parseLrc(synced) }
        val listState = rememberLazyListState()
        val currentIndex by remember(lines) {
            derivedStateOf {
                var idx = -1
                for (i in lines.indices) if (lines[i].first <= position) idx = i else break
                idx
            }
        }
        LaunchedEffect(currentIndex) {
            if (currentIndex >= 2 && currentIndex < lines.size) {
                listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 40.dp)) {
            itemsIndexed(lines, key = { i, _ -> i }) { i, (timeMs, text) ->
                val isCurrent = i == currentIndex
                Text(
                    text,
                    style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playerViewModel.seekTo(timeMs) }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                )
            }
        }
    } else if (plain != null) {
        Text(
            plain,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
        )
    } else {
        Text(
            "No lyrics found for this track",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            textAlign = TextAlign.Center,
        )
    }
}

private fun parseLrc(lrc: String): List<Pair<Long, String>> {
    val out = mutableListOf<Pair<Long, String>>()
    for (raw in lrc.lineSequence()) {
        val match = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]\s*(.*)""").find(raw) ?: continue
        val (min, sec, frac, text) = match.destructured
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> (frac.toLongOrNull() ?: 0L) * 100
            2 -> (frac.toLongOrNull() ?: 0L) * 10
            else -> (frac.take(3).toLongOrNull() ?: 0L)
        }
        val timeMs = (min.toLongOrNull() ?: 0) * 60_000 + (sec.toLongOrNull() ?: 0) * 1000 + fracMs
        if (text.isNotBlank()) out.add(timeMs to text)
    }
    return out.sortedBy { it.first }
}
