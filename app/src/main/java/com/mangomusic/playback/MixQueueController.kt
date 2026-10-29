package com.mangomusic.playback

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import com.mangomusic.data.LibraryRepository
import com.mangomusic.domain.*
import com.mangomusic.online.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object MixQueue {
    const val AUTO="mango.mix.automatic"
    const val ENTRY="mango.queue.entry"
    fun key(item: MediaItem)=item.mediaMetadata.extras?.getString(ENTRY) ?: item.mediaId
    const val SOURCE="mango.mix.source"
    const val NEXT="mango.queue.playNext"
    fun automatic(item: MediaItem)=item.mediaMetadata.extras?.getBoolean(AUTO)==true
    fun track(item: MediaItem)=Track(item.mediaId,item.mediaMetadata.title?.toString().orEmpty(),
        item.mediaMetadata.artist?.toString().orEmpty(),"https://www.youtube.com/watch?v=${item.mediaId.removePrefix("youtube:")}",thumbnailUrl=item.mediaMetadata.artworkUri?.toString())
    fun media(track: Track, source: String="")=MediaItem.Builder().setMediaId(track.id).setUri(track.uri).setMediaMetadata(
        MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist)
            .setArtworkUri(track.thumbnailUrl?.let(android.net.Uri::parse))
            .setExtras(Bundle().apply { putBoolean(AUTO,true);putString(SOURCE,source);putString(ENTRY,java.util.UUID.randomUUID().toString()) }).build()).build()
}

/** Service-owned, cancellable catalog work. All player mutations occur on its application looper. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MixQueueController(
    private val player: ExoPlayer,
    private val library: LibraryRepository,
    private val provider: RecommendationProvider,
    private val offline: StateFlow<Boolean>,
    private val settings: MixSettings,
    parentScope: CoroutineScope
): Player.Listener {
    private val scope=CoroutineScope(parentScope.coroutineContext+SupervisorJob(parentScope.coroutineContext[Job]))
    private var request: Job?=null
    private var generation=0
    private var seedId: String?=null
    private var attempts=0
    private var nextAttempt=0L
    private val visited=linkedSetOf<String>()
    private var previousKey: String?=null
    private val recent=linkedMapOf<String,Track>()
    private var previousQueueIds=emptySet<String>()
    private val rejected=linkedSetOf<String>()
    private var failedInARow=0
    init {
        player.addListener(this)
        scope.launch {
            combine(settings.enabled,offline,settings.retry) { enabled,off,retry -> Triple(enabled,off,retry) }.collect { (enabled,off,_) ->
                cancelRequest();attempts=0;nextAttempt=0
                if(!enabled || off) {
                    removeUpcomingAutomatic()
                    restoreShuffle()
                    settings.status.value=MixStatus(message=if(off && enabled) "Mix is unavailable offline." else null)
                } else { settings.status.value=if(player.repeatMode!=Player.REPEAT_MODE_OFF) MixStatus(message="Mix resumes when Repeat is off.") else MixStatus();check() }
            }
        }
        scope.launch { while(isActive) { check();delay(5000) } }
    }
    private fun cancelRequest() { generation++;request?.cancel();request=null }
    private fun order(shuffled: Boolean=player.shuffleModeEnabled): List<Int> {
        val timeline=player.currentTimeline
        val result=mutableListOf<Int>()
        var index=timeline.getFirstWindowIndex(shuffled)
        while(index!=C.INDEX_UNSET && result.size<timeline.windowCount) {
            result+=index;index=timeline.getNextWindowIndex(index,Player.REPEAT_MODE_OFF,shuffled)
        }
        return result
    }
    private fun upcoming():List<Int> = order().let { it.drop(it.indexOf(player.currentMediaItemIndex)+1) }
    private fun prioritize() {
        if(!settings.enabled.value || !player.shuffleModeEnabled || player.mediaItemCount==0)return
        val traversal=order()
        val automatic=(0 until player.mediaItemCount).filter { MixQueue.automatic(player.getMediaItemAt(it)) }.toSet()
        val next=(0 until player.mediaItemCount).associateWith { player.getMediaItemAt(it).mediaMetadata.extras?.getLong(MixQueue.NEXT) ?: 0L }
        val position=traversal.indexOf(player.currentMediaItemIndex)
        val unheard=traversal.take(position.coerceAtLeast(0)).filter { MixQueue.key(player.getMediaItemAt(it)) !in visited }
        val normalized=traversal.filter { it !in unheard }+unheard
        val desired=MixPolicy.prioritize(normalized,player.currentMediaItemIndex,automatic,next)
        if(desired!=traversal)player.setShuffleOrder(ExplicitShuffleOrder(desired))
        // Keep insertions at the traversal tail, then partition them on the next timeline event.
        else if(traversal!=lastExplicitOrder)player.setShuffleOrder(ExplicitShuffleOrder(desired))
        lastExplicitOrder=desired
    }
    private var lastExplicitOrder: List<Int>?=null
    private fun restoreShuffle() {
        if(lastExplicitOrder!=null) { lastExplicitOrder=null;player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order(true).toIntArray(),System.nanoTime())) }
    }
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { if(!shuffleModeEnabled)restoreShuffle() }
    private fun removeUpcomingAutomatic() {
        val future=upcoming().toSet()
        (0 until player.mediaItemCount).filter { index -> index!=player.currentMediaItemIndex && MixQueue.automatic(player.getMediaItemAt(index)) &&
            (index in future || MixQueue.key(player.getMediaItemAt(index)) !in visited) }.sortedDescending().forEach(player::removeMediaItem)
    }
    override fun onTimelineChanged(timeline: Timeline,reason: Int) {
        val ids=(0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
        rejected.addAll(previousQueueIds-ids)
        while(rejected.size>100)rejected.remove(rejected.first())
        previousQueueIds=ids
        visited.retainAll((0 until player.mediaItemCount).map { MixQueue.key(player.getMediaItemAt(it)) }.toSet())
    }
    override fun onMediaItemTransition(item: MediaItem?,reason: Int) {
        previousKey?.let { visited+=it }
        previousKey=item?.let(MixQueue::key)
        if(seedId!=item?.mediaId) { cancelRequest();seedId=item?.mediaId;attempts=0;nextAttempt=0;settings.status.value=MixStatus(source=item?.mediaMetadata?.extras?.getString(MixQueue.SOURCE)) }
    }
    override fun onPlaybackStateChanged(playbackState: Int) {
        if(playbackState==Player.STATE_IDLE && player.playerError==null) { cancelRequest();settings.status.value=settings.status.value.copy(loading=false) }
    }
    override fun onEvents(player: Player,events: Player.Events) {
        if(player.isPlaying) {
            failedInARow=0
            player.currentMediaItem?.let { item ->
                visited+=MixQueue.key(item)
                if(item.mediaId.startsWith("youtube:")) {
                    recent[item.mediaId]=MixQueue.track(item)
                    if(recent.size>100)recent.remove(recent.keys.first())
                }
            }
        }
        prioritize();check()
    }
    override fun onRepeatModeChanged(repeatMode: Int) {
        if(repeatMode!=Player.REPEAT_MODE_OFF) { cancelRequest();settings.status.value=MixStatus(message="Mix resumes when Repeat is off.") }
        else { attempts=0;nextAttempt=0;check() }
    }
    override fun onPlayerError(error: PlaybackException) {
        val item=player.currentMediaItem ?: return
        if(!settings.enabled.value || offline.value || !MixQueue.automatic(item) || player.repeatMode!=Player.REPEAT_MODE_OFF)return
        rejected+=item.mediaId
        if(rejected.size>100)rejected.remove(rejected.first())
        failedInARow++
        if(failedInARow>=3) {
            cancelRequest();attempts=3
            settings.status.value=MixStatus(message="Mix songs could not play. Retry Mix or choose a song.",retryable=true)
            return
        }
        scope.launch {
            // Allow the existing bounded transient playback retry to recover first.
            delay(1800)
            if(settings.enabled.value && !offline.value && player.playWhenReady && player.repeatMode==Player.REPEAT_MODE_OFF && player.currentMediaItem?.mediaId==item.mediaId && player.playerError!=null) {
                val next=upcoming().firstOrNull()
                if(next!=null) { player.seekToDefaultPosition(next);player.prepare();player.play() }
                else { attempts=0;nextAttempt=0;check() }
            }
        }
    }
    fun check() {
        if(!settings.enabled.value || offline.value || player.repeatMode!=Player.REPEAT_MODE_OFF || request?.isActive==true || attempts>=3 || SystemClock.elapsedRealtime()<nextAttempt)return
        val item=player.currentMediaItem ?: return
        if(!player.playWhenReady || !item.mediaId.startsWith("youtube:"))return
        if(!player.isPlaying && player.playbackState!=Player.STATE_ENDED && !(MixQueue.automatic(item) && player.playerError!=null))return
        if(upcoming().size>2)return
        val seed=MixQueue.track(item)
        val token=generation
        val id=item.mediaId
        attempts++
        settings.status.value=MixStatus(loading=true)
        request=scope.launch {
            try {
                val saved=library.tracks.first()
                val history=saved.filter { it.lastPlayed>0 }.sortedByDescending { it.lastPlayed }.take(50).map { it.track() }
                val queued=(0 until player.mediaItemCount).map { MixQueue.track(player.getMediaItemAt(it)) }
                val blocked=queued+history+recent.values
                val batch=provider.recommendations(seed,blocked.map { it.id }.toSet()+rejected)
                ensureActive()
                if(token!=generation || !settings.enabled.value || offline.value || player.repeatMode!=Player.REPEAT_MODE_OFF || player.currentMediaItem?.mediaId!=id)return@launch
                // Recheck user additions/removals that happened while catalog work was in flight.
                val latest=(0 until player.mediaItemCount).map { MixQueue.track(player.getMediaItemAt(it)) }
                val blockedNow=latest+history+recent.values.toList()
                val rejectedNow=rejected.toSet()
                val selected=withContext(Dispatchers.Default) { MixPolicy.select(batch.tracks,blockedNow,rejectedNow) }
                if(selected.isEmpty()) {
                    nextAttempt=SystemClock.elapsedRealtime()+30000L*attempts
                    settings.status.value=MixStatus(message="No new Mix songs. Retry or add a song.",source=batch.source,retryable=true)
                } else {
                    selected.forEach { library.add(it) }
                    ensureActive()
                    if(token!=generation || !settings.enabled.value || offline.value || player.currentMediaItem?.mediaId!=id)return@launch
                    val queuedNow=(0 until player.mediaItemCount).map { MixQueue.track(player.getMediaItemAt(it)) }
                    val accepted=MixPolicy.select(selected,queuedNow+history+recent.values,rejected)
                    if(accepted.isEmpty()) { nextAttempt=SystemClock.elapsedRealtime()+30000;settings.status.value=MixStatus(source=batch.source);return@launch }
                    val resume=player.playWhenReady && (player.playbackState==Player.STATE_ENDED || MixQueue.automatic(item) && player.playerError!=null)
                    player.addMediaItems(accepted.map { MixQueue.media(it,batch.source) })
                    prioritize()
                    if(resume)upcoming().firstOrNull()?.let { player.seekToDefaultPosition(it);player.prepare();player.play() }
                    // Retain a useful previous-playback window without unbounded auto history in RAM.
                    if(player.mediaItemCount>80) (0 until player.currentMediaItemIndex-25).filter { MixQueue.automatic(player.getMediaItemAt(it)) }
                        .sortedDescending().forEach(player::removeMediaItem)
                    attempts=0;nextAttempt=SystemClock.elapsedRealtime()+10000
                    settings.status.value=MixStatus(source=batch.source)
                }
            } catch(e: TimeoutCancellationException) {
                nextAttempt=SystemClock.elapsedRealtime()+30000L*attempts
                settings.status.value=MixStatus(message="Mix timed out. Your queue is unchanged.",retryable=true)
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) {
                nextAttempt=SystemClock.elapsedRealtime()+30000L*attempts
                settings.status.value=MixStatus(message="Mix could not find songs. Your queue is unchanged.",retryable=true)
            } finally { if(token==generation && settings.status.value.loading)settings.status.value=settings.status.value.copy(loading=false) }
        }
    }
    fun release() { cancelRequest();player.removeListener(this);scope.cancel();settings.status.value=MixStatus() }
}
