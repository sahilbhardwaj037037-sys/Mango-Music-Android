package com.mangomusic.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.*
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.mangomusic.online.*
import kotlinx.coroutines.*
import java.io.IOException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MangoMediaSourceFactory(private val context: Context, private val online: OnlineRepository): MediaSource.Factory {
    private val local = DefaultMediaSourceFactory(context)
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory { local.setDrmSessionManagerProvider(provider); return this }
    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory { local.setLoadErrorHandlingPolicy(policy); return this }
    override fun getSupportedTypes(): IntArray = local.supportedTypes
    override fun createMediaSource(item: MediaItem): MediaSource =
        if (item.mediaId.startsWith("youtube:") && item.localConfiguration?.uri?.scheme !in listOf("file","content","asset")) OnlineMediaSource(context,online,item) else local.createMediaSource(item)
}

/** Resolves on the service/player's behalf, including automatic queue transitions in background. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class OnlineMediaSource(private val context: Context, private val online: OnlineRepository, private val original: MediaItem): CompositeMediaSource<Int>() {
    private var child: MediaSource? = null
    private var failure: IOException? = null
    private var job: Job? = null
    private var generation = 0
    override fun getMediaItem(): MediaItem = original
    override fun prepareSourceInternal(listener: TransferListener?) {
        super.prepareSourceInternal(listener)
        val handler=Handler(Looper.myLooper()!!)
        val token=++generation
        failure=null
        job=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val started=android.os.SystemClock.elapsedRealtime()
                val app=context.applicationContext as com.mangomusic.MangoApp
                val saved=app.repository.dao.get(original.mediaId)
                val offlineCopy=app.downloads.local(saved)
                if(offlineCopy!=null) {
                    ensureActive()
                    handler.post { if(generation==token) {
                        val source=DefaultMediaSourceFactory(context).createMediaSource(original.buildUpon().setUri(offlineCopy).build())
                        child=source;prepareChildSource(0,source)
                    } }
                    return@launch
                }
                val audio=online.resolve(original.mediaId)
                (context.applicationContext as com.mangomusic.MangoApp).audioDiagnostics.record("sourceResolved","durationMs" to (android.os.SystemClock.elapsedRealtime()-started),"transport" to if (audio.mimeType=="application/x-mpegURL") "hls" else "progressive")
                ensureActive()
                handler.post {
                    if (generation==token) {
                        val http=DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
                            .setDefaultRequestProperties(audio.headers)
                        val guarded=DataSource.Factory { OfflineGuardDataSource(http.createDataSource(),online) }
                        val source=DefaultMediaSourceFactory(DefaultDataSource.Factory(context,guarded))
                            .createMediaSource(original.buildUpon().setUri(audio.uri).setMimeType(audio.mimeType).build())
                        child=source
                        prepareChildSource(0,source)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) return@launch
                handler.post { if (generation==token) failure = when(e) { is OnlineFailure -> e; is TimeoutCancellationException -> OnlineFailure("timeout"); else -> OnlineFailure("unavailable") } }
            }
        }
    }
    override fun onChildSourceInfoRefreshed(id: Int, source: MediaSource, timeline: Timeline) { refreshSourceInfo(timeline) }
    override fun maybeThrowSourceInfoRefreshError() { failure?.let { throw it }; super.maybeThrowSourceInfoRefreshError() }
    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod = requireNotNull(child).createPeriod(id,allocator,startPositionUs)
    override fun releasePeriod(period: MediaPeriod) { child?.releasePeriod(period) }
    override fun releaseSourceInternal() { generation++;job?.cancel();job=null;super.releaseSourceInternal();child=null;failure=null }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class OfflineGuardDataSource(private val source: DataSource, private val online: OnlineRepository): DataSource by source {
    override fun open(dataSpec: DataSpec): Long {
        if (online.offline.value) throw OnlineFailure("offline")
        return source.open(dataSpec)
    }
}
