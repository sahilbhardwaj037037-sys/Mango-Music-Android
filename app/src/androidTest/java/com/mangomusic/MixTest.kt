package com.mangomusic

import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mangomusic.domain.*
import com.mangomusic.online.*
import com.mangomusic.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MixTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val app get()=context.applicationContext as MangoApp
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun await(message:String,timeout:Long=20000,predicate:()->Boolean) {
        val end=System.currentTimeMillis()+timeout
        while(System.currentTimeMillis()<end) { var yes=false;main { yes=predicate() };if(yes)return;Thread.sleep(100) };fail(message)
    }
    private fun track(n:Int)=Track("youtube:mixtrack00$n","Mix fixture $n","Test artist","asset:///mango_dawn.wav",60000)
    @Test fun manualPriorityShuffleCancellationRepeatAndOffline() {
        // Play original bundled media under stable synthetic IDs: deterministic queue tests, not evidence of online streaming.
        ActivityScenario.launch(MainActivity::class.java).use {
            val settings=MixSettings(context).apply { enable(false) }
            val offline=MutableStateFlow(false)
            val gate=CompletableDeferred<Unit>()
            val calls=AtomicInteger(0)
            val provider=RecommendationProvider { _,_ -> calls.incrementAndGet();gate.await();RecommendationBatch((2..5).map(::track),"Fixture") }
            lateinit var player:ExoPlayer;lateinit var mix:MixQueueController
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            main {
                player=ExoPlayer.Builder(context).build()
                mix=MixQueueController(player,app.repository,provider,offline,settings,scope)
                player.setMediaItem(MediaItem.Builder().setMediaId(track(1).id).setUri(track(1).uri).build());player.prepare();player.play()
                settings.enable(true)
            }
            try {
                await("Mix request did not start") { calls.get()==1 }
                main {
                    // This user addition occurs while recommendation work is pending.
                    player.addMediaItem(MediaItem.Builder().setMediaId("manual").setUri("asset:///after_rain.wav").build())
                    gate.complete(Unit)
                }
                await("Mix did not extend queue") { player.mediaItemCount==5 }
                main {
                    assertEquals("manual",player.getMediaItemAt(1).mediaId)
                    assertTrue((2..4).all { MixQueue.automatic(player.getMediaItemAt(it)) })
                    player.shuffleModeEnabled=true
                    player.addMediaItem(MediaItem.Builder().setMediaId("manual-later").setUri("asset:///pink_orbit.wav").build())
                }
                await("Shuffle did not prioritize pending manual songs") {
                    val next=player.nextMediaItemIndex
                    next>=0 && !MixQueue.automatic(player.getMediaItemAt(next))
                }
                var manualOrder=emptyList<String>()
                fun shuffledIds():List<String> {
                    val ids=mutableListOf<String>();var i=player.currentTimeline.getFirstWindowIndex(true)
                    while(i>=0 && ids.size<player.mediaItemCount) { ids+=player.getMediaItemAt(i).mediaId;i=player.currentTimeline.getNextWindowIndex(i,Player.REPEAT_MODE_OFF,true) }
                    return ids
                }
                main { manualOrder=shuffledIds().filter { it==track(1).id || it.startsWith("manual") };settings.enable(false) }
                await("Turning Mix off left pending recommendations") { player.mediaItemCount==3 }
                main { assertEquals("Mix off reshuffled the remaining manual queue",manualOrder,shuffledIds()) }
                val before=calls.get()
                main { player.repeatMode=Player.REPEAT_MODE_ONE;settings.enable(true) }
                Thread.sleep(800)
                assertEquals("Repeat One should suppress discovery",before,calls.get())
                main { player.repeatMode=Player.REPEAT_MODE_ALL }
                Thread.sleep(500);assertEquals(before,calls.get())
                main { offline.value=true;player.repeatMode=Player.REPEAT_MODE_OFF }
                Thread.sleep(500);assertEquals("Offline Mix started catalog work",before,calls.get())
            } finally {
                main { mix.release();player.release();settings.enable(false) };scope.cancel()
                runBlocking { (2..5).forEach { app.repository.dao.delete(track(it).id) } }
            }
        }
    }
    @Test fun disabledDuringFetchCannotAppendStaleResults() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val settings=MixSettings(context).apply { enable(false) }
            val started=CompletableDeferred<Unit>();val gate=CompletableDeferred<Unit>()
            val provider=RecommendationProvider { _,_ -> started.complete(Unit);withContext(NonCancellable) { gate.await() };RecommendationBatch(listOf(track(2)),"Fixture") }
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            lateinit var player:ExoPlayer;lateinit var mix:MixQueueController
            main {
                player=ExoPlayer.Builder(context).build();mix=MixQueueController(player,app.repository,provider,MutableStateFlow(false),settings,scope)
                player.setMediaItem(MediaItem.Builder().setMediaId(track(1).id).setUri(track(1).uri).build());player.prepare();player.play();settings.enable(true)
            }
            try {
                runBlocking { withTimeout(10000) { started.await() } }
                main { settings.enable(false);gate.complete(Unit) }
                Thread.sleep(800)
                main { assertEquals(1,player.mediaItemCount);assertFalse(settings.status.value.loading) }
            } finally { main { mix.release();player.release();settings.enable(false) };scope.cancel() }
        }
    }
    @Test fun failedAndEmptyRecommendationsLeavePlaybackAlone() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val settings=MixSettings(context).apply { enable(false) }
            val calls=AtomicInteger(0)
            val provider=RecommendationProvider { _,_ -> if(calls.incrementAndGet()==1)throw java.io.IOException("test network failure") else RecommendationBatch(emptyList(),"Fixture") }
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            lateinit var player:ExoPlayer;lateinit var mix:MixQueueController
            main { player=ExoPlayer.Builder(context).build();mix=MixQueueController(player,app.repository,provider,MutableStateFlow(false),settings,scope)
                player.setMediaItem(MediaItem.Builder().setMediaId(track(1).id).setUri(track(1).uri).build());player.prepare();player.play();settings.enable(true) }
            try {
                await("Network failure was not recoverable") { settings.status.value.retryable && player.isPlaying }
                Thread.sleep(600);assertEquals("Mix kept retrying without backoff",1,calls.get())
                main { assertEquals(1,player.mediaItemCount);settings.retry() }
                await("Empty recommendations were not handled") { calls.get()==2 && settings.status.value.message?.startsWith("No new")==true }
                main { assertTrue(player.isPlaying);assertEquals(1,player.mediaItemCount) }
            } finally { main { mix.release();player.release();settings.enable(false) };scope.cancel() }
        }
    }
    @Test fun unavailableAutomaticSongSkipsToManualSong() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val settings=MixSettings(context).apply { enable(false) }
            val provider=RecommendationProvider { _,_ -> RecommendationBatch(emptyList(),"Fixture") }
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            lateinit var player:ExoPlayer;lateinit var mix:MixQueueController
            main {
                player=ExoPlayer.Builder(context).build();mix=MixQueueController(player,app.repository,provider,MutableStateFlow(false),settings,scope)
                val missing=MixQueue.media(track(8).copy(uri="asset:///does-not-exist.audio"))
                val manual=MediaItem.Builder().setMediaId("manual-good").setUri("asset:///mango_dawn.wav").build()
                player.setMediaItems(listOf(missing,manual));settings.enable(true);player.prepare();player.play()
            }
            try {
                await("Unavailable auto track did not yield to the manual song") { player.currentMediaItem?.mediaId=="manual-good" && player.isPlaying }
                main { assertEquals(2,player.mediaItemCount);assertNull(player.playerError) }
            } finally { main { mix.release();player.release();settings.enable(false) };scope.cancel() }
        }
    }
    @Test fun lateRecommendationsResumeEndedQueueAtFirstNewSong() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val settings=MixSettings(context).apply { enable(false) }
            val started=CompletableDeferred<Unit>();val gate=CompletableDeferred<Unit>()
            val provider=RecommendationProvider { _,_ -> started.complete(Unit);gate.await();RecommendationBatch(listOf(track(2),track(3)),"Fixture") }
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            lateinit var player:ExoPlayer;lateinit var mix:MixQueueController
            main { player=ExoPlayer.Builder(context).build();mix=MixQueueController(player,app.repository,provider,MutableStateFlow(false),settings,scope)
                player.setMediaItem(MediaItem.Builder().setMediaId(track(1).id).setUri(track(1).uri).build());player.prepare();player.play();settings.enable(true) }
            try {
                runBlocking { withTimeout(10000) { started.await() } }
                main { player.seekTo(23500) }
                await("Seed did not reach its natural end") { player.playbackState==Player.STATE_ENDED }
                main { gate.complete(Unit) }
                await("Late Mix skipped its first song or failed to resume") { player.currentMediaItem?.mediaId==track(2).id && player.isPlaying }
            } finally {
                main { mix.release();player.release();settings.enable(false) };scope.cancel()
                runBlocking { (2..3).forEach { app.repository.dao.delete(track(it).id) } }
            }
        }
    }
    @Test fun unavailableRadioFallsBackToRealMusicSearch()=runBlocking {
        app.online.setOffline(false)
        // Deliberately nonexistent seed: only public metadata is requested, never playback/access bypass.
        val seed=Track("youtube:___________","Fluffing a Duck","Kevin MacLeod","https://www.youtube.com/watch?v=___________")
        val batch=app.online.recommendations(seed,setOf(seed.id))
        assertEquals("Related search",batch.source)
        assertTrue(batch.tracks.isNotEmpty())
        File(context.filesDir,"mix-fallback-evidence.json").writeText(JSONObject().put("source",batch.source).put("realSearchResults",batch.tracks.size).put("firstTitle",batch.tracks.first().title).toString(2))
    }
    @Test fun realProviderRecommendationsAndOfflineGuard()=runBlocking {
        app.online.setOffline(false)
        val seed=Track("youtube:6hBDh3dRvLc","Fluffing a Duck","Kevin MacLeod","https://www.youtube.com/watch?v=6hBDh3dRvLc",68000)
        val batch=app.online.recommendations(seed,setOf(seed.id))
        assertTrue("No actual radio or search recommendations returned",batch.tracks.isNotEmpty())
        assertTrue(batch.tracks.all { it.id.startsWith("youtube:") && it.id!=seed.id })
        File(context.filesDir,"mix-provider-evidence.json").writeText(JSONObject().put("source",batch.source).put("seed",seed.id).put("resultCount",batch.tracks.size).put("firstId",batch.tracks.first().id).put("firstTitle",batch.tracks.first().title).toString(2))
        app.online.setOffline(true)
        val before=app.online.requestsStarted.get()
        try {
            try { app.online.recommendations(seed,emptySet());fail("Offline recommendations were allowed") } catch(e:OnlineFailure) { assertEquals("offline",e.kind) }
            assertEquals(before,app.online.requestsStarted.get())
        } finally { app.online.setOffline(false) }
    }
}
