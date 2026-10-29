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
import com.mangomusic.data.SavedTrack
import com.mangomusic.domain.*
import com.mangomusic.playback.PlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
    val onlineSearch = MutableStateFlow(com.mangomusic.online.OnlineSearchState())
    private var searchJob: Job? = null
    val saved = repository.tracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
        launch { repository.seed() }
        future.addListener({
            try { player = future.get().also { it.addListener(listener) }; refresh() }
            catch (e: Exception) { error.value = "Could not connect to playback. Reopen Mango Music." }
        }, androidx.core.content.ContextCompat.getMainExecutor(application))
        viewModelScope.launch { while (isActive) { refresh(); delay(500) } }
        viewModelScope.launch { while (isActive) { try { repository.refreshDownloads(saved.value); downloads.reconcile(saved.value) } catch (e: Exception) { error.value = "Could not check downloads: ${e.localizedMessage}" }; delay(2000) } }
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
        if (value) { launch { downloads.pauseAll() }; searchJob?.cancel(); onlineSearch.value = com.mangomusic.online.OnlineSearchState() }
        prefs.edit().putBoolean("offline", value).apply()
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
        launch {
            sequence.forEach { repository.add(it) }
            p.setMediaItems(sequence.map { media(it) }, sequence.indexOfFirst { it.id == track.id }.coerceAtLeast(0), 0)
            p.prepare(); p.play()
        }
    }
    fun enqueue(track: Track, next: Boolean = false) {
        val p = player ?: return
        if (!available(track)) { error.value = "This song is not saved offline."; return }
        launch { repository.add(track); val insertion=if(next) (p.currentMediaItemIndex+1).coerceAtLeast(0) else
            ((p.currentMediaItemIndex+1 until p.mediaItemCount).firstOrNull { com.mangomusic.playback.MixQueue.automatic(p.getMediaItemAt(it)) } ?: p.mediaItemCount)
            p.addMediaItem(insertion,media(track,next)) }
    }
    fun toggle() { player?.let { if (it.isPlaying || it.playWhenReady && it.playbackState == Player.STATE_BUFFERING) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition(); if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun retry() { error.value = null; player?.prepare(); player?.play() }
    fun stop() { player?.stop(); player?.seekTo(0) }
    fun seek(ms: Long) { player?.seekTo(ms) }
    fun previous() { player?.seekToPreviousMediaItem() }
    fun next() { player?.seekToNextMediaItem() }
    fun repeat() { player?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE; Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF } } }
    fun shuffle() { player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun remove(index: Int) { player?.removeMediaItem(index) }
    fun queuePlay(index: Int) { player?.seekToDefaultPosition(index); player?.prepare(); player?.play() }
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
    override fun onCleared() { player?.removeListener(listener); MediaController.releaseFuture(future); super.onCleared() }
}

data class PlaybackState(val id: String? = null, val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val repeat: Int = 0, val shuffle: Boolean = false,
    val queue: List<String> = emptyList(), val index: Int = -1, val connected: Boolean = false, val failed: Boolean = false, val automaticEntries: Set<Int> = emptySet())
