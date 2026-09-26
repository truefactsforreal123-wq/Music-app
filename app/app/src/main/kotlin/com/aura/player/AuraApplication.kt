package com.aura.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aura.player.di.AppContainer

class AuraApplication : Application(), ImageLoaderFactory {

    val container: AppContainer by lazy { AppContainer(this) }

    /** Shared Coil loader: 25% of heap in RAM, 256 MB on disk, smooth crossfades. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(150)
            .build()
}
