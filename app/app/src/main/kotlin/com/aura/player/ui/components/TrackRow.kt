package com.aura.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.aura.player.data.db.DownloadState
import com.aura.player.data.db.TrackEntity
import java.util.Locale

@Composable
fun TrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { if (selectionMode) onToggleSelect() else onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
        }
        Artwork(track.artUrl, 48.dp, RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOf(track.artist, formatDuration(track.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Per-track download progress bar — real percent during transfer.
            when (track.downloadState) {
                DownloadState.DOWNLOADING, DownloadState.PREPARING, DownloadState.QUEUED -> {
                    val indeterminate = track.downloadState != DownloadState.DOWNLOADING || track.downloadProgress <= 0
                    LinearProgressIndicator(
                        progress = { if (indeterminate) 0f else track.downloadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                DownloadState.ERROR -> Text(
                    track.downloadError ?: "Download failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                else -> Unit
            }
        }
        DownloadIndicator(track, onDownload, onDeleteDownload)
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = { menuOpen = false; onPlayNext() })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menuOpen = false; onAddToQueue() })
            DropdownMenuItem(
                text = { Text(if (track.isLiked) "Remove from liked" else "Add to liked") },
                onClick = { menuOpen = false; onToggleLike() },
            )
            if (track.downloadState == DownloadState.DONE) {
                DropdownMenuItem(text = { Text("Delete download") }, onClick = { menuOpen = false; onDeleteDownload() })
            } else if (track.downloadState == DownloadState.NONE || track.downloadState == DownloadState.ERROR) {
                DropdownMenuItem(text = { Text("Download") }, onClick = { menuOpen = false; onDownload() })
            }
            if (onRemove != null) {
                DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menuOpen = false; onRemove() })
            }
        }
    }
}

// Always-visible per-track download control so every row offers it up front.
@Composable
private fun DownloadIndicator(track: TrackEntity, onDownload: () -> Unit, onDeleteDownload: () -> Unit) {
    when (track.downloadState) {
        DownloadState.NONE -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Filled.DownloadForOffline,
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DownloadState.DONE -> IconButton(onClick = onDeleteDownload) {
            Icon(
                Icons.Filled.DownloadDone,
                contentDescription = "Downloaded — tap to delete",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DownloadState.ERROR -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = "Download failed — tap to retry",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        else -> Unit // QUEUED / PREPARING / DOWNLOADING — the progress bar already shows
    }
}

@Composable
fun Artwork(url: String?, size: androidx.compose.ui.unit.Dp, shape: RoundedCornerShape) {
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        } else {
            Image(
                painter = rememberAsyncImagePainter(url),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}
