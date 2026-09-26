package com.aura.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aura.player.data.api.ApiService
import com.aura.player.data.api.TrackDto
import com.aura.player.data.db.TrackEntity
import com.aura.player.di.AppContainer
import com.aura.player.ui.PlayerViewModel
import com.aura.player.ui.components.EmptyState
import com.aura.player.ui.components.TrackRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Searches the local library instantly and the server's catalog (yt-dlp)
 * with a 400 ms debounce. Server results can be played immediately or
 * bookmarked into a playlist.
 */
@Composable
fun SearchScreen(
    container: AppContainer,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    var query by remember { mutableStateOf("") }
    var localResults by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var remoteResults by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            localResults = emptyList()
            remoteResults = emptyList()
            remoteError = null
            return@LaunchedEffect
        }
        localResults = container.db.trackDao().search(query.trim())
        searchJob?.cancel()
        searchJob = launch {
            delay(400) // debounce: one upstream search per burst of typing
            searching = true
            remoteError = null
            try {
                remoteResults = container.api.search(query.trim()).tracks
            } catch (t: Throwable) {
                remoteError = t.message ?: "Search failed"
                remoteResults = emptyList()
            } finally {
                searching = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search songs, artists…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )

        if (query.isBlank()) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "Search",
                subtitle = "Find songs in your library or search the wider catalog.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                if (localResults.isNotEmpty()) {
                    item(key = "local-header") {
                        SectionLabel("In your library")
                    }
                    itemsIndexed(localResults, key = { _, t -> "local-${t.id}" }) { _, track ->
                        val currentId by playerViewModel.currentTrackId.collectAsState()
                        TrackRow(
                            track = track,
                            isCurrent = track.id == currentId,
                            selectionMode = false,
                            selected = false,
                            onToggleSelect = {},
                            onClick = { playerViewModel.playTracks(listOf(track)) },
                            onPlayNext = { playerViewModel.addToQueue(listOf(track), playNext = true) },
                            onAddToQueue = { playerViewModel.addToQueue(listOf(track), playNext = false) },
                            onToggleLike = { playerViewModel.toggleLike(track) },
                            onDownload = { playerViewModel.download(track) },
                            onDeleteDownload = { playerViewModel.deleteDownload(track) },
                            onRemove = null,
                        )
                    }
                }
                item(key = "remote-header") { SectionLabel("From the catalog") }
                if (searching && remoteResults.isEmpty()) {
                    item(key = "searching") {
                        Text(
                            "Searching…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                remoteError?.let { err ->
                    item(key = "remote-error") {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(remoteResults, key = { _, t -> "remote-${t.id}" }) { index, dto ->
                    val entity = dto.toEntityPlaceholder()
                    val currentId by playerViewModel.currentTrackId.collectAsState()
                    TrackRow(
                        track = entity,
                        isCurrent = dto.id == currentId,
                        selectionMode = false,
                        selected = false,
                        onToggleSelect = {},
                        onClick = {
                            scope.launch {
                                // Persist to Room first so the session can resolve metadata.
                                container.db.trackDao().upsertAll(listOf(entity))
                                playerViewModel.playTracks(listOf(entity))
                            }
                        },
                        onPlayNext = {
                            scope.launch {
                                container.db.trackDao().upsertAll(listOf(entity))
                                playerViewModel.addToQueue(listOf(entity), playNext = true)
                            }
                        },
                        onAddToQueue = {
                            scope.launch {
                                container.db.trackDao().upsertAll(listOf(entity))
                                playerViewModel.addToQueue(listOf(entity), playNext = false)
                            }
                        },
                        onToggleLike = {
                            scope.launch {
                                container.db.trackDao().upsertAll(listOf(entity))
                                playerViewModel.toggleLike(entity)
                            }
                        },
                        onDownload = {
                            scope.launch {
                                container.db.trackDao().upsertAll(listOf(entity))
                                playerViewModel.download(entity)
                            }
                        },
                        onDeleteDownload = {},
                        onRemove = null,
                    )
                }
            }
        }
    }
}

private fun TrackDto.toEntityPlaceholder() = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    artUrl = artUrl,
    source = source,
    sourceUrl = sourceUrl,
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
