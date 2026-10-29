package com.mangomusic

import android.content.ComponentName
import androidx.lifecycle.Lifecycle
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.mangomusic.online.OnlineFailure
import com.mangomusic.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OnlinePlaybackTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val app get()=context.applicationContext as MangoApp
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun await(message:String, timeout:Long=60000, predicate:()->Boolean) {
        val end=System.currentTimeMillis()+timeout
        while(System.currentTimeMillis()<end) {
            var ok=false;main { ok=predicate() };if(ok)return
            Thread.sleep(150)
        }
        fail(message)
    }
    @Test fun runtimeAndRealSearch()=runBlocking {
        app.online.setOffline(false)
        val diagnostics=app.online.diagnostics()
        assertTrue(diagnostics.getString("python").startsWith("3.14"))
        assertEquals("2026.10.26",diagnostics.getString("ytDlp"))
        assertEquals(0,diagnostics.getInt("quickJsExit"))
        val tracks=app.online.search("Kevin MacLeod Carefree")
        assertTrue("Real online search returned no results",tracks.isNotEmpty())
        assertTrue(tracks.all { it.id.startsWith("youtube:") && it.uri.startsWith("https://www.youtube.com/watch?v=") })
        File(context.filesDir,"online-search-evidence.json").writeText(JSONObject().put("runtime",diagnostics)
            .put("results",JSONArray(tracks.map { JSONObject().put("id",it.id).put("title",it.title).put("artist",it.artist).put("thumbnail",it.thumbnailUrl) })).toString(2))
    }
    @Test fun offlineBlocksSearchAndResolutionWithoutRequests()=runBlocking {
        app.online.setOffline(true)
        val before=app.online.requestsStarted.get()
        try {
            try { app.online.search("test");fail("Offline search was allowed") } catch(e:OnlineFailure) { assertEquals("offline",e.kind) }
            try { app.online.resolve("youtube:aqz-KE-bpKQ");fail("Offline resolution was allowed") } catch(e:OnlineFailure) { assertEquals("offline",e.kind) }
            assertEquals(before,app.online.requestsStarted.get())
        } finally { app.online.setOffline(false) }
    }
    @Test fun onlineMusicQueueTransitionsAndCacheWork() {
        app.online.setOffline(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val music=runBlocking { app.online.search("Kevin MacLeod Carefree").first { it.title.contains("Carefree",ignoreCase=true) } }
            val second=com.mangomusic.domain.Track("youtube:aqz-KE-bpKQ","Big Buck Bunny","Blender Foundation","https://www.youtube.com/watch?v=aqz-KE-bpKQ")
            runBlocking { app.repository.add(music);app.repository.add(second) }
            lateinit var future:ListenableFuture<MediaController>
            main { future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync() }
            val player=future.get(20,TimeUnit.SECONDS)
            fun item(t:com.mangomusic.domain.Track)=MediaItem.Builder().setMediaId(t.id).setUri(t.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build()).build()
            try {
                main { player.repeatMode=Player.REPEAT_MODE_OFF;player.shuffleModeEnabled=false;player.setMediaItems(listOf(item(music),item(second)));player.prepare();player.play() }
                await("Actual online music did not play") { player.isPlaying && player.currentPosition>1000 }
                scenario.moveToState(Lifecycle.State.CREATED)
                main { player.seekToNextMediaItem() }
                await("Online next failed in background") { player.currentMediaItem?.mediaId==second.id && player.isPlaying && player.currentPosition>400 }
                val before=app.online.requestsStarted.get()
                main { player.seekToPreviousMediaItem() }
                await("Online previous failed") { player.currentMediaItem?.mediaId==music.id && player.isPlaying }
                assertEquals("Previous unnecessarily re-extracted cached audio",before,app.online.requestsStarted.get())
                main { player.repeatMode=Player.REPEAT_MODE_OFF;player.seekTo((player.duration-800).coerceAtLeast(0)) }
                await("Online queue did not advance automatically at end") { player.currentMediaItem?.mediaId==second.id && player.isPlaying }
                main { player.repeatMode=Player.REPEAT_MODE_ONE;player.shuffleModeEnabled=true;assertEquals(1,player.repeatMode);assertTrue(player.shuffleModeEnabled);player.shuffleModeEnabled=false }
                runBlocking { assertTrue(app.repository.dao.get(music.id)!!.playCount>0);assertEquals(music.uri,app.repository.dao.get(music.id)!!.uri) }
                File(context.filesDir,"online-queue-evidence.json").writeText(JSONObject().put("musicVideoId",music.id).put("musicTitle",music.title).put("actualMusicPlayback",true).put("nextPreviousInBackground",true).put("automaticTransition",true).put("cacheReuse",true).toString(2))
            } finally { main { player.repeatMode=Player.REPEAT_MODE_OFF;player.shuffleModeEnabled=false;player.stop();player.clearMediaItems();player.release() } }
        }
    }
    @Test fun actualYouTubeAudioPlaysThroughNativeService() {
        app.online.setOffline(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var future:ListenableFuture<MediaController>
            main { future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync() }
            val player=future.get(20,TimeUnit.SECONDS)
            try {
                // Blender Foundation's publicly released Creative Commons movie, used as permitted test media.
                val track=com.mangomusic.domain.Track("youtube:aqz-KE-bpKQ","Big Buck Bunny","Blender Foundation","https://www.youtube.com/watch?v=aqz-KE-bpKQ")
                runBlocking { app.repository.add(track) }
                main { player.setMediaItem(MediaItem.Builder().setMediaId(track.id).setUri(track.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build());player.prepare();player.play() }
                await("Real YouTube audio did not decode/play through Media3") { player.isPlaying && player.currentPosition>1200 }
                var paused=0L
                main { assertNull(player.playerError); assertTrue(player.duration>0);player.pause();paused=player.currentPosition }
                Thread.sleep(600)
                main { assertTrue(kotlin.math.abs(player.currentPosition-paused)<180);player.seekTo(10000);player.play() }
                await("Online seek/resume failed") { player.isPlaying && player.currentPosition>10000 }
                scenario.moveToState(Lifecycle.State.CREATED)
                var position=0L;main { position=player.currentPosition }
                Thread.sleep(1200)
                main { assertTrue(player.isPlaying);assertTrue(player.currentPosition>position+500) }
                assertTrue(context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.isNotEmpty())
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_MEDIA_PAUSE").close()
                await("System media pause did not reach the background session") { !player.isPlaying && !player.playWhenReady }
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_MEDIA_PLAY").close()
                await("System media play did not reach the background session") { player.isPlaying }
                runBlocking {
                    if (!app.repository.dao.get(track.id)!!.favorite) app.repository.dao.toggleFavorite(track.id)
                    val saved=app.repository.dao.get(track.id)!!
                    assertTrue(saved.favorite);assertTrue(saved.playCount>0)
                    assertEquals(track.uri,saved.uri)
                }
                File(context.filesDir,"online-playback-evidence.json").writeText(JSONObject().put("videoId","aqz-KE-bpKQ").put("nativePlayback",true).put("seekPauseResume",true).put("background",true).put("systemMediaKeys",true).put("favoriteAndHistory",true).toString(2))
            } finally { main { player.stop();player.clearMediaItems();player.release() } }
        }
    }
}
