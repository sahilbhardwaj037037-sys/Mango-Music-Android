package com.mangomusic.data

import android.app.DownloadManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.*
import com.mangomusic.domain.Track
import com.mangomusic.domain.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Entity(tableName = "tracks")
data class SavedTrack(@PrimaryKey val id: String, val title: String, val artist: String, val uri: String,
    val durationMs: Long = 0, val downloadable: Boolean = false, val favorite: Boolean = false,
    val lastPlayed: Long = 0, val playCount: Int = 0, val localUri: String? = null, val downloadId: Long = 0,
    val downloadError: String? = null, val thumbnailUrl: String? = null) {
    fun track() = Track(id, title, artist, uri, durationMs, downloadable, thumbnailUrl)
}
@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE") fun observe(): Flow<List<SavedTrack>>
    @Query("SELECT * FROM tracks WHERE id=:id") suspend fun get(id: String): SavedTrack?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(track: SavedTrack)
    @Query("UPDATE tracks SET favorite = NOT favorite WHERE id=:id") suspend fun toggleFavorite(id: String)
    @Query("UPDATE tracks SET lastPlayed=:now, playCount=playCount+1 WHERE id=:id") suspend fun played(id: String, now: Long)
    @Query("DELETE FROM tracks WHERE id=:id") suspend fun delete(id: String)
    @Query("UPDATE tracks SET lastPlayed=0, playCount=0") suspend fun clearHistory()
    @Query("UPDATE tracks SET downloadId=:job, downloadError=NULL WHERE id=:id") suspend fun downloading(id: String, job: Long)
    @Query("UPDATE tracks SET localUri=:uri, downloadId=0, downloadError=:error WHERE id=:id") suspend fun downloaded(id: String, uri: String?, error: String?)
}
@Database(entities = [SavedTrack::class], version = 2, exportSchema = true)
abstract class LibraryDatabase: RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        val MIGRATION_1_2 = object: androidx.room.migration.Migration(1,2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE tracks ADD COLUMN thumbnailUrl TEXT") }
        }
    }
}

class LibraryRepository(private val context: Context) {
    val db = Room.databaseBuilder(context, LibraryDatabase::class.java, "mango.db").addMigrations(LibraryDatabase.MIGRATION_1_2).build()
    val dao = db.library()
    val tracks = dao.observe()
    suspend fun seed() { Catalog.samples.forEach { add(it) } }
    suspend fun add(track: Track) { dao.insert(SavedTrack(track.id, track.title, track.artist, track.uri, track.durationMs, track.downloadable, thumbnailUrl = track.thumbnailUrl)) }
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val folder = File(context.filesDir, "imports").apply { mkdirs() }
        val file = File(folder, id)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "This file could not be opened." }
                file.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 200L * 1024 * 1024) { "Choose an audio file smaller than 200 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val meta = MediaMetadataRetriever()
            val track = try {
                meta.setDataSource(file.absolutePath)
                val title = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "Imported audio"
                Track(id, title, meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Local library",
                    Uri.fromFile(file).toString(), meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0)
            } finally { meta.release() }
            add(track)
        } catch (e: Exception) { file.delete(); throw e }
    }
    suspend fun addStream(title: String, url: String, mayDownload: Boolean) {
        val parsed = Uri.parse(url.trim())
        require(parsed.scheme == "https" && !parsed.host.isNullOrBlank() && parsed.userInfo == null) { "Enter a direct HTTPS audio link." }
        require(title.isNotBlank()) { "Enter a track title." }
        add(Track(UUID.randomUUID().toString(), title.trim().take(200), "Your audio link", parsed.toString(), downloadable = mayDownload))
    }
    suspend fun download(track: Track) = withContext(Dispatchers.IO) {
        require(track.downloadable && Uri.parse(track.uri).scheme == "https") { "This track is not available to download." }
        val manager = context.getSystemService(DownloadManager::class.java)
        val job = manager.enqueue(DownloadManager.Request(Uri.parse(track.uri)).setTitle(track.title)
            .setAllowedOverMetered(true).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, "audio", "${track.id}.audio"))
        dao.downloading(track.id, job)
    }
    suspend fun refreshDownloads(saved: List<SavedTrack>) = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(DownloadManager::class.java)
        saved.filter { it.downloadId > 0 }.forEach { track ->
            manager.query(DownloadManager.Query().setFilterById(track.downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) { dao.downloaded(track.id, null, "Download was removed. Try again."); return@forEach }
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val local = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        val meta = MediaMetadataRetriever()
                        try {
                            meta.setDataSource(context, Uri.parse(local))
                            require((meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0)
                            dao.downloaded(track.id, local, null)
                        } catch (_: Exception) {
                            manager.remove(track.downloadId)
                            dao.downloaded(track.id, null, "The download is not playable audio. Check the link and retry.")
                        } finally { meta.release() }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        manager.remove(track.downloadId)
                        dao.downloaded(track.id, null, "Download failed ($reason). Check your connection and retry.")
                    }
                }
            }
        }
    }
}
