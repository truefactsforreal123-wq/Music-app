package com.aura.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists", indices = [Index("updatedAt")])
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceUrl: String? = null,
    val provider: String? = null,
    val artUrl: String? = null,
    val trackCount: Int = 0,
    val updatedAt: Long = 0,
)

/** Download lifecycle for a track, driven by WorkManager. */
object DownloadState {
    const val NONE = 0
    const val QUEUED = 1
    const val PREPARING = 2
    const val DOWNLOADING = 3
    const val DONE = 4
    const val ERROR = 5
}

@Entity(tableName = "tracks", indices = [Index("isLiked"), Index("lastPlayedAt"), Index("playCount")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0,
    val artUrl: String? = null,
    val source: String = "",
    val sourceUrl: String? = null,
    val isLiked: Boolean = false,
    val playCount: Long = 0,
    val lastPlayedAt: Long? = null,
    // download bookkeeping (local only — never overwritten by server sync)
    val downloadState: Int = DownloadState.NONE,
    val downloadProgress: Int = 0,
    val localPath: String? = null,
    val sizeBytes: Long = 0,
    val downloadError: String? = null,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("playlistId", "position"), Index("trackId")],
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
)
