package com.mangomusic.domain

import java.text.Normalizer
import java.util.Locale

/** Pure selection/order rules; playback and catalog I/O stay outside this policy. */
object MixPolicy {
    private val marks=Regex("\\p{M}+")
    private val punctuation=Regex("[^\\p{L}\\p{N}]+")
    private val spaces=Regex("\\s+")
    private val labels=Regex("\\b(official music video|official video|official audio|lyric video|lyrics video|no copyright music|copyright free music|royalty free music|visualizer|lyrics|hd|4k)\\b")
    private val onlineId=Regex("youtube:[A-Za-z0-9_-]{11}")
    private fun normalize(text:String)=Normalizer.normalize(text.lowercase(Locale.ROOT),Normalizer.Form.NFD)
        .replace(marks,"").replace(punctuation," ").trim().replace(spaces," ")
    private fun withoutCredit(title:String,artist:String)=if(artist.isEmpty())title else title.removePrefix("$artist ").removeSuffix(" $artist").trim()
    private data class Recording(val artist:String,val title:String,val duration:Long) {
        val ownTitle=withoutCredit(title,artist)
        val key="$artist|$ownTitle"
    }
    private fun recording(track:Track)=Recording(normalize(track.artist.removeSuffix(" - Topic")),normalize(track.title).replace(labels,"").trim().replace(spaces," "),track.durationMs)
    fun songKey(track: Track)=recording(track).key
    private fun sameSong(a:Recording,b:Recording):Boolean {
        fun credited(upload:Recording,original:Recording)=original.artist.isNotEmpty() &&
            (" ${upload.title} ").contains(" ${original.artist} ") && withoutCredit(upload.title,original.artist)==original.ownTitle
        return a.key==b.key || credited(a,b) || credited(b,a) ||
            (a.title.isNotEmpty() && a.title==b.title && a.duration>0 && b.duration>0 && kotlin.math.abs(a.duration-b.duration)<=4000)
    }
    fun select(candidates: List<Track>, blocked: List<Track>, excludedIds: Set<String>, limit: Int=3): List<Track> {
        if(limit<=0)return emptyList()
        val ids=(blocked.map { it.id }+excludedIds).toMutableSet()
        val recordings=blocked.map(::recording).toMutableList()
        val result=mutableListOf<Track>()
        for(track in candidates) {
            if(!onlineId.matches(track.id) || track.title.isBlank() || track.id in ids ||
                (track.durationMs!=0L && track.durationMs !in 30000..1200000))continue
            val candidate=recording(track)
            if(recordings.any { sameSong(candidate,it) })continue
            ids+=track.id;recordings+=candidate;result+=track
            if(result.size>=limit)break
        }
        return result
    }
    /** Keep already-traversed shuffle history, then pending user requests, then recommendations. */
    fun prioritize(order: List<Int>, current: Int, automatic: Set<Int>, playNext: Map<Int,Long> = emptyMap()): List<Int> {
        val position=order.indexOf(current)
        if(position<0)return order
        val prefix=order.take(position+1)
        val remaining=order.drop(position+1)
        return prefix+remaining.filter { it !in automatic }.sortedByDescending { playNext[it] ?: 0L }+remaining.filter { it in automatic }
    }
}
