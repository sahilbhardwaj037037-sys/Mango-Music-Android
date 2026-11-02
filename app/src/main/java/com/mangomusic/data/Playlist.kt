package com.mangomusic.data

import androidx.room.*
import com.mangomusic.domain.Track
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId", "trackId"], unique = true),
        Index(value = ["playlistId", "position"])
    ]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: String,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val title: String,
    val artist: String,
    val uri: String,
    val durationMs: Long = 0,
    val downloadable: Boolean = false,
    val thumbnailUrl: String? = null
) {
    fun toTrack() = Track(
        id = trackId,
        title = title,
        artist = artist,
        uri = uri,
        durationMs = durationMs,
        downloadable = downloadable,
        thumbnailUrl = thumbnailUrl
    )
}

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val tracks: List<PlaylistTrackEntity> = emptyList()
) {
    val tracksInOrder: List<PlaylistTrackEntity>
        get() = tracks.sortedWith(compareBy({ it.position }, { it.addedAt }))

    val domainTracks: List<Track>
        get() = tracksInOrder.map { it.toTrack() }
}

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC, createdAt DESC")
    fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylistWithTracks(id: Long): Flow<PlaylistWithTracks?>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlaylistMeta(id: Long, name: String, description: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchPlaylist(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: String): Boolean

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun getPlaylistsContainingTrack(trackId: String): List<Long>

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePosition(playlistId: Long, trackId: String, position: Int)
}
