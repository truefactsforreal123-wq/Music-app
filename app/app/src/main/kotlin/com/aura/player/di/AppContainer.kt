package com.aura.player.di

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import com.aura.player.data.api.ApiService
import com.aura.player.data.api.Api
import com.aura.player.data.db.AuraDatabase
import com.aura.player.data.prefs.SettingsRepository
import com.aura.player.data.repo.LibraryRepository
import com.aura.player.download.DownloadRepository
import com.aura.player.player.PlayerConnection
import java.io.File

/** Hand-rolled DI: one container, lazy singletons, zero reflection overhead. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val api: ApiService by lazy { Api.create(settings) }
    val db: AuraDatabase by lazy { AuraDatabase.get(appContext) }
    val library: LibraryRepository by lazy { LibraryRepository(db, api) }
    val downloads: DownloadRepository by lazy { DownloadRepository(appContext, api, settings, db) }

    /** 512 MB LRU cache for streamed audio — replays never re-hit the network. */
    val streamCache: SimpleCache by lazy {
        SimpleCache(
            File(appContext.cacheDir, "stream_cache"),
            LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
            StandaloneDatabaseProvider(appContext),
        )
    }

    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }

    val okHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .build()
    }
}
