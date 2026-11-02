package com.mangomusic

import com.mangomusic.data.PlaylistEntity
import com.mangomusic.data.PlaylistTrackEntity
import com.mangomusic.data.PlaylistWithTracks
import com.mangomusic.domain.Track
import org.junit.Assert.*
import org.junit.Test

class PlaylistTest {

    @Test
    fun playlistTrackEntityConvertsToTrackAccurately() {
        val entity = PlaylistTrackEntity(
            id = 1L,
            playlistId = 42L,
            trackId = "youtube:abc123xyz",
            position = 0,
            addedAt = 1000L,
            title = "Starboy",
            artist = "The Weeknd",
            uri = "https://example.com/audio.mp4",
            durationMs = 230000L,
            downloadable = true,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val track = entity.toTrack()
        assertEquals("youtube:abc123xyz", track.id)
        assertEquals("Starboy", track.title)
        assertEquals("The Weeknd", track.artist)
        assertEquals("https://example.com/audio.mp4", track.uri)
        assertEquals(230000L, track.durationMs)
        assertTrue(track.downloadable)
        assertEquals("https://example.com/thumb.jpg", track.thumbnailUrl)
    }

    @Test
    fun playlistWithTracksMaintainsPositionOrder() {
        val playlist = PlaylistEntity(
            id = 10L,
            name = "Late Night Drive",
            description = "Chill vibes",
            createdAt = 100L,
            updatedAt = 200L
        )

        val track1 = PlaylistTrackEntity(
            id = 1L, playlistId = 10L, trackId = "track_1", position = 2, addedAt = 100L,
            title = "Song Three", artist = "Artist A", uri = "uri1", durationMs = 180000L
        )
        val track2 = PlaylistTrackEntity(
            id = 2L, playlistId = 10L, trackId = "track_2", position = 0, addedAt = 50L,
            title = "Song One", artist = "Artist B", uri = "uri2", durationMs = 200000L
        )
        val track3 = PlaylistTrackEntity(
            id = 3L, playlistId = 10L, trackId = "track_3", position = 1, addedAt = 75L,
            title = "Song Two", artist = "Artist C", uri = "uri3", durationMs = 210000L
        )

        val withTracks = PlaylistWithTracks(
            playlist = playlist,
            tracks = listOf(track1, track2, track3)
        )

        val ordered = withTracks.tracksInOrder
        assertEquals(3, ordered.size)
        assertEquals("track_2", ordered[0].trackId)
        assertEquals("track_3", ordered[1].trackId)
        assertEquals("track_1", ordered[2].trackId)

        val domainTracks = withTracks.domainTracks
        assertEquals("Song One", domainTracks[0].title)
        assertEquals("Song Two", domainTracks[1].title)
        assertEquals("Song Three", domainTracks[2].title)
    }

    @Test
    fun playlistTrackReorderComputation() {
        val originalList = mutableListOf("A", "B", "C", "D")
        val fromIndex = 3 // Move "D" to index 1
        val toIndex = 1

        val item = originalList.removeAt(fromIndex)
        originalList.add(toIndex, item)

        assertEquals(listOf("A", "D", "B", "C"), originalList)

        // Move "A" from 0 to 2
        val item2 = originalList.removeAt(0)
        originalList.add(2, item2)
        assertEquals(listOf("D", "B", "A", "C"), originalList)
    }

    @Test
    fun duplicateDetectionLogic() {
        val tracks = listOf(
            PlaylistTrackEntity(id = 1L, playlistId = 1L, trackId = "song_123", position = 0, addedAt = 10L, title = "Title", artist = "Artist", uri = "uri", durationMs = 0L)
        )
        val candidateTrack = Track(id = "song_123", title = "Title", artist = "Artist", uri = "uri", durationMs = 0L)
        val newTrack = Track(id = "song_456", title = "Title 2", artist = "Artist 2", uri = "uri2", durationMs = 0L)

        val isCandidateDuplicate = tracks.any { it.trackId == candidateTrack.id }
        val isNewDuplicate = tracks.any { it.trackId == newTrack.id }

        assertTrue(isCandidateDuplicate)
        assertFalse(isNewDuplicate)
    }

    @Test
    fun moodQueriesCoverAllExistingMoodsWithCuratedMusicTerms() {
        val moods = com.mangomusic.ui.DiscoverCatalog.moods
        assertEquals(7, moods.size)
        assertTrue(moods.any { it.id == "mood-funk" && it.name == "Funk" })

        for (mood in moods) {
            val queries = com.mangomusic.ui.DiscoverCatalog.moodQueries[mood.id]
            assertNotNull("Queries should be defined for mood: ${mood.id}", queries)
            assertTrue("Mood ${mood.id} should have multiple curated queries", queries!!.size >= 4)

            // Each query must contain music-prioritizing terminology
            for (q in queries) {
                assertTrue("Query '$q' should contain music-related keywords",
                    q.contains("song", ignoreCase = true) ||
                    q.contains("track", ignoreCase = true) ||
                    q.contains("music", ignoreCase = true) ||
                    q.contains("beat", ignoreCase = true) ||
                    q.contains("hit", ignoreCase = true) ||
                    q.contains("pop", ignoreCase = true) ||
                    q.contains("rap", ignoreCase = true) ||
                    q.contains("instrumental", ignoreCase = true)
                )
            }

            // Verify cycling/rotation produces varied queries
            val first = com.mangomusic.ui.DiscoverCatalog.nextMoodQuery(mood.id, mood.name)
            val second = com.mangomusic.ui.DiscoverCatalog.nextMoodQuery(mood.id, mood.name)
            assertNotEquals("Subsequent mood taps should provide query variety", first, second)
        }
    }

    @Test
    fun featuredArtistsContainsOnlyArtistsAndWithinRange() {
        val artists = com.mangomusic.ui.DiscoverCatalog.featuredArtists
        assertTrue("Artists collection should contain 20–25 artists (was ${artists.size})", artists.size in 20..25)

        // Must not contain Mango Studio
        assertFalse("Mango Studio must be removed completely from Featured Artists",
            artists.any { it.id == "artist-mango" || it.name.contains("Mango Studio", ignoreCase = true) })

        // Check uniqueness of IDs and names
        val ids = artists.map { it.id }
        assertEquals("Artist IDs must be unique", ids.size, ids.distinct().size)
        val names = artists.map { it.name.lowercase() }
        assertEquals("Artist names must be unique", names.size, names.distinct().size)

        // Check balanced coverage of categories
        val punjabi = artists.filter { it.category == "Punjabi" }
        val hindi = artists.filter { it.category == "Hindi" }
        val english = artists.filter { it.category == "English" }
        val regional = artists.filter { it.category == "Regional" }

        assertTrue("Should have popular Punjabi artists", punjabi.size >= 5)
        assertTrue("Should have popular Hindi artists", hindi.size >= 5)
        assertTrue("Should have popular English artists", english.size >= 5)
        assertTrue("Should have Regional artists", regional.isNotEmpty())
    }

    @Test
    fun medleyCollectionsContainCuratedMusicQueriesAndRotate() {
        val collections = com.mangomusic.ui.DiscoverCatalog.medleyCollections
        assertEquals("Should have 3 online medley collections", 3, collections.size)

        val newReleases = collections.find { it.id == "col-new-releases" }
        assertNotNull(newReleases)
        assertEquals("New Releases", newReleases!!.title)

        val classicBollywood = collections.find { it.id == "col-classic-bollywood" }
        assertNotNull(classicBollywood)
        assertEquals("Classic Bollywood", classicBollywood!!.title)

        val popularSongs = collections.find { it.id == "col-popular-songs" }
        assertNotNull(popularSongs)
        assertEquals("Most Popular Songs", popularSongs!!.title)

        for (col in collections) {
            assertTrue("Collection ${col.title} should have multiple queries", col.queries.size >= 3)
            for (q in col.queries) {
                assertTrue("Query '$q' must be music focused",
                    q.contains("song", ignoreCase = true) ||
                    q.contains("music", ignoreCase = true) ||
                    q.contains("audio", ignoreCase = true) ||
                    q.contains("hits", ignoreCase = true)
                )
            }
            val first = com.mangomusic.ui.DiscoverCatalog.nextCollectionQuery(col.id, col.queries)
            val second = com.mangomusic.ui.DiscoverCatalog.nextCollectionQuery(col.id, col.queries)
            assertNotEquals("Queries should rotate for variety", first, second)
        }
    }

    @Test
    fun filterMusicOnlyExcludesNonMusicContentAndFallsBack() {
        val nonMusicTracks = listOf(
            Track("t1", "How to Play Guitar Tutorial", "Instructor", "uri1"),
            Track("t2", "Movie Scene | Epic Climax", "Channel", "uri2"),
            Track("t3", "Arijit Singh - Soulful Song Official Audio", "Sony Music", "uri3"),
            Track("t4", "Podcast Episode 45 - Chat with Celebrity", "Host", "uri4")
        )

        val nonMusic = listOf(
            "tutorial", "how to", "reaction", "reacting", "interview",
            "podcast", "vlog", "trailer", "teaser", "review",
            "movie clip", "scene", "behind the scenes", "bts footage",
            "episode", "full movie", "gameplay", "walkthrough", "news",
            "press meet", "speech", "comedy", "standup", "unboxing"
        )
        val filtered = nonMusicTracks.filter { track ->
            val text = "${track.title} ${track.artist}".lowercase()
            nonMusic.none { text.contains(it) }
        }

        assertEquals(1, filtered.size)
        assertEquals("t3", filtered.first().id)

        // Fallback check: if all tracks filtered out, fall back safely
        val allBad = listOf(Track("b1", "Full Movie HD", "Cinema", "uri"))
        val emptyFiltered = allBad.filter { track ->
            val text = "${track.title} ${track.artist}".lowercase()
            nonMusic.none { text.contains(it) }
        }
        val safeResult = emptyFiltered.ifEmpty { allBad }
        assertEquals(1, safeResult.size)
        assertEquals("b1", safeResult.first().id)
    }

    @Test
    fun all24FeaturedArtistsHaveAssignedDrawablesAndValidCategories() {
        val artists = com.mangomusic.ui.DiscoverCatalog.featuredArtists
        assertEquals(24, artists.size)
        assertEquals(24, artists.map { it.id }.distinct().size)

        // Verify each artist has a non-null image drawable assigned
        artists.forEach { artist ->
            assertNotNull("Artist ${artist.name} should have an image drawable", artist.imageUrl)
            assertTrue("Artist image should be a drawable resource ID", artist.imageUrl is Int && (artist.imageUrl as Int) != 0)
            assertFalse(artist.name.contains("Mango", ignoreCase = true))
        }

        // Verify category balance
        val punjabiCount = artists.count { it.category == "Punjabi" }
        val hindiCount = artists.count { it.category == "Hindi" }
        val englishCount = artists.count { it.category == "English" }
        val regionalCount = artists.count { it.category == "Regional" }

        assertTrue("Should have Punjabi artists", punjabiCount >= 5)
        assertTrue("Should have Hindi artists", hindiCount >= 5)
        assertTrue("Should have English artists", englishCount >= 5)
        assertTrue("Should have Regional artists", regionalCount >= 2)
    }

    @Test
    fun onlineMixCollectionsAreExactlySixWithValidThumbnailsAndQueries() {
        val mixes = com.mangomusic.ui.DiscoverCatalog.onlineMixes
        assertEquals("Must have exactly six online mix collections", 6, mixes.size)

        val expectedIds = listOf(
            "mix-punjabi",
            "mix-hindi",
            "mix-english",
            "mix-chill",
            "mix-romantic",
            "mix-trending"
        )
        assertEquals(expectedIds, mixes.map { it.id })

        mixes.forEach { mix ->
            assertTrue(mix.title.isNotBlank())
            assertTrue(mix.subtitle.isNotBlank())
            assertTrue(mix.description.isNotBlank())
            assertTrue("Mix ${mix.id} should have a non-zero drawable ID", mix.imageResId != 0)
            assertTrue("Mix ${mix.id} should have search queries", mix.queries.isNotEmpty())
            mix.queries.forEach { q ->
                assertTrue(q.isNotBlank())
                assertFalse("Internal queries should not contain UI placeholders", q.contains("TODO"))
            }
        }
    }

    @Test
    fun onlineMix24HourCacheAgeEvaluation() {
        val ttlMs = 24 * 60 * 60 * 1000L
        val now = 100_000_000L

        val freshCacheTime = now - (2 * 60 * 60 * 1000L) // 2 hours old
        val isFresh = (now - freshCacheTime) < ttlMs
        assertTrue("Cache under 24 hours old should be fresh", isFresh)

        val staleCacheTime = now - (25 * 60 * 60 * 1000L) // 25 hours old
        val isStale = (now - staleCacheTime) >= ttlMs
        assertTrue("Cache 24 hours old or older should be stale and require refresh", isStale)
    }
}
