package com.mangomusic.online

import com.mangomusic.domain.Track

data class RecommendationBatch(val tracks: List<Track>, val source: String)
fun interface RecommendationProvider {
    suspend fun recommendations(seed: Track, excluded: Set<String>): RecommendationBatch
}
