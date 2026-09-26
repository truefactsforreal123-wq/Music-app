package com.aura.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Upsert
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<PlaylistEntity>

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT id FROM playlists")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM playlists WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)
}

@Dao
interface TrackDao {
    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observe(id: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY rowid DESC")
    fun observeLiked(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int = 30): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' " +
            "ORDER BY playCount DESC, lastPlayedAt DESC LIMIT 50",
    )
    suspend fun search(q: String): List<TrackEntity>

    @Query(
        "UPDATE tracks SET isLiked = :liked WHERE id = :id"
    )
    suspend fun setLiked(id: String, liked: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :at WHERE id = :id")
    suspend fun incrementPlay(id: String, at: Long)

    @Query("UPDATE tracks SET durationMs = :durationMs WHERE id = :id AND durationMs = 0")
    suspend fun backfillDuration(id: String, durationMs: Long)

    // ---- download bookkeeping ----
    @Query("UPDATE tracks SET downloadState = :state, downloadProgress = :progress WHERE id = :id")
    suspend fun setDownloadProgress(id: String, state: Int, progress: Int)

    @Query("UPDATE tracks SET downloadState = 4, downloadProgress = 100, localPath = :path, sizeBytes = :size, downloadError = NULL WHERE id = :id")
    suspend fun markDownloaded(id: String, path: String, size: Long)

    @Query("UPDATE tracks SET downloadState = :state, downloadError = :error WHERE id = :id")
    suspend fun markDownloadError(id: String, state: Int, error: String?)

    @Query("UPDATE tracks SET downloadState = 0, downloadProgress = 0, localPath = NULL, sizeBytes = 0, downloadError = NULL WHERE id = :id")
    suspend fun clearDownload(id: String)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM tracks WHERE localPath IS NOT NULL")
    fun observeTotalDownloadSize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM tracks WHERE downloadState IN (1, 2, 3)")
    fun observeActiveDownloadCount(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE downloadState IN (1, 2, 3)")
    suspend fun activeDownloads(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL")
    suspend fun downloadedTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL ORDER BY rowid DESC")
    fun observeDownloaded(): Flow<List<TrackEntity>>
}

@Dao
interface PlaylistTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PlaylistTrackEntity>)

    @Transaction
    suspend fun replacePlaylist(playlistId: String, trackIds: List<String>) {
        deleteForPlaylist(playlistId)
        insertAll(trackIds.mapIndexed { i, t -> PlaylistTrackEntity(playlistId, t, i) })
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: String, trackId: String)

    @Query(
        "SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC"
    )
    fun observeTracks(playlistId: String): Flow<List<TrackEntity>>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun trackIds(playlistId: String): List<String>
}
