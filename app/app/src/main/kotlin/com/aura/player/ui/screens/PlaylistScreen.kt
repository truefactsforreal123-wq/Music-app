package com.aura.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.player.data.db.DownloadState
import com.aura.player.data.db.TrackEntity
import com.aura.player.di.AppContainer
import com.aura.player.ui.PlayerViewModel
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.EmptyState
import com.aura.player.ui.components.TrackRow
import com.aura.player.ui.components.glass
import com.aura.player.ui.theme.AuraShapes
import kotlinx.coroutines.launch

/**
 * Playlist detail. Supports the multi-select download flow: long-press a row
 * to enter selection mode, tick checkboxes, then "Download (n)" — or grab
 * everything with "Download all".
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    container: AppContainer,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    playlistId: String,
) {
    val isLikedView = playlistId == "liked"
    val playlist by if (isLikedView) {
        remember { mutableStateOf<com.aura.player.data.db.PlaylistEntity?>(null) }
    } else {
        container.library.playlist(playlistId).collectAsState(initial = null)
    }
    val tracks by if (isLikedView) {
        container.library.likedTracks().collectAsState(initial = emptyList())
    } else {
        container.library.playlistTracks(playlistId).collectAsState(initial = emptyList())
    }

    val currentTrackId by playerViewModel.currentTrackId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val selectionMode = remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(playlistId) {
        if (!isLikedView) runCatching { container.library.syncPlaylist(playlistId) }
    }

    Column(Modifier.fillMaxSize()) {
        if (selectionMode.value) {
            SelectionTopBar(
                count = selected.size,
                onClose = { selectionMode.value = false; selected.clear() },
                onSelectAll = {
                    selected.clear()
                    selected.addAll(tracks.map { it.id })
                },
                onDownloadSelected = {
                    val chosen = tracks.filter { it.id in selected }
                    playerViewModel.downloadAll(chosen)
                    selectionMode.value = false
                    selected.clear()
                },
            )
        } else {
            PlaylistHeader(
                title = if (isLikedView) "Liked songs" else (playlist?.name ?: "Playlist"),
                subtitle = buildString {
                    append("${tracks.size} tracks")
                    if (!isLikedView) playlist?.provider?.let { append(" · $it") }
                },
                artUrl = if (isLikedView) tracks.firstOrNull()?.artUrl else (playlist?.artUrl ?: tracks.firstOrNull()?.artUrl),
                onBack = { navController.popBackStack() },
                tracks = tracks,
                playerViewModel = playerViewModel,
            )
        }

        if (tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.PlayCircle,
                title = "Nothing here yet",
                subtitle = if (isLikedView) "Like songs from any playlist and they collect here."
                else "Import a playlist link or add songs from search.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                    val inSelection = track.id in selected
                    Box(
                        Modifier.combinedClickable(
                            enabled = true,
                            onClick = {
                                if (selectionMode.value) {
                                    if (inSelection) selected.remove(track.id) else selected.add(track.id)
                                } else {
                                    playerViewModel.playTracks(tracks, index)
                                }
                            },
                            onLongClick = {
                                selectionMode.value = true
                                if (!inSelection) selected.add(track.id)
                            },
                        ),
                    ) {
                        TrackRow(
                            track = track,
                            isCurrent = track.id == currentTrackId,
                            selectionMode = selectionMode.value,
                            selected = inSelection,
                            onToggleSelect = {
                                if (inSelection) selected.remove(track.id) else selected.add(track.id)
                            },
                            onClick = { playerViewModel.playTracks(tracks, index) },
                            onPlayNext = { playerViewModel.addToQueue(listOf(track), playNext = true) },
                            onAddToQueue = { playerViewModel.addToQueue(listOf(track), playNext = false) },
                            onToggleLike = { playerViewModel.toggleLike(track) },
                            onDownload = { playerViewModel.download(track) },
                            onDeleteDownload = { playerViewModel.deleteDownload(track) },
                            onRemove = if (isLikedView) null else {
                                {
                                    scope.launch { container.library.removeFromPlaylist(playlistId, track.id) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDownloadSelected: () -> Unit) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Exit selection") }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = "Select all") }
            IconButton(onClick = onDownloadSelected, enabled = count > 0) {
                Icon(Icons.Filled.DownloadForOffline, contentDescription = "Download selected")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun PlaylistHeader(
    title: String,
    subtitle: String,
    artUrl: String?,
    onBack: () -> Unit,
    tracks: List<TrackEntity>,
    playerViewModel: PlayerViewModel,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(artUrl, 108.dp, RoundedCornerShape(20.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { playerViewModel.playTracks(tracks) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Play")
            }
            OutlinedButton(
                onClick = { playerViewModel.playTracks(tracks, shuffle = true) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Shuffle")
            }
            OutlinedButton(
                onClick = { playerViewModel.downloadAll(tracks) },
                enabled = tracks.isNotEmpty(),
            ) {
                Icon(Icons.Filled.DownloadForOffline, contentDescription = null)
                Text("All")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
