package com.mangomusic.domain

import java.util.Locale

data class Track(val id: String, val title: String, val artist: String, val uri: String, val durationMs: Long = 0, val downloadable: Boolean = false, val thumbnailUrl: String? = null)

object Catalog {
    val samples = listOf(
        Track("mango-dawn", "Mango Dawn", "Mango Studio · original demo", "asset:///mango_dawn.wav", 24000),
        Track("mango-orbit", "Pink Orbit", "Mango Studio · original demo", "asset:///pink_orbit.wav", 24000),
        Track("mango-dusk", "After the Rain", "Mango Studio · original demo", "asset:///after_rain.wav", 24000)
    )
    fun search(tracks: List<Track>, query: String): List<Track> {
        val term = query.trim()
        require(term.length <= 200 && term.none { it.isISOControl() }) { "Use up to 200 characters without control characters." }
        return tracks.filter { "${it.title} ${it.artist}".contains(term, ignoreCase = true) }.distinctBy { it.id }
    }
}

/** Frame-independent motion, kept separate from composition for deterministic testing. */
object DiscMotion {
    fun advance(angle: Float, elapsedMs: Float, playing: Boolean): Float =
        if (playing) (angle + elapsedMs.coerceIn(0f, 64f) * 0.018f) % 360f else angle
}

fun timeLabel(ms: Long): String = (ms.coerceAtLeast(0) / 1000).let { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) }
