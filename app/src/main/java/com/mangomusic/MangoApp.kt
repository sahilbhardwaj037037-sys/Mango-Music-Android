package com.mangomusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mangomusic.data.LibraryRepository
import com.mangomusic.runtime.YtDlpRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MangoApp: Application(), ImageLoaderFactory {
    val downloads by lazy { com.mangomusic.downloads.AudioDownloads(this) }
    val repository by lazy { LibraryRepository(this) }
    val audioDiagnostics by lazy { com.mangomusic.playback.AudioPlaybackDiagnostics(this) }
    val mix by lazy { com.mangomusic.playback.MixSettings(this) }
    val online by lazy { com.mangomusic.online.OnlineRepository(this) }
    val updater by lazy { com.mangomusic.updater.UpdateManager(this) }

    override fun onCreate() {
        super.onCreate()
        // Asynchronously warm up the Python runtime on a background thread so the user
        // experiences no cold-start freeze when first searching or resolving online audio.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                YtDlpRuntime.warmup(this@MangoApp)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
