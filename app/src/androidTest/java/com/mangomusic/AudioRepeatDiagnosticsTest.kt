package com.mangomusic

import android.content.ComponentName
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.mangomusic.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in, full natural first/repeat passes. Never seeks over the first-pass problem. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioRepeatDiagnosticsTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    @Test fun compareFreshAndNaturalRepeatForTwoSongs() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("audioComparison")=="true")
        val context=instrumentation.targetContext
        val app=context.applicationContext as MangoApp
        app.online.setOffline(false)
        app.audioDiagnostics.clear()
        app.audioDiagnostics.fingerprintPcm=true
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var future:ListenableFuture<MediaController>
        main { future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync() }
        val player=future.get(20,TimeUnit.SECONDS)
        val evidence=JSONObject().put("naturalRepeat",true).put("seekUsed",false).put("audibleQualityVerified",false)
        val songs=JSONArray()
        try {
            for ((index,query) in listOf("Kevin MacLeod Fluffing a Duck","Kevin MacLeod Sneaky Adventure").withIndex()) {
                val results=runBlocking { app.online.search(query) }
                val track=results.firstOrNull { it.artist=="Kevin MacLeod" && it.durationMs in 30000..180000 }
                    ?: results.first { it.durationMs in 30000..180000 }
                runBlocking { app.repository.add(track) }
                val started=android.os.SystemClock.elapsedRealtime()
                main {
                    player.repeatMode=Player.REPEAT_MODE_ONE;player.shuffleModeEnabled=false
                    player.setMediaItem(MediaItem.Builder().setMediaId(track.id).setUri(track.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()).build())
                    player.prepare();player.play()
                }
                val readyDeadline=System.currentTimeMillis()+60000
                var playing=false
                while(!playing && System.currentTimeMillis()<readyDeadline) { main { playing=player.isPlaying && player.currentPosition>200 };Thread.sleep(100) }
                assertTrue("Fresh playback did not start",playing)
                val startup=android.os.SystemClock.elapsedRealtime()-started
                if(index==0) {
                    fun clickPlayer(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
                        if(node==null)return false
                        if(node.contentDescription?.toString()=="Open Now Playing") {
                            var target:android.view.accessibility.AccessibilityNodeInfo?=node
                            while(target!=null && !target.isClickable)target=target.parent
                            return target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true
                        }
                        return (0 until node.childCount).any { clickPlayer(node.getChild(it)) }
                    }
                    val uiDeadline=System.currentTimeMillis()+10000
                    var clicked=false
                    while(!clicked && System.currentTimeMillis()<uiDeadline) { clicked=clickPlayer(instrumentation.uiAutomation.rootInActiveWindow);if(!clicked)Thread.sleep(250) }
                    assertTrue("Mini player did not become interactive",clicked)
                    app.audioDiagnostics.record("comparisonUiReady")
                }
                val selection=app.audioDiagnostics.snapshot().let { a -> (0 until a.length()).map { a.getJSONObject(it) }.last { it.getString("event")=="transition" }.getInt("selection") }
                fun passes():Int = app.audioDiagnostics.snapshot().let { a -> (0 until a.length()).map { a.getJSONObject(it) }.filter { it.getString("event")=="transition" && it.getInt("selection")==selection }.maxOf { it.getInt("pass") } }
                val deadline=System.currentTimeMillis()+400000
                while(passes()<3 && System.currentTimeMillis()<deadline) {
                    Thread.sleep(1000)
                    main { assertNull("Native playback failed",player.playerError) }
                }
                assertTrue("Two complete natural passes did not finish",passes()>=3)
                main { player.pause() }
                songs.put(JSONObject().put("id",track.id).put("title",track.title).put("startupMs",startup).put("naturalRepeats",passes()-1).put("catalogDurationMs",track.durationMs))
                evidence.put("songs",songs).put("events",app.audioDiagnostics.snapshot())
                File(context.filesDir,"audio-repeat-diagnostics.json").writeText(evidence.toString(2))
            }
        } finally {
            evidence.put("songs",songs).put("events",app.audioDiagnostics.snapshot())
            File(context.filesDir,"audio-repeat-diagnostics.json").writeText(evidence.toString(2))
            main { player.stop();player.clearMediaItems();player.repeatMode=Player.REPEAT_MODE_OFF;player.release() }
            scenario.close();app.audioDiagnostics.fingerprintPcm=false
        }
    }
}
