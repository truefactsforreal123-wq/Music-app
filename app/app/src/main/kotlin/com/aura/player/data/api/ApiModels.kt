package com.aura.player.data.api

import kotlinx.serialization.Serializable

@Serializable
data class TrackDto(
    val id: String,
    val source: String,
    val sourceId: String,
    val sourceUrl: String? = null,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0,
    val artUrl: String? = null,
    val isLiked: Boolean = false,
    val playCount: Long = 0,
    val lastPlayedAt: Long? = null,
    val cached: Boolean = false,
)

@Serializable
data class PlaylistSummaryDto(
    val id: String,
    val name: String,
    val sourceUrl: String? = null,
    val provider: String? = null,
    val artUrl: String? = null,
    val updatedAt: Long = 0,
    val trackCount: Int = 0,
)

@Serializable
data class PlaylistFullDto(
    val id: String,
    val name: String,
    val sourceUrl: String? = null,
    val provider: String? = null,
    val artUrl: String? = null,
    val updatedAt: Long = 0,
    val trackCount: Int = 0,
    val tracks: List<TrackDto> = emptyList(),
)

@Serializable
data class PlaylistsResponse(val playlists: List<PlaylistSummaryDto> = emptyList())

@Serializable
data class PlaylistResponse(val playlist: PlaylistFullDto)

@Serializable
data class TracksResponse(val tracks: List<TrackDto> = emptyList())

@Serializable
data class TrackResponse(val track: TrackDto)

@Serializable
data class FetchRequest(val url: String)

@Serializable
data class FetchResponse(val playlist: PlaylistSummaryDto, val added: Int, val total: Int)

@Serializable
data class StreamResponse(val kind: String, val url: String, val expiresIn: Long? = null)

@Serializable
data class PrepareResponse(val ready: Boolean, val queued: Boolean)

/**
 * Track metadata sent on first contact with the server. The phone's Room DB is
 * the source of truth: if the server lives on an ephemeral host and lost its
 * DB, this body re-creates the row so prepare/stream/download keep working.
 */
@Serializable
data class PrepareBody(
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val artUrl: String? = null,
    val sourceUrl: String? = null,
)

@Serializable
data class DownloadStatusResponse(
    val ready: Boolean,
    val queued: Boolean,
    val sizeBytes: Long? = null,
    val error: String? = null,
)

@Serializable
data class LyricsResponse(val syncedLyrics: String? = null, val plainLyrics: String? = null)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val version: String = "",
    val ytDlp: YtDlpHealth = YtDlpHealth(),
    val spotify: Boolean = false,
    val lanIps: List<String> = emptyList(),
) {
    @Serializable
    data class YtDlpHealth(val available: Boolean = false, val version: String? = null)
}

@Serializable
data class CreatePlaylistRequest(val name: String)

@Serializable
data class LikeRequest(val trackId: String, val liked: Boolean)

@Serializable
data class HistoryRequest(val trackId: String)

@Serializable
data class ReorderRequest(val trackIds: List<String>)

@Serializable
data class OkResponse(val ok: Boolean = true)
