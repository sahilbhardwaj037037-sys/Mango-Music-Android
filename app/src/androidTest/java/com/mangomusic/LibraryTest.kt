package com.mangomusic

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mangomusic.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryTest {
    @Test fun originalAudioImportCopiesAndReadsMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = (context.applicationContext as MangoApp).repository
        val input = java.io.File(context.cacheDir, "import-test.wav")
        context.assets.open("mango_dawn.wav").use { source -> input.outputStream().use { source.copyTo(it) } }
        val before = repository.tracks.first().map { it.id }.toSet()
        var imported: SavedTrack? = null
        try {
            repository.import(android.net.Uri.fromFile(input))
            imported = repository.tracks.first().first { it.id !in before }
            assertTrue(imported.uri.startsWith("file:"))
            assertEquals(24000L, imported.durationMs)
            assertTrue(java.io.File(android.net.Uri.parse(imported.uri).path!!).exists())
        } finally {
            imported?.let { repository.dao.delete(it.id); java.io.File(android.net.Uri.parse(it.uri).path!!).delete() }
            input.delete()
        }
    }
    @Test fun favoritesAndHistorySurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${System.nanoTime()}.db"
        fun open() = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
        var db = open()
        try {
            val track = SavedTrack("demo", "Demo", "Mango", "asset:///mango_dawn.wav")
            db.library().insert(track)
            db.library().toggleFavorite("demo")
            db.library().played("demo", 1234)
            db.library().played("demo", 2345)
            // Seeding must never replace user state.
            db.library().insert(track)
            db.close(); db = open()
            val restored = db.library().get("demo")!!
            assertTrue(restored.favorite); assertEquals(2, restored.playCount); assertEquals(2345L, restored.lastPlayed)
            db.library().clearHistory()
            assertTrue(db.library().get("demo")!!.favorite)
            assertEquals(0, db.library().get("demo")!!.playCount)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
