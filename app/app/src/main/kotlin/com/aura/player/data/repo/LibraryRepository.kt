package com.aura.player.data.repo

import androidx.room.withTransaction
import com.aura.player.data.api.ApiService
import com.aura.player.data.api.CreatePlaylistRequest
import com.aura.player.data.api.FetchRequest
import com.aura.player.data.api.HistoryRequest
import com.aura.player.data.api.LikeRequest
import com.aura.player.data.api.TrackDto
import com.aura.player.data.db.AuraDatabase
import com.aura.player.data.db.PlaylistEntity
import com.aura.player.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single source of truth on-device: the Room mirror of the server library.
 * Server sync merges display fields but never clobbers local-only state
 * (download progress, local file paths, play counts).
 */
class LibraryRepository(
    private val db: AuraDatabase,
    private val api: ApiService,
) {
    private val playlistDao = db.playlistDao()
    private val trackDao = db.trackDao()
    private val joinDao = db.playlistTrackDao()

    // ---------- Observation (all reactive, off the main thread) ----------

    fun playlists(): Flow<List<PlaylistEntity>> = playlistDao.observeAll().flowOn(Dispatchers.IO)

    fun playlist(id: String): Flow<PlaylistEntity?> = playlistDao.observe(id)

    fun playlistTracks(id: String): Flow<List<TrackEntity>> = joinDao.observeTracks(id).flowOn(Dispatchers.IO)

    fun likedTracks(): Flow<List<TrackEntity>> = trackDao.observeLiked().flowOn(Dispatchers.IO)

    fun recent(limit: Int = 30): Flow<List<TrackEntity>> = trackDao.observeRecent(limit).flowOn(Dispatchers.IO)

    fun mostPlayed(limit: Int = 30): Flow<List<TrackEntity>> = trackDao.observeMostPlayed(limit).flowOn(Dispatchers.IO)

    fun playlistNames(): Flow<List<PlaylistEntity>> = playlists()

    fun observeTrack(id: String): Flow<TrackEntity?> = trackDao.observe(id).flowOn(Dispatchers.IO)

    /** Every track with a local file on the device (music or audio), newest first. */
    fun downloadedTracks(): Flow<List<TrackEntity>> = trackDao.observeDownloaded().flowOn(Dispatchers.IO)

    fun downloadSizeBytes(): Flow<Long> = trackDao.observeTotalDownloadSize().flowOn(Dispatchers.IO)

    // ---------- Sync ----------

    suspend fun syncLibrary() = withContext(Dispatchers.IO) {
        val summaries = api.playlists().playlists
        db.withTransaction {
            playlistDao.upsertAll(
                summaries.map {
                    PlaylistEntity(
                        id = it.id,
                        name = it.name,
                        sourceUrl = it.sourceUrl,
                        provider = it.provider,
                        artUrl = it.artUrl,
                        trackCount = it.trackCount,
                        updatedAt = it.updatedAt,
                    )
                },
            )
            // NOTE: no deleteNotIn(). The phone is the source of truth — if the
            // server host reset its DB (ephemeral hosting), an empty response
            // must never wipe local playlists.
        }
        for (summary in summaries) {
            syncPlaylist(summary.id)
        }
    }

    suspend fun syncPlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val full = api.playlist(playlistId).playlist
        db.withTransaction {
            playlistDao.upsertAll(
                listOf(
                    PlaylistEntity(
                        id = full.id,
                        name = full.name,
                        sourceUrl = full.sourceUrl,
                        provider = full.provider,
                        artUrl = full.artUrl,
                        trackCount = full.trackCount,
                        updatedAt = full.updatedAt,
                    ),
                ),
            )
            mergeTracks(full.tracks)
            joinDao.replacePlaylist(playlistId, full.tracks.map { it.id })
        }
    }

    /** Display fields refresh from the server; local-only fields are preserved. */
    private suspend fun mergeTracks(dto: List<TrackDto>) {
        for (t in dto) {
            val existing = trackDao.get(t.id)
            if (existing == null) {
                trackDao.upsertAll(listOf(t.toEntity()))
            } else {
                trackDao.upsertAll(
                    listOf(
                        existing.copy(
                            title = t.title,
                            artist = t.artist,
                            durationMs = if (existing.durationMs > 0) existing.durationMs else t.durationMs,
                            artUrl = t.artUrl ?: existing.artUrl,
                            isLiked = t.isLiked,
                        ),
                    ),
                )
            }
        }
    }

    private fun TrackDto.toEntity() = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        artUrl = artUrl,
        source = source,
        sourceUrl = sourceUrl,
        isLiked = isLiked,
        playCount = 0,
        lastPlayedAt = lastPlayedAt,
    )

    // ---------- Mutations ----------

    suspend fun fetchPlaylist(url: String) = withContext(Dispatchers.IO) {
        val res = api.fetch(FetchRequest(url))
        syncPlaylist(res.playlist.id)
        res
    }

    suspend fun createPlaylist(name: String): String = withContext(Dispatchers.IO) {
        val playlist = api.createPlaylist(CreatePlaylistRequest(name)).playlist
        syncPlaylist(playlist.id)
        playlist.id
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        runCatching { api.deletePlaylist(id) }
        db.withTransaction {
            joinDao.deleteForPlaylist(id)
            playlistDao.delete(id)
        }
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        runCatching { api.removePlaylistTrack(playlistId, trackId) }
        joinDao.removeTrack(playlistId, trackId)
    }

    suspend fun addServerTrackToPlaylist(playlistId: String, track: TrackDto) = withContext(Dispatchers.IO) {
        api.addTracks(
            playlistId,
            ApiService.AddTracksBody(
                tracks = listOf(
                    ApiService.AddTrackBody(
                        source = track.source,
                        sourceId = track.sourceId,
                        sourceUrl = track.sourceUrl,
                        title = track.title,
                        artist = track.artist,
                        durationMs = track.durationMs,
                        artUrl = track.artUrl,
                    ),
                ),
            ),
        )
        syncPlaylist(playlistId)
    }

    suspend fun setLiked(trackId: String, liked: Boolean) = withContext(Dispatchers.IO) {
        trackDao.setLiked(trackId, liked)
        runCatching { api.like(LikeRequest(trackId, liked)) }
    }

    suspend fun recordPlay(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.incrementPlay(trackId, System.currentTimeMillis())
        runCatching { api.history(HistoryRequest(trackId)) }
    }

    suspend fun track(trackId: String): TrackEntity? = withContext(Dispatchers.IO) { trackDao.get(trackId) }

    suspend fun localPathFor(trackId: String): String? = track(trackId)?.localPath?.takeIf { it.isNotBlank() }
}
