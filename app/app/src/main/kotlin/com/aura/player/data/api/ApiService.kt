package com.aura.player.data.api

import com.aura.player.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("api/health")
    suspend fun health(): HealthResponse

    @POST("api/fetch")
    suspend fun fetch(@Body body: FetchRequest): FetchResponse

    @GET("api/playlists")
    suspend fun playlists(): PlaylistsResponse

    @GET("api/playlists/{id}")
    suspend fun playlist(@Path("id") id: String): PlaylistResponse

    @POST("api/playlists")
    suspend fun createPlaylist(@Body body: CreatePlaylistRequest): PlaylistResponse

    @POST("api/playlists/{id}/tracks")
    suspend fun addTracks(@Path("id") id: String, @Body body: AddTracksBody): PlaylistResponse

    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: String): OkResponse

    @DELETE("api/playlists/{id}/tracks/{trackId}")
    suspend fun removePlaylistTrack(@Path("id") id: String, @Path("trackId") trackId: String): OkResponse

    @GET("api/tracks/{id}/stream")
    suspend fun stream(
        @Path("id") id: String,
        @Query("title") title: String? = null,
        @Query("artist") artist: String? = null,
        @Query("durationMs") durationMs: Long? = null,
        @Query("artUrl") artUrl: String? = null,
    ): StreamResponse

    @POST("api/tracks/{id}/prepare")
    suspend fun prepare(
        @Path("id") id: String,
        @Body body: PrepareBody = PrepareBody(),
    ): PrepareResponse

    @GET("api/tracks/{id}/status")
    suspend fun downloadStatus(@Path("id") id: String): DownloadStatusResponse

    @POST("api/tracks/like")
    suspend fun like(@Body body: LikeRequest): TrackResponse

    @POST("api/history")
    suspend fun history(@Body body: HistoryRequest): TrackResponse

    @GET("api/recent")
    suspend fun recent(@Query("limit") limit: Int = 30): TracksResponse

    @GET("api/most-played")
    suspend fun mostPlayed(@Query("limit") limit: Int = 30): TracksResponse

    @GET("api/likes")
    suspend fun likes(): TracksResponse

    @GET("api/search")
    suspend fun search(@Query("q") q: String): TracksResponse

    @GET("api/lyrics")
    suspend fun lyrics(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("durationMs") durationMs: Long,
    ): LyricsResponse

    @Serializable
    data class AddTracksBody(val tracks: List<AddTrackBody> = emptyList(), val trackIds: List<String> = emptyList())

    @Serializable
    data class AddTrackBody(
        val source: String,
        val sourceId: String,
        val sourceUrl: String? = null,
        val title: String,
        val artist: String? = null,
        val durationMs: Long? = null,
        val artUrl: String? = null,
    )
}

/** Rewrites every request onto the server address configured in Settings. */
class HostSelectionInterceptor(private val settings: SettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = runBlocking { settings.serverUrlHttp() }
        val request = chain.request()
        val newUrl = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

object Api {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun create(settings: SettingsRepository): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(HostSelectionInterceptor(settings))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("http://aura.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
