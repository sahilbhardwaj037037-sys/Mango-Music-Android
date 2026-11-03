package com.mangomusic.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mangomusic.MangoApp
import com.mangomusic.data.*
import com.mangomusic.domain.*
import com.mangomusic.playback.PlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ArtistProfileState(
    val artist: FeaturedArtist? = null,
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null
)

data class MoodDiscoveryState(
    val mood: MoodItem? = null,
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null
)

data class MedleyCollectionState(
    val collection: MedleyCollection? = null,
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicViewModel(application: Application): AndroidViewModel(application) {
    private val repository = (application as MangoApp).repository
    private val downloads = (application as MangoApp).downloads
    val downloadConsent=MutableStateFlow<Track?>(null)
    private val online = (application as MangoApp).online
    private val mixSettings=(application as MangoApp).mix
    val mixEnabled=mixSettings.enabled
    val mixStatus=mixSettings.status
    fun mix(value: Boolean) { mixSettings.enable(value) }
    fun retryMix() { mixSettings.retry() }
    val updater = (application as MangoApp).updater
    val updateUiState = updater.uiState
    val updateDialogVisible = updater.dialogVisible
    fun checkForUpdates(isManual: Boolean = false) { updater.checkForUpdates(isManual) }
    fun startUpdateDownload(release: com.mangomusic.updater.ReleaseInfo) { updater.startDownload(release) }
    fun cancelUpdateDownload(release: com.mangomusic.updater.ReleaseInfo) { updater.cancelDownload(release) }
    fun installUpdate(file: java.io.File) { updater.installApk(file) }
    fun dismissUpdateDialog() { updater.dismissDialog() }
    fun showUpdateDialog() { updater.showDialog() }
    val onlineSearch = MutableStateFlow(com.mangomusic.online.OnlineSearchState())
    private var searchJob: Job? = null
    val artistProfile = MutableStateFlow(ArtistProfileState())
    private val artistTracksCache = mutableMapOf<String, List<Track>>()
    private var artistJob: Job? = null
    val moodDiscovery = MutableStateFlow(MoodDiscoveryState())
    private val moodTracksCache = mutableMapOf<String, List<Track>>()
    private var moodJob: Job? = null
    val medleyCollection = MutableStateFlow(MedleyCollectionState())
    private val collectionTracksCache = mutableMapOf<String, List<Track>>()
    private var collectionJob: Job? = null
    val trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingLoading = MutableStateFlow<Boolean>(false)
    private val trendingCache = mutableMapOf<String, List<Track>>()
    private var trendingJob: Job? = null
    val onlineMixState = MutableStateFlow(OnlineMixDetailState())
    private var mixFetchJob: Job? = null
    private val mixCachePrefs = application.getSharedPreferences("online_mix_cache", 0)
    private val MIX_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    val saved = repository.tracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selectedPlaylistId = MutableStateFlow<Long?>(null)
    private val prefs = application.getSharedPreferences("appearance", 0)
    var player: MediaController? = null; private set
    private val future = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java))).buildAsync()
    val playback = MutableStateFlow(PlaybackState())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val theme = MutableStateFlow(prefs.getString("theme", "System") ?: "System")
    val offline = MutableStateFlow(prefs.getBoolean("offline", false))
    private var hadPlaybackError = false
    private val listener = object: Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
            if (player.isPlaying && hadPlaybackError) { error.value = null; hadPlaybackError = false }
        }
        override fun onPlayerError(e: PlaybackException) {
            hadPlaybackError = true
            val failure = generateSequence(e as Throwable?) { it.cause }.filterIsInstance<com.mangomusic.online.OnlineFailure>().firstOrNull()
            error.value = failure?.message ?: "Audio could not play. Check the file or connection, then retry. (${e.errorCodeName})"
        }
    }
    init {
        launch { repository.cleanupDemoTracks() }
        viewModelScope.launch(Dispatchers.IO) {
            updater.checkForUpdates(isManual = false)
        }
        future.addListener({
            try { player = future.get().also { it.addListener(listener) }; refresh() }
            catch (e: Exception) { error.value = "Could not connect to playback. Reopen Mango Music." }
        }, androidx.core.content.ContextCompat.getMainExecutor(application))
        viewModelScope.launch {
            while (isActive) {
                val isPlaying = player?.isPlaying == true
                if (isPlaying) {
                    refresh()
                    delay(500)
                } else {
                    delay(1500)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                try {
                    val list = saved.value
                    if (list.any { it.downloadState in com.mangomusic.downloads.DownloadPolicy.active }) {
                        repository.refreshDownloads(list)
                        downloads.reconcile(list)
                        delay(2000)
                    } else {
                        delay(8000)
                    }
                } catch (e: Exception) {
                    error.value = "Could not check downloads: ${e.localizedMessage}"
                    delay(5000)
                }
            }
        }
        loadTrending("All Music")
    }
    private fun refresh() {
        val p = player ?: return
        playback.value = PlaybackState(p.currentMediaItem?.mediaId, p.isPlaying, p.playbackState == Player.STATE_BUFFERING,
            p.currentPosition.coerceAtLeast(0), p.duration.coerceAtLeast(0), p.repeatMode, p.shuffleModeEnabled,
            (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }, p.currentMediaItemIndex, true, p.playerError != null, (0 until p.mediaItemCount).filter { com.mangomusic.playback.MixQueue.automatic(p.getMediaItemAt(it)) }.toSet())
    }
    private fun launch(action: suspend () -> Unit) { viewModelScope.launch { try { action() } catch (e: Exception) { error.value = e.localizedMessage ?: "Something went wrong. Please retry." } } }
    fun appearance(value: String) { theme.value = value; prefs.edit().putString("theme", value).apply() }
    fun offline(value: Boolean) {
        player?.stop(); player?.clearMediaItems(); offline.value = value
        online.setOffline(value)
        if (value) {
            launch { downloads.pauseAll() }; searchJob?.cancel(); onlineSearch.value = com.mangomusic.online.OnlineSearchState()
            artistJob?.cancel()
            val currentArtist = artistProfile.value.artist
            if (currentArtist != null && artistProfile.value.loading) {
                val localTracks = saved.value.map { it.track() }.filter {
                    it.artist.contains(currentArtist.name, ignoreCase = true) || it.title.contains(currentArtist.name, ignoreCase = true)
                }
                if (localTracks.isNotEmpty()) {
                    artistProfile.value = ArtistProfileState(artist = currentArtist, tracks = localTracks)
                } else {
                    artistProfile.value = ArtistProfileState(artist = currentArtist, error = "Online audio is unavailable in Offline Mode.")
                }
            }
            moodJob?.cancel()
            val currentMood = moodDiscovery.value.mood
            if (currentMood != null && moodDiscovery.value.loading) {
                moodDiscovery.value = MoodDiscoveryState(mood = currentMood, error = "Online audio is unavailable in Offline Mode.")
            }
            collectionJob?.cancel()
            val currentCollection = medleyCollection.value.collection
            if (currentCollection != null && medleyCollection.value.loading) {
                medleyCollection.value = MedleyCollectionState(collection = currentCollection, error = "Online audio is unavailable in Offline Mode.")
            }
            trendingJob?.cancel()
            trendingLoading.value = false
            mixFetchJob?.cancel()
            val currentMixState = onlineMixState.value
            if (currentMixState.loading && currentMixState.tracks.isEmpty()) {
                val mix = currentMixState.mix
                if (mix != null) {
                    onlineMixState.value = OnlineMixDetailState(mix = mix, error = "Online audio is unavailable in Offline Mode.")
                }
            } else if (currentMixState.refreshing) {
                onlineMixState.value = currentMixState.copy(refreshing = false)
            }
        }
        prefs.edit().putBoolean("offline", value).apply()
    }
    fun selectArtist(artist: FeaturedArtist) {
        val current = artistProfile.value
        if (current.artist?.id == artist.id && (current.loading || current.tracks.isNotEmpty())) {
            return
        }
        val cached = artistTracksCache[artist.id]
        if (cached != null && cached.isNotEmpty()) {
            artistProfile.value = ArtistProfileState(artist = artist, tracks = cached)
            return
        }
        fetchArtist(artist)
    }
    fun retryArtist() {
        val artist = artistProfile.value.artist ?: return
        artistTracksCache.remove(artist.id)
        fetchArtist(artist)
    }
    private fun fetchArtist(artist: FeaturedArtist) {
        artistJob?.cancel()
        val initialLocal = saved.value.map { it.track() }.filter {
            it.artist.contains(artist.name, ignoreCase = true) || it.title.contains(artist.name, ignoreCase = true)
        }
        artistProfile.value = ArtistProfileState(artist = artist, loading = true, tracks = initialLocal)
        if (offline.value) {
            if (initialLocal.isNotEmpty()) {
                artistProfile.value = ArtistProfileState(artist = artist, tracks = initialLocal)
            } else {
                artistProfile.value = ArtistProfileState(
                    artist = artist,
                    error = "Online audio is unavailable in Offline Mode. Switch off Offline Mode in Settings to explore ${artist.name}'s music."
                )
            }
            return
        }
        artistJob = viewModelScope.launch {
            try {
                val results = online.search(artist.name)
                ensureActive()
                val combined = (initialLocal + results).distinctBy { it.id }
                artistTracksCache[artist.id] = combined
                artistProfile.value = ArtistProfileState(artist = artist, tracks = combined)
            } catch (e: TimeoutCancellationException) {
                if (initialLocal.isNotEmpty()) {
                    artistProfile.value = ArtistProfileState(artist = artist, tracks = initialLocal)
                } else {
                    artistProfile.value = ArtistProfileState(artist = artist, error = "Search timed out. Please retry.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (initialLocal.isNotEmpty()) {
                    artistProfile.value = ArtistProfileState(artist = artist, tracks = initialLocal)
                } else {
                    artistProfile.value = ArtistProfileState(
                        artist = artist,
                        error = e.localizedMessage ?: "Could not load songs for ${artist.name}. Please retry."
                    )
                }
            }
        }
    }

    fun selectMood(mood: MoodItem) {
        val current = moodDiscovery.value
        if (current.mood?.id == mood.id && (current.loading || current.tracks.isNotEmpty())) {
            return
        }
        val cached = moodTracksCache[mood.id]
        if (cached != null && cached.isNotEmpty()) {
            moodDiscovery.value = MoodDiscoveryState(mood = mood, tracks = cached)
            return
        }
        fetchMood(mood)
    }

    fun retryMood() {
        val mood = moodDiscovery.value.mood ?: return
        moodTracksCache.remove(mood.id)
        fetchMood(mood)
    }

    private fun fetchMood(mood: MoodItem) {
        moodJob?.cancel()
        moodDiscovery.value = MoodDiscoveryState(mood = mood, loading = true)
        if (offline.value) {
            moodDiscovery.value = MoodDiscoveryState(
                mood = mood,
                error = "Online audio is unavailable in Offline Mode. Switch off Offline Mode in Settings to explore ${mood.name} music."
            )
            return
        }
        val query = DiscoverCatalog.nextMoodQuery(mood.id, mood.name)
        moodJob = viewModelScope.launch {
            try {
                val results = online.search(query)
                ensureActive()
                val musicOnly = filterMusicOnly(results)
                moodTracksCache[mood.id] = musicOnly
                moodDiscovery.value = MoodDiscoveryState(mood = mood, tracks = musicOnly)
            } catch (e: TimeoutCancellationException) {
                moodDiscovery.value = MoodDiscoveryState(mood = mood, error = "Search timed out. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                moodDiscovery.value = MoodDiscoveryState(
                    mood = mood,
                    error = e.localizedMessage ?: "Could not load songs for ${mood.name}. Please retry."
                )
            }
        }
    }

    fun selectMedleyCollection(collection: MedleyCollection) {
        val current = medleyCollection.value
        if (current.collection?.id == collection.id && (current.loading || current.tracks.isNotEmpty())) {
            return
        }
        val cached = collectionTracksCache[collection.id]
        if (cached != null && cached.isNotEmpty()) {
            medleyCollection.value = MedleyCollectionState(collection = collection, tracks = cached)
            return
        }
        fetchMedleyCollection(collection)
    }

    fun retryMedleyCollection() {
        val col = medleyCollection.value.collection ?: return
        collectionTracksCache.remove(col.id)
        fetchMedleyCollection(col)
    }

    private fun fetchMedleyCollection(collection: MedleyCollection) {
        collectionJob?.cancel()
        medleyCollection.value = MedleyCollectionState(collection = collection, loading = true)
        if (offline.value) {
            val matchingLocal = saved.value.map { it.track() }.filter(::available)
            if (matchingLocal.isNotEmpty()) {
                medleyCollection.value = MedleyCollectionState(collection = collection, tracks = matchingLocal)
            } else {
                medleyCollection.value = MedleyCollectionState(
                    collection = collection,
                    error = "Online audio is unavailable in Offline Mode. Switch off Offline Mode in Settings to explore ${collection.title}."
                )
            }
            return
        }
        val query = DiscoverCatalog.nextCollectionQuery(collection.id, collection.queries)
        collectionJob = viewModelScope.launch {
            try {
                val results = online.search(query)
                ensureActive()
                val musicOnly = filterMusicOnly(results)
                collectionTracksCache[collection.id] = musicOnly
                medleyCollection.value = MedleyCollectionState(collection = collection, tracks = musicOnly)
            } catch (e: TimeoutCancellationException) {
                medleyCollection.value = MedleyCollectionState(collection = collection, error = "Search timed out. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                medleyCollection.value = MedleyCollectionState(
                    collection = collection,
                    error = e.localizedMessage ?: "Could not load songs for ${collection.title}. Please retry."
                )
            }
        }
    }

    fun loadTrending(category: String = "All Music") {
        trendingJob?.cancel()
        val cached = trendingCache[category]
        if (cached != null && cached.isNotEmpty()) {
            trendingTracks.value = cached
            return
        }
        val localFallback = saved.value.map { it.track() }.filter(::available).take(8)
        if (offline.value) {
            trendingTracks.value = localFallback
            return
        }
        trendingLoading.value = trendingTracks.value.isEmpty()
        val query = when (category) {
            "Punjabi" -> "trending punjabi songs latest hits"
            "Hindi" -> "trending hindi bollywood songs latest"
            "English" -> "trending english billboard songs hits"
            "Regional" -> "trending regional indian music songs"
            else -> "top trending official music songs"
        }
        trendingJob = viewModelScope.launch {
            try {
                val results = online.search(query)
                ensureActive()
                val musicOnly = filterMusicOnly(results).take(8)
                trendingCache[category] = musicOnly
                trendingTracks.value = musicOnly
            } catch (e: TimeoutCancellationException) {
                if (trendingTracks.value.isEmpty()) trendingTracks.value = localFallback
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (trendingTracks.value.isEmpty()) trendingTracks.value = localFallback
            } finally {
                trendingLoading.value = false
            }
        }
    }

    internal fun getMixCache(mixId: String): Pair<Long, List<Track>>? {
        val time = mixCachePrefs.getLong("cache_time_$mixId", 0L)
        val jsonStr = mixCachePrefs.getString("cache_tracks_$mixId", null)
        if (time > 0 && !jsonStr.isNullOrBlank()) {
            try {
                val array = org.json.JSONArray(jsonStr)
                val list = (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    Track(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        uri = obj.getString("uri"),
                        durationMs = obj.optLong("durationMs", 0L),
                        downloadable = obj.optBoolean("downloadable", false),
                        thumbnailUrl = if (obj.has("thumbnailUrl") && !obj.isNull("thumbnailUrl")) obj.getString("thumbnailUrl") else null
                    )
                }
                if (list.isNotEmpty()) return Pair(time, list)
            } catch (_: Exception) {}
        }
        return null
    }

    internal fun saveMixCache(mixId: String, tracks: List<Track>, timestamp: Long) {
        try {
            val array = org.json.JSONArray()
            tracks.forEach { t ->
                val obj = org.json.JSONObject()
                obj.put("id", t.id)
                obj.put("title", t.title)
                obj.put("artist", t.artist)
                obj.put("uri", t.uri)
                obj.put("durationMs", t.durationMs)
                obj.put("downloadable", t.downloadable)
                if (t.thumbnailUrl != null) obj.put("thumbnailUrl", t.thumbnailUrl)
                array.put(obj)
            }
            mixCachePrefs.edit()
                .putLong("cache_time_$mixId", timestamp)
                .putString("cache_tracks_$mixId", array.toString())
                .apply()
        } catch (_: Exception) {}
    }

    fun openOnlineMix(mix: OnlineMixItem) {
        val current = onlineMixState.value
        if (current.mix?.id == mix.id && (current.loading || current.refreshing)) return

        val cached = getMixCache(mix.id)
        val now = System.currentTimeMillis()
        if (cached != null) {
            val (cachedTime, tracks) = cached
            val isExpired = (now - cachedTime) >= MIX_CACHE_TTL_MS
            onlineMixState.value = OnlineMixDetailState(
                mix = mix,
                tracks = tracks,
                lastFetchedAt = cachedTime,
                isFromCache = true
            )
            if (isExpired && !offline.value) {
                fetchOnlineMix(mix, isRefresh = true)
            }
        } else {
            if (offline.value) {
                onlineMixState.value = OnlineMixDetailState(
                    mix = mix,
                    error = "You are currently offline. Connect to the internet to load this mix."
                )
            } else {
                fetchOnlineMix(mix, isRefresh = false)
            }
        }
    }

    fun refreshOnlineMix() {
        val mix = onlineMixState.value.mix ?: return
        if (offline.value) {
            error.value = "Cannot refresh while offline."
            return
        }
        fetchOnlineMix(mix, isRefresh = true)
    }

    fun retryOnlineMix() {
        val mix = onlineMixState.value.mix ?: return
        val cached = getMixCache(mix.id)
        fetchOnlineMix(mix, isRefresh = cached != null)
    }

    private fun fetchOnlineMix(mix: OnlineMixItem, isRefresh: Boolean) {
        mixFetchJob?.cancel()
        if (isRefresh) {
            onlineMixState.value = onlineMixState.value.copy(refreshing = true, error = null)
        } else {
            onlineMixState.value = OnlineMixDetailState(mix = mix, loading = true, error = null)
        }

        mixFetchJob = viewModelScope.launch {
            try {
                val rawTracks = mutableListOf<Track>()
                if (mix.queries.isNotEmpty()) {
                    rawTracks += online.search(mix.queries[0])
                }
                ensureActive()
                if (rawTracks.size < 20 && mix.queries.size > 1) {
                    try {
                        rawTracks += online.search(mix.queries[1])
                    } catch (_: Exception) {}
                }
                ensureActive()
                val musicOnly = filterMusicOnly(rawTracks).distinctBy { it.id }.take(20)
                if (musicOnly.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    saveMixCache(mix.id, musicOnly, now)
                    onlineMixState.value = OnlineMixDetailState(
                        mix = mix,
                        tracks = musicOnly,
                        lastFetchedAt = now,
                        loading = false,
                        refreshing = false
                    )
                } else {
                    if (isRefresh) {
                        onlineMixState.value = onlineMixState.value.copy(refreshing = false)
                    } else {
                        onlineMixState.value = OnlineMixDetailState(
                            mix = mix,
                            error = "No songs found for this mix. Please tap retry.",
                            loading = false,
                            refreshing = false
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (isRefresh) {
                    onlineMixState.value = onlineMixState.value.copy(refreshing = false)
                } else {
                    onlineMixState.value = OnlineMixDetailState(
                        mix = mix,
                        error = "Search timed out. Please tap retry.",
                        loading = false,
                        refreshing = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isRefresh) {
                    onlineMixState.value = onlineMixState.value.copy(refreshing = false)
                } else {
                    onlineMixState.value = OnlineMixDetailState(
                        mix = mix,
                        error = e.message ?: "Failed to load mix. Tap retry.",
                        loading = false,
                        refreshing = false
                    )
                }
            }
        }
    }

    internal fun filterMusicOnly(tracks: List<Track>): List<Track> {
        val nonMusic = listOf(
            "tutorial", "how to", "reaction", "reacting", "interview",
            "podcast", "vlog", "trailer", "teaser", "review",
            "movie clip", "scene", "behind the scenes", "bts footage",
            "episode", "full movie", "gameplay", "walkthrough", "news",
            "press meet", "speech", "comedy", "standup", "unboxing"
        )
        val filtered = tracks.filter { track ->
            val text = "${track.title} ${track.artist}".lowercase()
            nonMusic.none { text.contains(it) }
        }
        return filtered.ifEmpty { tracks }
    }
    fun available(track: Track): Boolean = !offline.value || track.uri.startsWith("asset:") || track.uri.startsWith("file:") || saved.value.any { it.id == track.id && downloads.local(it) != null }
    fun searchOnline(query: String, enabled: Boolean) {
        searchJob?.cancel()
        val term = query.trim()
        onlineSearch.value = com.mangomusic.online.OnlineSearchState(query=term)
        if (!enabled || offline.value || term.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(450)
            onlineSearch.value = com.mangomusic.online.OnlineSearchState(query=term,loading=true)
            try {
                val results=online.search(term)
                ensureActive()
                onlineSearch.value = com.mangomusic.online.OnlineSearchState(query=term,tracks=results)
            } catch (e: TimeoutCancellationException) {
                onlineSearch.value = com.mangomusic.online.OnlineSearchState(query=term,error="Search timed out. Please retry.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                onlineSearch.value = com.mangomusic.online.OnlineSearchState(query=term,error=if (e is TimeoutCancellationException) "Search timed out. Please retry." else e.localizedMessage ?: "Search failed. Please retry.")
            }
        }
    }
    private fun media(track: Track, next: Boolean=false): MediaItem {
        val local = downloads.local(saved.value.find { it.id == track.id })
        return MediaItem.Builder().setMediaId(track.id).setUri(local ?: track.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setArtworkUri(track.thumbnailUrl?.let(Uri::parse)).setExtras(android.os.Bundle().apply { putString(com.mangomusic.playback.MixQueue.ENTRY,java.util.UUID.randomUUID().toString());if(next)putLong(com.mangomusic.playback.MixQueue.NEXT,System.nanoTime()) }).build()).build()
    }
    fun play(track: Track, list: List<Track> = listOf(track)) {
        val p = player ?: return
        if (!available(track)) { error.value = "This song is not saved offline."; return }
        val sequence = list.filter(::available).ifEmpty { listOf(track) }
        error.value = null
        val targetIndex = sequence.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        p.setMediaItems(sequence.map { media(it) }, targetIndex, 0)
        p.prepare()
        p.play()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addAll(sequence)
            } catch (_: Exception) {}
        }
    }
    fun enqueue(track: Track, next: Boolean = false) {
        val p = player ?: return
        if (!available(track)) { error.value = "This song is not saved offline."; return }
        val insertion = if (next) (p.currentMediaItemIndex + 1).coerceAtLeast(0)
        else ((p.currentMediaItemIndex + 1 until p.mediaItemCount).firstOrNull { com.mangomusic.playback.MixQueue.automatic(p.getMediaItemAt(it)) } ?: p.mediaItemCount)
        p.addMediaItem(insertion, media(track, next))
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.add(track)
            } catch (_: Exception) {}
        }
    }
    fun toggle() {
        player?.let {
            if (it.isPlaying || (it.playWhenReady && it.playbackState == Player.STATE_BUFFERING)) {
                it.pause()
            } else {
                error.value = null
                if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
                if (it.playbackState == Player.STATE_IDLE) it.prepare()
                it.play()
            }
        }
    }
    fun retry() {
        val p = player ?: return
        error.value = null
        val id = p.currentMediaItem?.mediaId
        if (id != null && id.startsWith("youtube:")) {
            online.invalidate(id)
        }
        p.prepare()
        p.play()
    }
    fun stop() { player?.stop(); player?.seekTo(0) }
    fun seek(ms: Long) { player?.seekTo(ms) }
    fun previous() {
        player?.let {
            error.value = null
            it.seekToPreviousMediaItem()
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            it.play()
        }
    }
    fun next() {
        player?.let {
            error.value = null
            it.seekToNextMediaItem()
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            it.play()
        }
    }
    fun repeat() { player?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE; Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF } } }
    fun shuffle() { player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun remove(index: Int) { player?.removeMediaItem(index) }
    fun queuePlay(index: Int) {
        player?.let {
            error.value = null
            it.seekToDefaultPosition(index)
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            it.play()
        }
    }
    fun favorite(track: Track) = launch { repository.add(track); repository.dao.toggleFavorite(track.id) }
    fun clearHistory() = launch { repository.dao.clearHistory() }
    fun import(uri: Uri) = launch { busy.value = true; try { repository.import(uri) } finally { busy.value = false } }
    fun addStream(title: String, url: String, downloadable: Boolean) = launch { repository.addStream(title, url, downloadable) }
    fun download(track:Track) {
        if(offline.value) { error.value="Turn off Offline Mode to download.";return }
        downloadConsent.value=track
    }
    fun confirmDownload() { val track=downloadConsent.value ?: return;downloadConsent.value=null;launch { downloads.enqueue(track,true) } }
    fun cancelDownload(track:Track)=launch { downloads.cancel(track.id) }
    fun deleteDownload(track:Track)=launch { downloads.remove(track.id) }

    fun createPlaylist(name: String, description: String = "", initialTrack: Track? = null, onCreated: (Long) -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            error.value = "Playlist name cannot be empty."
            return
        }
        launch {
            val now = System.currentTimeMillis()
            val playlistId = repository.playlistDao.insertPlaylist(
                PlaylistEntity(name = cleanName, description = description.trim(), createdAt = now, updatedAt = now)
            )
            if (initialTrack != null) {
                repository.add(initialTrack)
                repository.playlistDao.insertTrack(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = initialTrack.id,
                        position = 0,
                        addedAt = now,
                        title = initialTrack.title,
                        artist = initialTrack.artist,
                        uri = initialTrack.uri,
                        durationMs = initialTrack.durationMs,
                        downloadable = initialTrack.downloadable,
                        thumbnailUrl = initialTrack.thumbnailUrl
                    )
                )
            }
            onCreated(playlistId)
        }
    }

    fun updatePlaylist(id: Long, name: String, description: String = "") {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            error.value = "Playlist name cannot be empty."
            return
        }
        launch {
            repository.playlistDao.updatePlaylistMeta(id, cleanName, description.trim(), System.currentTimeMillis())
        }
    }

    fun deletePlaylist(id: Long) {
        launch {
            repository.playlistDao.deletePlaylist(id)
            if (selectedPlaylistId.value == id) {
                selectedPlaylistId.value = null
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        launch {
            val exists = repository.playlistDao.isTrackInPlaylist(playlistId, track.id)
            if (exists) {
                onResult(false, "\"${track.title}\" is already in this playlist.")
                return@launch
            }
            val nextPosition = (repository.playlistDao.getMaxPosition(playlistId) ?: -1) + 1
            val now = System.currentTimeMillis()
            repository.add(track)
            repository.playlistDao.insertTrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = nextPosition,
                    addedAt = now,
                    title = track.title,
                    artist = track.artist,
                    uri = track.uri,
                    durationMs = track.durationMs,
                    downloadable = track.downloadable,
                    thumbnailUrl = track.thumbnailUrl
                )
            )
            repository.playlistDao.touchPlaylist(playlistId, now)
            onResult(true, "Added \"${track.title}\" to playlist.")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        launch {
            repository.playlistDao.removeTrack(playlistId, trackId)
            repository.playlistDao.touchPlaylist(playlistId)
        }
    }

    fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        launch {
            val tracks = repository.playlistDao.getTracksForPlaylist(playlistId).toMutableList()
            if (fromIndex in tracks.indices && toIndex in tracks.indices && fromIndex != toIndex) {
                val item = tracks.removeAt(fromIndex)
                tracks.add(toIndex, item)
                tracks.forEachIndexed { idx, t ->
                    repository.playlistDao.updatePosition(playlistId, t.trackId, idx)
                }
                repository.playlistDao.touchPlaylist(playlistId)
            }
        }
    }

    fun playPlaylist(playlistWithTracks: PlaylistWithTracks, shuffle: Boolean = false) {
        val tracks = playlistWithTracks.domainTracks
        if (tracks.isEmpty()) {
            error.value = "This playlist is empty. Add songs to play."
            return
        }
        if (shuffle) {
            val shuffled = tracks.shuffled()
            play(shuffled.first(), shuffled)
        } else {
            play(tracks.first(), tracks)
        }
    }

    override fun onCleared() { player?.removeListener(listener); MediaController.releaseFuture(future); super.onCleared() }
}

data class PlaybackState(val id: String? = null, val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val repeat: Int = 0, val shuffle: Boolean = false,
    val queue: List<String> = emptyList(), val index: Int = -1, val connected: Boolean = false, val failed: Boolean = false, val automaticEntries: Set<Int> = emptySet())
