package com.aura.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aura.player.di.AppContainer
import com.aura.player.ui.PlayerViewModel
import com.aura.player.ui.components.EmptyState
import com.aura.player.ui.components.TrackRow
import kotlinx.coroutines.launch
import java.util.Locale

/** One flat list of everything saved on the device — music and audio alike. */
@Composable
fun DownloadsScreen(
    container: AppContainer,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    val downloads by container.library.downloadedTracks().collectAsState(initial = emptyList())
    val totalBytes by container.library.downloadSizeBytes().collectAsState(initial = 0L)
    val currentId by playerViewModel.currentTrackId.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${downloads.size} file${if (downloads.size == 1) "" else "s"} · ${formatBytes(totalBytes)} on device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloads.isNotEmpty()) {
                IconButton(onClick = { playerViewModel.playTracks(downloads) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                }
                IconButton(onClick = {
                    playerViewModel.stopPlayback()
                    scope.launch { container.downloads.deleteAllLocal() }
                }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Delete all downloads")
                }
            }
        }

        if (downloads.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.PlayArrow,
                title = "Nothing downloaded yet",
                subtitle = "Tap the download icon on any track to keep it offline.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(downloads, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        selectionMode = false,
                        selected = false,
                        onToggleSelect = {},
                        onClick = { playerViewModel.playTracks(downloads, downloads.indexOf(track)) },
                        onPlayNext = { playerViewModel.addToQueue(listOf(track), playNext = true) },
                        onAddToQueue = { playerViewModel.addToQueue(listOf(track), playNext = false) },
                        onToggleLike = { playerViewModel.toggleLike(track) },
                        onDownload = { playerViewModel.download(track) },
                        onDeleteDownload = { playerViewModel.deleteDownload(track) },
                        onRemove = null,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 30 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
    bytes >= 1 shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}
