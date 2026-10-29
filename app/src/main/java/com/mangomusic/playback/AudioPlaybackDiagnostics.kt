package com.mangomusic.playback

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.*
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Debug-only bounded numeric telemetry: no titles, queries, URIs, headers or audio samples. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioPlaybackDiagnostics(context: Context) {
    val enabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    @Volatile var fingerprintPcm = false
    private val events = ArrayDeque<JSONObject>()
    private var pass = 0
    private var selection = 0
    @Volatile private var track: AudioTrack? = null
    @Synchronized fun record(event: String, vararg values: Pair<String,Any>) {
        if (!enabled) return
        val row=JSONObject().put("timeMs",SystemClock.elapsedRealtime()).put("event",event).put("selection",selection).put("pass",pass)
        values.forEach { (key,value) -> row.put(key,value) }
        if (events.size >= 2000) events.removeFirst()
        events.addLast(row)
        Log.d("MangoAudio",row.toString())
    }
    @Synchronized fun selectionNumber(): Int = selection
    @Synchronized fun snapshot(): JSONArray = JSONArray(events.map { JSONObject(it.toString()) })
    @Synchronized fun clear() { events.clear();pass=0;selection=0 }
    fun sample(player: ExoPlayer) {
        if (!enabled || player.currentMediaItem == null) return
        val native=track
        val underruns=try { native?.underrunCount ?: -1 } catch (_: IllegalStateException) { -1 }
        record("sample","positionMs" to player.currentPosition,"bufferedMs" to player.totalBufferedDuration,
            "state" to player.playbackState,"playing" to player.isPlaying,"loading" to player.isLoading,
            "audioTrackUnderruns" to underruns,"speed" to player.playbackParameters.speed,"volume" to player.volume)
    }
    fun release() { track=null }
    fun renderers(context: Context): DefaultRenderersFactory = object: DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? {
            if (!enabled) return super.buildAudioSink(context,enableFloatOutput,enableAudioTrackPlaybackParams)
            // Same builder settings and provider as Media3's default; only observe the returned track.
            val sink=DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioTrackProvider { config, attributes, sessionId, trackContext ->
                    DefaultAudioSink.AudioTrackProvider.DEFAULT.getAudioTrack(config,attributes,sessionId,trackContext).also { track=it }
                }.build()
            return if (fingerprintPcm) PcmProbeAudioSink(sink,this@AudioPlaybackDiagnostics) else sink
        }
    }
    fun listener(player: ExoPlayer): AnalyticsListener = object: AnalyticsListener {
        override fun onMediaItemTransition(time: AnalyticsListener.EventTime, item: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) pass++ else { selection++;pass=1 }
            record("transition","reason" to reason,"online" to (item?.mediaId?.startsWith("youtube:")==true))
        }
        override fun onPlaybackStateChanged(time: AnalyticsListener.EventTime, state: Int) { record("state","state" to state,"positionMs" to time.currentPlaybackPositionMs,"bufferedMs" to time.totalBufferedDurationMs) }
        override fun onIsPlayingChanged(time: AnalyticsListener.EventTime, playing: Boolean) { record("playing","playing" to playing,"positionMs" to time.currentPlaybackPositionMs) }
        override fun onPlayWhenReadyChanged(time: AnalyticsListener.EventTime, ready: Boolean, reason: Int) { record("playIntent","ready" to ready,"reason" to reason) }
        override fun onAudioSessionIdChanged(time: AnalyticsListener.EventTime, sessionId: Int) { record("audioSession","sessionId" to sessionId) }
        override fun onAudioTrackReleased(time: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) { record("audioTrackReleased","rate" to config.sampleRate,"bufferBytes" to config.bufferSize) }
        override fun onIsLoadingChanged(time: AnalyticsListener.EventTime, loading: Boolean) { record("loading","loading" to loading) }
        override fun onAudioDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestamp: Long, duration: Long) { record("decoder","name" to name,"initializationMs" to duration) }
        override fun onAudioInputFormatChanged(time: AnalyticsListener.EventTime, format: Format, reuse: DecoderReuseEvaluation?) {
            record("format","mime" to (format.sampleMimeType ?: "unknown"),"codec" to (format.codecs ?: "unknown"),"rate" to format.sampleRate,"channels" to format.channelCount,"bitrate" to format.bitrate,"pcmEncoding" to format.pcmEncoding,"reuse" to (reuse?.result ?: -1))
        }
        override fun onAudioTrackInitialized(time: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) { record("audioTrack","rate" to config.sampleRate,"encoding" to config.encoding,"channelMask" to config.channelConfig,"bufferBytes" to config.bufferSize,"offload" to config.offload) }
        override fun onAudioUnderrun(time: AnalyticsListener.EventTime, bytes: Int, duration: Long, elapsed: Long) { record("underrun","bufferBytes" to bytes,"bufferDurationMs" to duration,"elapsedSinceFeedMs" to elapsed,"positionMs" to time.currentPlaybackPositionMs,"bufferedMs" to time.totalBufferedDurationMs) }
        override fun onAudioPositionAdvancing(time: AnalyticsListener.EventTime, startTime: Long) { record("outputAdvancing","positionMs" to time.currentPlaybackPositionMs) }
        override fun onAudioDisabled(time: AnalyticsListener.EventTime, counters: DecoderCounters) { counters.ensureUpdated();record("decoderDisabled","renderedBuffers" to counters.renderedOutputBufferCount,"skippedBuffers" to counters.skippedOutputBufferCount,"droppedBuffers" to counters.droppedBufferCount) }
        override fun onAudioSinkError(time: AnalyticsListener.EventTime, error: Exception) { record("sinkError","class" to error.javaClass.simpleName) }
        override fun onAudioCodecError(time: AnalyticsListener.EventTime, error: Exception) { record("codecError","class" to error.javaClass.simpleName) }
        override fun onPlayerError(time: AnalyticsListener.EventTime, error: PlaybackException) { record("playerError","code" to error.errorCode) }
        override fun onLoadCompleted(time: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) { record("load","type" to data.dataType,"bytes" to info.bytesLoaded,"durationMs" to info.loadDurationMs,"bufferedMs" to time.totalBufferedDurationMs) }
        override fun onLoadError(time: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, error: IOException, cancelled: Boolean) { record("loadError","type" to data.dataType,"class" to error.javaClass.simpleName,"cancelled" to cancelled) }
        override fun onBandwidthEstimate(time: AnalyticsListener.EventTime, elapsed: Int, bytes: Long, estimate: Long) { record("bandwidth","bitsPerSecond" to estimate,"bytes" to bytes,"durationMs" to elapsed) }
        override fun onPositionDiscontinuity(time: AnalyticsListener.EventTime, old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) { record("discontinuity","reason" to reason,"oldMs" to old.positionMs,"newMs" to new.positionMs) }
    }
}
