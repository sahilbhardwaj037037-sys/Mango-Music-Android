package com.mangomusic

import android.content.ComponentName
import androidx.lifecycle.Lifecycle
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.mangomusic.domain.Catalog
import com.mangomusic.domain.Track
import com.mangomusic.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(message: String, timeout: Long = 12000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) {
            var ok = false; main { ok = condition() }; if (ok) return
            Thread.sleep(100)
        }
        fail(message)
    }
    private fun withPlayer(test: (ActivityScenario<MainActivity>, MediaController) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var future: ListenableFuture<MediaController>
            main { future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync() }
            val player = future.get(15, TimeUnit.SECONDS)
            try { test(scenario, player) } finally { main { player.stop(); player.clearMediaItems(); player.release() } }
        }
    }
    private fun item(track: Track) = MediaItem.Builder().setMediaId(track.id).setUri(track.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build()
    @Test fun assetPlaybackPauseSeekBackgroundAndModes() = withPlayer { scenario, player ->
        main { player.setMediaItems(Catalog.samples.map(::item)); player.prepare(); player.play() }
        await("Bundled audio did not start") { player.isPlaying && player.currentPosition > 400 }
        var pausedPosition = 0L
        main { player.pause(); pausedPosition = player.currentPosition }
        Thread.sleep(500)
        main { assertFalse(player.isPlaying); assertTrue(kotlin.math.abs(player.currentPosition - pausedPosition) < 150); player.seekTo(5000); player.play() }
        await("Seek/resume failed") { player.isPlaying && player.currentPosition >= 5000 }
        scenario.moveToState(Lifecycle.State.CREATED)
        var before = 0L; main { before = player.currentPosition }
        Thread.sleep(1200)
        main { assertTrue("Playback stopped in background", player.isPlaying); assertTrue(player.currentPosition > before + 500) }
        val notifications = context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
        assertTrue("Missing media notification", notifications.any { it.notification.category == android.app.Notification.CATEGORY_TRANSPORT })
        main {
            player.repeatMode = Player.REPEAT_MODE_ONE; player.shuffleModeEnabled = true
            assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode); assertTrue(player.shuffleModeEnabled)
            player.shuffleModeEnabled = false; player.seekTo(0, 23500)
        }
        await("Repeat One did not loop") { player.currentMediaItemIndex == 0 && player.currentPosition in 1..4000 && player.isPlaying }
        main { player.repeatMode = Player.REPEAT_MODE_ALL; player.seekTo(2, 23500) }
        await("Repeat All did not wrap") { player.currentMediaItemIndex == 0 && player.isPlaying }
        main { player.repeatMode = Player.REPEAT_MODE_OFF; player.seekToNextMediaItem() }
        await("Next did not advance") { player.currentMediaItemIndex == 1 && player.isPlaying }
        main { player.seekToPreviousMediaItem() }
        await("Previous did not return") { player.currentMediaItemIndex == 0 }
        runBlocking {
            val dao = (context.applicationContext as MangoApp).repository.dao
            assertTrue("History not recorded", (dao.get(Catalog.samples[1].id)?.playCount ?: 0) > 0)
        }
        main { player.stop() }
        main { assertFalse(player.isPlaying) }
    }
    @Test fun transientAudioFocusLossPausesAndResumes() = withPlayer { _, player ->
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener({ }, android.os.Handler(android.os.Looper.getMainLooper())).build()
        main { player.setMediaItem(item(Catalog.samples[0])); player.prepare(); player.play() }
        await("Audio did not start before focus test") { player.isPlaying }
        try {
            main { assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(request)) }
            await("Audio focus loss did not pause playback") { !player.isPlaying }
        } finally { main { audio.abandonAudioFocusRequest(request) } }
        await("Playback did not resume after transient focus loss") { player.isPlaying }
    }
    @Test fun directHttpsAudioActuallyPlays() = withPlayer { _, player ->
        main { player.setMediaItem(item(Track("https-test", "Public Media3 test audio", "Google test media", "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"))); player.prepare(); player.play() }
        await("HTTPS playback did not reach playing state", 30000) { player.isPlaying && player.currentPosition > 500 }
        main { assertTrue(player.duration > 0); assertNull(player.playerError) }
    }
    @Test fun authorizedDownloadThenLocalPlayback() = withPlayer { _, player ->
        // Example.ogg by JesseW, CC BY-SA 3.0; see docs/INTEGRATIONS.md.
        val repository = (context.applicationContext as MangoApp).repository
        val track = Track("download-integration", "Example.ogg", "JesseW · CC BY-SA 3.0", "https://upload.wikimedia.org/wikipedia/commons/c/c8/Example.ogg", downloadable = true)
        var local: String? = null
        try {
            runBlocking {
                repository.add(track); repository.download(track)
                val deadline = System.currentTimeMillis() + 45000
                while (System.currentTimeMillis() < deadline) {
                    val stored = repository.dao.get(track.id)!!
                    repository.refreshDownloads(listOf(stored))
                    val updated = repository.dao.get(track.id)!!
                    if (updated.downloadError != null) fail(updated.downloadError)
                    if (updated.localUri != null) { local = updated.localUri; break }
                    kotlinx.coroutines.delay(250)
                }
            }
            assertNotNull("Authorized download did not complete", local)
            assertTrue(local!!.startsWith("file:"))
            main { player.setMediaItem(item(track.copy(uri = local!!))); player.prepare(); player.play() }
            await("Downloaded audio did not play from local file") { player.isPlaying && player.currentPosition > 300 }
        } finally {
            main { player.stop(); player.clearMediaItems() }
            local?.let { java.io.File(android.net.Uri.parse(it).path!!).delete() }
            runBlocking { repository.dao.delete(track.id) }
        }
    }
    @Test fun unavailableSourceReportsFailure() = withPlayer { _, player ->
        main { player.setMediaItem(item(Track("missing-test", "Missing audio", "Test", "asset:///does-not-exist.wav"))); player.prepare(); player.play() }
        await("Missing source did not report a playback error") { player.playerError != null }
        main { assertFalse(player.isPlaying) }
    }
}
