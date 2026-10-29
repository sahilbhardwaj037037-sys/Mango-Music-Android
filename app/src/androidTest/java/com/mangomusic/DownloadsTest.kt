package com.mangomusic

import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.mangomusic.data.*
import com.mangomusic.domain.Track
import com.mangomusic.downloads.DownloadPolicy
import com.mangomusic.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DownloadsTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val app get()=context.applicationContext as MangoApp
    private fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
    private fun awaitDownload(id:String,timeout:Long=240000):SavedTrack {
        val end=System.currentTimeMillis()+timeout
        while(System.currentTimeMillis()<end) {
            val row=runBlocking { app.repository.dao.get(id) }!!
            if(row.downloadState=="COMPLETED")return row
            if(row.downloadState in listOf("FAILED","PAUSED","CANCELLED"))fail("Download $id: ${row.downloadState}: ${row.downloadError}")
            Thread.sleep(250)
        }
        throw AssertionError("Download did not complete: $id")
    }
    @Test fun directCompletionDuplicatePersistenceAndSafeDeletion() {
        ActivityScenario.launch(MainActivity::class.java).use {
            app.online.setOffline(false)
            val track=Track("download-direct-test","Authorized test audio","Google test media","https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",downloadable=true)
            try {
                runBlocking { app.repository.add(track);app.repository.dao.toggleFavorite(track.id);app.repository.dao.played(track.id,1234);app.downloads.enqueue(track,true);app.downloads.enqueue(track,true) }
                val row=awaitDownload(track.id)
                assertTrue(row.durationMs>0);assertTrue(row.downloadedBytes>0);assertEquals(64,row.fileSha256!!.length)
                val path=File(Uri.parse(row.localUri).path!!);assertTrue(path.isFile)
                runBlocking { app.downloads.enqueue(track,true) }
                assertEquals(row.downloadWork,runBlocking { app.repository.dao.get(track.id)!!.downloadWork })
                val reopened=androidx.room.Room.databaseBuilder(context,LibraryDatabase::class.java,"mango.db").addMigrations(LibraryDatabase.MIGRATION_1_2,LibraryDatabase.MIGRATION_2_3).build()
                try { assertEquals(row.localUri,runBlocking { reopened.library().get(track.id)!!.localUri }) } finally { reopened.close() }
                runBlocking { app.downloads.remove(track.id) }
                assertFalse(path.exists())
                val kept=runBlocking { app.repository.dao.get(track.id)!! };assertTrue(kept.favorite);assertEquals(1234L,kept.lastPlayed);assertNull(kept.localUri)
            } finally { runBlocking { app.downloads.remove(track.id);app.repository.dao.delete(track.id) } }
        }
    }
    @Test fun cancelRetryAndStaleLeaseCannotPublish() {
        ActivityScenario.launch(MainActivity::class.java).use {
            app.online.setOffline(false)
            val track=Track("download-cancel-test","Authorized test audio","Google test media","https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",downloadable=true)
            try {
                runBlocking {
                    app.downloads.enqueue(track,true)
                    val previous=app.repository.dao.get(track.id)!!
                    app.downloads.cancel(track.id)
                    assertEquals("CANCELLED",app.repository.dao.get(track.id)!!.downloadState)
                    assertEquals(0,app.repository.dao.completeDownload(track.id,previous.downloadWork!!,previous.downloadAttempt ?: "stale","file:///wrong",null,1,1,"bad"))
                    app.downloads.enqueue(track,true)
                }
                assertNotNull(awaitDownload(track.id).localUri)
            } finally { runBlocking { app.downloads.remove(track.id);app.repository.dao.delete(track.id) } }
        }
    }
    @Test fun deletionNeverRemovesUnownedFilesOrImportedAudio()=runBlocking {
        val file=File(context.filesDir,"unrelated-download-safety.wav").apply { writeText("keep") }
        val id="download-path-safety"
        try {
            app.repository.dao.insert(SavedTrack(id,"Keep","Artist",Uri.fromFile(file).toString(),favorite=true,lastPlayed=12,localUri=Uri.fromFile(file).toString()))
            app.downloads.remove(id)
            assertEquals("keep",file.readText());assertTrue(app.repository.dao.get(id)!!.favorite)
        } finally { file.delete();app.repository.dao.delete(id) }
    }
    @Test fun realYouTubeDownloadPlaysOfflineWithoutResolver() {
        // Official Blender Foundation CC BY open movie: https://peach.blender.org/about/
        val track=Track("youtube:aqz-KE-bpKQ","Big Buck Bunny","Blender Foundation","https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        ActivityScenario.launch(MainActivity::class.java).use {
            app.online.setOffline(false);app.mix.enable(false)
            lateinit var future:ListenableFuture<MediaController>
            main { future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync() }
            val player=future.get(20,TimeUnit.SECONDS)
            val wifi=android.provider.Settings.Global.getInt(context.contentResolver,"wifi_on",1)!=0
            val mobile=android.provider.Settings.Global.getInt(context.contentResolver,"mobile_data",1)!=0
            fun shell(command:String) { instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
            try {
                // Independent direct online audio plays while the YouTube transfer is active.
                main { player.repeatMode=Player.REPEAT_MODE_ONE;player.setMediaItem(MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"));player.prepare();player.play() }
                val end=System.currentTimeMillis()+30000
                while(System.currentTimeMillis()<end) { var ready=false;main { ready=player.isPlaying };if(ready)break;Thread.sleep(100) }
                runBlocking { app.downloads.enqueue(track,true) }
                val row=awaitDownload(track.id)
                main { assertNull(player.playerError);assertTrue("Online playback interrupted by download",player.isPlaying) }
                assertTrue(row.durationMs>500000);assertNotNull(app.downloads.local(row))
                app.online.setOffline(true)
                shell("svc wifi disable");shell("svc data disable")
                val manager=context.getSystemService(android.net.ConnectivityManager::class.java)
                val networkEnd=System.currentTimeMillis()+15000
                while(manager.activeNetwork!=null && System.currentTimeMillis()<networkEnd)Thread.sleep(200)
                assertNull("Emulator still has an active network",manager.activeNetwork)
                val requests=app.online.requestsStarted.get()
                // Canonical provider URI deliberately exercises service-side local preference.
                main { player.setMediaItem(MediaItem.Builder().setMediaId(track.id).setUri(track.uri).build());player.prepare();player.play() }
                var played=false;val until=System.currentTimeMillis()+20000
                while(System.currentTimeMillis()<until) { main { played=player.isPlaying && player.currentPosition>1200 };if(played)break;Thread.sleep(100) }
                main { assertNull(player.playerError);assertTrue("Saved YouTube audio did not play offline",played);player.pause();player.seekTo(15000);player.play() }
                Thread.sleep(1200);main { assertTrue(player.isPlaying);assertTrue(player.currentPosition>=15000) }
                assertEquals("Offline local playback invoked resolver",requests,app.online.requestsStarted.get())
                File(context.filesDir,"download-evidence.json").writeText(org.json.JSONObject().put("id",track.id).put("bytes",row.downloadedBytes).put("durationMs",row.durationMs).put("sha256",row.fileSha256).put("localPlayback",true).put("resolverRequests",app.online.requestsStarted.get()-requests).put("onlinePlaybackDuringDownload",true).toString(2))
            } finally {
                main { player.repeatMode=Player.REPEAT_MODE_OFF;player.stop();player.clearMediaItems();player.release() }
                if(wifi)shell("svc wifi enable");if(mobile)shell("svc data enable")
                app.online.setOffline(false)
                runBlocking { app.downloads.remove(track.id) }
            }
        }
    }
}
