package com.mangomusic.ui.prototype

import androidx.compose.ui.graphics.Color

/**
 * Clean mock data models for Mango Music v2.0 UI prototypes.
 * Isolated from production Room entities and network services.
 */
data class PrototypeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val language: String,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val dominantColor: Color = Color(0xFF6B1D2F),
    val secondaryColor: Color = Color(0xFF2C1B4D),
    val plays: Int = 0,
    val isFavorite: Boolean = false
)

data class PrototypeArtist(
    val id: String,
    val name: String,
    val language: String,
    val imageUrl: String? = null,
    val monthlyListeners: String = "2.4M monthly listeners",
    val accentColor: Color = Color(0xFFFF5364)
)

data class PrototypeMixCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val language: String,
    val gradientColors: List<Color>,
    val trackCount: Int = 25
)

object PrototypeData {
    val categories = listOf("All Music", "Punjabi", "Hindi", "English", "Regional")

    val trendingTracks = listOf(
        PrototypeTrack(
            id = "trend-1",
            title = "Kinni Kinni",
            artist = "Diljit Dosanjh",
            language = "Punjabi",
            durationMs = 214000,
            dominantColor = Color(0xFF8B2635),
            secondaryColor = Color(0xFF381528),
            plays = 142
        ),
        PrototypeTrack(
            id = "trend-2",
            title = "Softly",
            artist = "Karan Aujla, Ikky",
            language = "Punjabi",
            durationMs = 175000,
            dominantColor = Color(0xFF683B19),
            secondaryColor = Color(0xFF281E15),
            plays = 98
        ),
        PrototypeTrack(
            id = "trend-3",
            title = "Chaleya",
            artist = "Arijit Singh, Shilpa Rao",
            language = "Hindi",
            durationMs = 200000,
            dominantColor = Color(0xFF1B3B5F),
            secondaryColor = Color(0xFF121B2A),
            plays = 215,
            isFavorite = true
        ),
        PrototypeTrack(
            id = "trend-4",
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            language = "English",
            durationMs = 230000,
            dominantColor = Color(0xFF5B1627),
            secondaryColor = Color(0xFF200E1C),
            plays = 76
        ),
        PrototypeTrack(
            id = "trend-5",
            title = "Tauba Tauba",
            artist = "Karan Aujla",
            language = "Hindi",
            durationMs = 208000,
            dominantColor = Color(0xFF5E2B63),
            secondaryColor = Color(0xFF1B142A),
            plays = 184
        ),
        PrototypeTrack(
            id = "trend-6",
            title = "Illuminati",
            artist = "Sushin Shyam, Dabzee",
            language = "Regional",
            durationMs = 192000,
            dominantColor = Color(0xFF1B4D3E),
            secondaryColor = Color(0xFF0F261F),
            plays = 63
        ),
        PrototypeTrack(
            id = "trend-7",
            title = "Cruel Summer",
            artist = "Taylor Swift",
            language = "English",
            durationMs = 178000,
            dominantColor = Color(0xFF8C3E5A),
            secondaryColor = Color(0xFF2E1929),
            plays = 89
        )
    )

    val featuredArtists = listOf(
        PrototypeArtist("art-1", "Diljit Dosanjh", "Punjabi", accentColor = Color(0xFFFF6F59)),
        PrototypeArtist("art-2", "Arijit Singh", "Hindi", accentColor = Color(0xFF4A90E2)),
        PrototypeArtist("art-3", "Karan Aujla", "Punjabi", accentColor = Color(0xFFE2954A)),
        PrototypeArtist("art-4", "The Weeknd", "English", accentColor = Color(0xFF904AE2)),
        PrototypeArtist("art-5", "AP Dhillon", "Punjabi", accentColor = Color(0xFF4AE2A8)),
        PrototypeArtist("art-6", "Taylor Swift", "English", accentColor = Color(0xFFE24A7A)),
        PrototypeArtist("art-7", "Shubh", "Punjabi", accentColor = Color(0xFFD4AF37)),
        PrototypeArtist("art-8", "Anirudh", "Regional", accentColor = Color(0xFF20B2AA))
    )

    val curatedMixes = listOf(
        PrototypeMixCard(
            id = "mix-1",
            title = "Your Daily Mix",
            subtitle = "Diljit, Karan Aujla, Arijit",
            language = "All Music",
            gradientColors = listOf(Color(0xFF8B1D38), Color(0xFF381528))
        ),
        PrototypeMixCard(
            id = "mix-2",
            title = "Punjabi Heatwave",
            subtitle = "High-energy beats & anthems",
            language = "Punjabi",
            gradientColors = listOf(Color(0xFFB85324), Color(0xFF3A1A0B))
        ),
        PrototypeMixCard(
            id = "mix-3",
            title = "Bollywood Soul",
            subtitle = "Melodic romance & acoustics",
            language = "Hindi",
            gradientColors = listOf(Color(0xFF22577A), Color(0xFF112638))
        ),
        PrototypeMixCard(
            id = "mix-4",
            title = "Midnight English",
            subtitle = "Late night R&B & Synthwave",
            language = "English",
            gradientColors = listOf(Color(0xFF6B2D5C), Color(0xFF220E1E))
        )
    )

    val recentlyPlayed = listOf(
        trendingTracks[0],
        trendingTracks[2],
        trendingTracks[1],
        trendingTracks[4]
    )
}
