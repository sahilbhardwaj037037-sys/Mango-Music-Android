package com.mangomusic.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mangomusic.MainActivity
import com.mangomusic.MangoApp
import kotlinx.coroutines.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService: MediaSessionService() {
    private var mix: MixQueueController? = null
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recordedId: String? = null
    override fun onCreate() {
        super.onCreate()
        val online = (application as MangoApp).online
        val diagnostics = (application as MangoApp).audioDiagnostics
        var retryId: String? = null
        var retries = 0
        var consecutiveSkips = 0
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 600,
                /* bufferForPlaybackAfterRebufferMs = */ 1_200
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val player = ExoPlayer.Builder(this, diagnostics.renderers(this))
            .setMediaSourceFactory(MangoMediaSourceFactory(this, online))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        if (diagnostics.enabled) {
            player.addAnalyticsListener(diagnostics.listener(player))
            scope.launch { while (isActive) { diagnostics.sample(player); delay(5000) } }
        }
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        player.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
        player.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
        player.addListener(object: Player.Listener {
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                recordedId = null
                retryId = item?.mediaId
                retries = 0
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) consecutiveSkips = 0
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val id = player.currentMediaItem?.mediaId ?: return
                if (!id.startsWith("youtube:")) return
                online.invalidate(id)
                val retryable = error.errorCode in listOf(
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                )
                if (retryable && retries < 1 && !online.offline.value) {
                    retries++
                    scope.launch {
                        delay(600)
                        if (retryId == id && player.currentMediaItem?.mediaId == id && !online.offline.value) player.prepare()
                    }
                } else {
                    if (player.hasNextMediaItem() && consecutiveSkips < 5) {
                        consecutiveSkips++
                        scope.launch {
                            delay(400)
                            if (player.playbackState == Player.STATE_IDLE && player.hasNextMediaItem()) {
                                player.seekToNextMediaItem()
                                player.prepare()
                                player.play()
                            }
                        }
                    }
                }
            }
            override fun onEvents(player: Player, events: Player.Events) {
                val id = player.currentMediaItem?.mediaId ?: return
                if (player.isPlaying) {
                    consecutiveSkips = 0
                    if (recordedId != id) {
                        recordedId = id
                        scope.launch { (application as MangoApp).repository.dao.played(id, System.currentTimeMillis()) }
                    }
                }
                if (player.isPlaying && player.nextMediaItemIndex >= 0) online.prefetch(player.getMediaItemAt(player.nextMediaItemIndex).mediaId)
            }
            override fun onRepeatModeChanged(repeatMode: Int) { prefs.edit().putInt("repeat", repeatMode).apply() }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { prefs.edit().putBoolean("shuffle", shuffleModeEnabled).apply() }
        })
        mix = MixQueueController(player,(application as MangoApp).repository,online,online.offline,(application as MangoApp).mix,scope)
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(activity).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Only this app and trusted system notification controllers can mutate the session.
        return if (controllerInfo.packageName == packageName || controllerInfo.isTrusted) session else null
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.playWhenReady != true) stopSelf()
    }
    override fun onDestroy() { mix?.release();mix=null; (application as MangoApp).audioDiagnostics.release(); session?.run { player.release(); release() }; session = null; scope.cancel(); super.onDestroy() }
}
