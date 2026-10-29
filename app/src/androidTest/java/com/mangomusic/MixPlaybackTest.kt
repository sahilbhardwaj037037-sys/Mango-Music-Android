package com.mangomusic

import android.content.ComponentName
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.mangomusic.domain.*
import com.mangomusic.playback.*
import com.mangomusic.ui.MusicViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MixPlaybackTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val app get()=context.applicationContext as MangoApp
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun await(message:String,timeout:Long=75000,predicate:()->Boolean) {
        val end=System.currentTimeMillis()+timeout
        while(System.currentTimeMillis()<end) { var yes=false;main { yes=predicate() };if(yes)return;Thread.sleep(150) };fail(message)
    }
    private fun click(description:String) {
        fun find(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
            if(node==null)return false
            if(node.contentDescription?.toString()==description) {
                var target:android.view.accessibility.AccessibilityNodeInfo?=node
                while(target!=null && !target.isClickable)target=target.parent
                return target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true
            }
            return (0 until node.childCount).any { find(node.getChild(it)) }
        }
        val end=System.currentTimeMillis()+10000
        while(System.currentTimeMillis()<end) { if(find(instrumentation.uiAutomation.rootInActiveWindow)) { Thread.sleep(300);return };Thread.sleep(150) }
        fail("UI control did not respond: $description")
    }
    private fun screenshot(name:String) {
        File(context.filesDir,name).outputStream().use { output -> instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output) }
    }
    @Test fun actualRadioExpandsQueueAndPlaysAfterManualSongInBackground() {
        app.online.setOffline(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val seed=Track("youtube:6hBDh3dRvLc","Fluffing a Duck","Kevin MacLeod","https://www.youtube.com/watch?v=6hBDh3dRvLc",68000)
            runBlocking { app.repository.add(seed) }
            lateinit var future:ListenableFuture<MediaController>;lateinit var model:MusicViewModel
            scenario.onActivity { model=ViewModelProvider(it)[MusicViewModel::class.java] }
            main { future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync() }
            val player=future.get(20,TimeUnit.SECONDS)
            try {
                main {
                    player.repeatMode=Player.REPEAT_MODE_OFF;player.shuffleModeEnabled=true;app.mix.enable(true)
                    player.setMediaItem(MediaItem.Builder().setMediaId(seed.id).setUri(seed.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(seed.title).setArtist(seed.artist).build()).build())
                    player.prepare();player.play()
                }
                await("Real online seed did not play") { player.isPlaying && player.currentPosition>300 }
                await("Actual Mix recommendations were not appended") { player.mediaItemCount>=4 }
                click("Open Now Playing");screenshot("mix-now-playing.png")
                click("Open Up Next");screenshot("mix-up-next.png")
                main {
                    assertEquals(seed.id,player.currentMediaItem?.mediaId)
                    assertTrue(MixQueue.automatic(player.getMediaItemAt(1)))
                    model.enqueue(Catalog.samples[1])
                }
                await("Manual queue item was not inserted before Mix") { player.getMediaItemAt(1).mediaId==Catalog.samples[1].id }
                main { player.shuffleModeEnabled=true }
                await("Shuffled Next ignored manual priority") { player.nextMediaItemIndex>=0 && player.getMediaItemAt(player.nextMediaItemIndex).mediaId==Catalog.samples[1].id }
                main { player.seekToNextMediaItem() }
                await("Manual song did not play") { player.currentMediaItem?.mediaId==Catalog.samples[1].id && player.isPlaying }
                // Keep the ordinary player/session alive while its Activity is stopped.
                scenario.moveToState(Lifecycle.State.CREATED)
                main { player.seekTo(23500) }
                await("Mix did not continue automatically after the manual track") { player.currentMediaItem?.let(MixQueue::automatic)==true && player.isPlaying && player.currentPosition>600 }
                var autoId="";var source=""
                main { autoId=player.currentMediaItem!!.mediaId;source=player.currentMediaItem!!.mediaMetadata.extras?.getString(MixQueue.SOURCE).orEmpty();assertTrue(source in listOf("YouTube radio","Related search"));app.mix.enable(false) }
                await("Mix off removed the current song or left future auto tracks") {
                    player.currentMediaItem?.mediaId==autoId && player.isPlaying &&
                        (player.currentMediaItemIndex+1 until player.mediaItemCount).none { MixQueue.automatic(player.getMediaItemAt(it)) }
                }
                runBlocking { val saved=app.repository.dao.get(autoId)!!;assertTrue(saved.playCount>0);assertTrue(saved.uri.startsWith("https://www.youtube.com/watch?v=")) }
                File(context.filesDir,"mix-playback-evidence.json").writeText(JSONObject().put("seed",seed.id).put("autoTrack",autoId)
                    .put("radioOrSearchSource",source).put("realQueueExpansion",true).put("manualPriorityWithShuffle",true)
                    .put("automaticBackgroundPlayback",true).put("mixOffKeepsCurrentPlaying",true).toString(2))
            } finally { main { app.mix.enable(false);player.stop();player.clearMediaItems();player.repeatMode=Player.REPEAT_MODE_OFF;player.shuffleModeEnabled=false;player.release() } }
        }
    }
}
