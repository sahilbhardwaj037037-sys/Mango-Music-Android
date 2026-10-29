package com.mangomusic

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mangomusic.data.LibraryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun versionOneLibraryAndDownloadArePreserved()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="migration-${System.nanoTime()}.db"
        val audio=File(context.filesDir,"migration-${System.nanoTime()}.audio").apply { writeText("preserve-existing-audio") }
        val old=context.openOrCreateDatabase(name,0,null)
        old.execSQL("CREATE TABLE tracks (id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,artist TEXT NOT NULL,uri TEXT NOT NULL,durationMs INTEGER NOT NULL,downloadable INTEGER NOT NULL,favorite INTEGER NOT NULL,lastPlayed INTEGER NOT NULL,playCount INTEGER NOT NULL,localUri TEXT,downloadId INTEGER NOT NULL,downloadError TEXT)")
        old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        old.execSQL("INSERT INTO room_master_table VALUES(42,'00d65950c9ed4ee2d6a522348538c987')")
        val local=android.net.Uri.fromFile(audio).toString()
        old.execSQL("INSERT INTO tracks VALUES('existing','Existing favorite','Artist','https://example.com/audio.mp3',12000,1,1,1234,7,?,0,NULL)",arrayOf(local))
        old.version=1;old.close()
        val db=Room.databaseBuilder(context,LibraryDatabase::class.java,name).addMigrations(LibraryDatabase.MIGRATION_1_2).build()
        try {
            val track=db.library().get("existing")!!
            assertTrue(track.favorite);assertEquals(7,track.playCount);assertEquals(1234L,track.lastPlayed)
            assertEquals(local,track.localUri);assertNull(track.thumbnailUrl)
            assertEquals("preserve-existing-audio",audio.readText())
        } finally { db.close();context.deleteDatabase(name);audio.delete() }
    }
}
