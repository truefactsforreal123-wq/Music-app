package com.aura.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.di.AppContainer
import com.aura.player.ui.PlayerViewModel

/** Floating glass mini player with a hairline progress line. */
@Composable
fun MiniPlayer(
    container: AppContainer,
    playerViewModel: PlayerViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller by playerViewModel.player.collectAsStateWithLifecycle()
    val currentId by playerViewModel.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val position by playerViewModel.positionMs.collectAsStateWithLifecycle()
    val duration by playerViewModel.durationMs.collectAsStateWithLifecycle()
    val track by container.library.observeTrack(currentId ?: "").collectAsState(initial = null)

    AnimatedVisibility(
        visible = controller != null && currentId != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .glass(RoundedCornerShape(18.dp))
                .clickable { onOpen() },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track?.artUrl, 44.dp, RoundedCornerShape(12.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title ?: "Playing",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { playerViewModel.toggle() }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { playerViewModel.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = { playerViewModel.stopPlayback() }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val denom = if (duration > 0) duration else 1L
            LinearProgressIndicator(
                progress = { (position.toFloat() / denom).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
