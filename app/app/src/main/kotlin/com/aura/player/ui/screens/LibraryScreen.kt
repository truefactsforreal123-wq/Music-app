package com.aura.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.aura.player.data.db.PlaylistEntity
import com.aura.player.di.AppContainer
import com.aura.player.ui.PlayerViewModel
import com.aura.player.ui.components.Artwork
import com.aura.player.ui.components.glass
import com.aura.player.ui.theme.AuraShapes

@Composable
fun LibraryScreen(
    container: AppContainer,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    val playlists by container.library.playlists().collectAsState(initial = emptyList())
    val recent by container.library.recent(12).collectAsState(initial = emptyList())

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Aura", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Your music, black & white.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(
                            icon = Icons.Filled.Favorite,
                            label = "Liked",
                            modifier = Modifier.weight(1f),
                        ) { navController.navigate("playlist/liked") }
                        QuickCard(
                            icon = Icons.Outlined.DownloadDone,
                            label = "Downloads",
                            modifier = Modifier.weight(1f),
                        ) { navController.navigate("downloads") }
                        QuickCard(
                            icon = Icons.Filled.Link,
                            label = "Import",
                            modifier = Modifier.weight(1f),
                        ) { navController.navigate("import") }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item(key = "recent-title") {
                    SectionTitle("Recently played")
                }
                item(key = "recent") {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recent, key = { it.id }) { track ->
                            Column(
                                Modifier
                                    .width(120.dp)
                                    .clickable { playerViewModel.playTracks(listOf(track)) },
                            ) {
                                Artwork(track.artUrl, 120.dp, RoundedCornerShape(16.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(track.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "playlists-title") { SectionTitle("Playlists") }

            if (playlists.isEmpty()) {
                item(key = "empty") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "No playlists yet — paste a link to import one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(playlist) { navController.navigate("playlist/${playlist.id}") }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { navController.navigate("import") },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Import playlist") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun QuickCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .glass(AuraShapes.medium)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

/** Compact one-line playlist row — keeps the home screen scannable. */
@Composable
private fun PlaylistRow(playlist: PlaylistEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            val art = playlist.artUrl
            if (art.isNullOrBlank()) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, AuraShapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                androidx.compose.foundation.Image(
                    painter = rememberAsyncImagePainter(art),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, AuraShapes.medium),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.trackCount} tracks${playlist.provider?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
