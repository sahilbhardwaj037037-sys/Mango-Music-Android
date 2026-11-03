package com.mangomusic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mangomusic.R
import com.mangomusic.data.SavedTrack
import com.mangomusic.domain.Track
import kotlin.math.abs

data class FeaturedArtist(
    val id: String,
    val name: String,
    val category: String,
    val accentColor: Color,
    val imageUrl: Any? = null
)

data class CuratedMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val gradientColors: List<Color>,
    val onPlay: () -> Unit,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val artworkResId: Int? = null,
    val artworkUrl: String? = null
)

data class MedleyCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val gradientColors: List<Color>,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val queries: List<String>
)

data class OnlineMixItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val imageResId: Int,
    val gradientColors: List<Color>,
    val queries: List<String>
)

data class MoodItem(
    val id: String,
    val name: String,
    val gradientColors: List<Color>,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val artworkResId: Int? = null
)

object DiscoverCatalog {
    val categories = listOf("All Music", "Punjabi", "Hindi", "English", "Regional")

    val moods = listOf(
        MoodItem("mood-chill", "Chill", listOf(Color(0xFF004D40), Color(0xFF00695C)), Icons.Outlined.Spa, R.drawable.mix_chill),
        MoodItem("mood-romantic", "Romantic", listOf(Color(0xFF880E4F), Color(0xFFAD1457)), Icons.Outlined.FavoriteBorder, R.drawable.mix_romantic),
        MoodItem("mood-happy", "Happy", listOf(Color(0xFFE65100), Color(0xFFEF6C00)), Icons.Outlined.WbSunny, R.drawable.mix_punjabi),
        MoodItem("mood-sad", "Sad", listOf(Color(0xFF1A237E), Color(0xFF283593)), Icons.Outlined.WaterDrop, R.drawable.mix_hindi),
        MoodItem("mood-workout", "Workout", listOf(Color(0xFFB71C1C), Color(0xFFC62828)), Icons.Outlined.FitnessCenter, R.drawable.mix_trending),
        MoodItem("mood-focus", "Focus", listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)), Icons.Outlined.SelfImprovement, R.drawable.mix_chill),
        MoodItem("mood-funk", "Funk", listOf(Color(0xFF6A1B9A), Color(0xFF8E24AA)), Icons.Outlined.GraphicEq, R.drawable.mix_english)
    )

    val moodQueries: Map<String, List<String>> = mapOf(
        "mood-chill" to listOf(
            "Chill music mix",
            "Punjabi chill songs",
            "Hindi chill songs",
            "English chill music",
            "relaxing acoustic songs"
        ),
        "mood-romantic" to listOf(
            "Romantic songs mix",
            "Bollywood romantic songs",
            "Punjabi romantic songs",
            "Hindi love songs",
            "English romantic hits"
        ),
        "mood-happy" to listOf(
            "Happy songs mix",
            "Bollywood happy songs",
            "Punjabi feel-good songs",
            "Hindi upbeat songs",
            "English feel-good hits"
        ),
        "mood-sad" to listOf(
            "Sad songs mix",
            "Bollywood sad songs",
            "Punjabi emotional songs",
            "Hindi heartbreak songs",
            "English sad music"
        ),
        "mood-workout" to listOf(
            "Gym workout songs mix",
            "Punjabi gym songs",
            "Bollywood workout songs",
            "Hindi workout music",
            "high-energy rap and hip-hop"
        ),
        "mood-focus" to listOf(
            "Focus music mix",
            "lo-fi beats",
            "instrumental focus music",
            "ambient music",
            "piano concentration music"
        ),
        "mood-funk" to listOf(
            "Funk music mix",
            "popular funk songs",
            "modern funk music",
            "upbeat funk tracks",
            "Brazilian funk music"
        )
    )

    private val moodIndices = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    fun nextMoodQuery(moodId: String, fallbackMoodName: String = ""): String {
        val list = moodQueries[moodId] ?: return if (fallbackMoodName.isNotBlank()) "$fallbackMoodName songs" else "chill music tracks"
        val counter = moodIndices.computeIfAbsent(moodId) { java.util.concurrent.atomic.AtomicInteger(0) }
        val idx = counter.getAndIncrement().mod(list.size)
        return list[idx]
    }

    fun moodSubtitle(moodId: String): String = when (moodId) {
        "mood-chill" -> "Mellow vibes, acoustic & ambient tunes"
        "mood-romantic" -> "Love songs, soulful melodies & romantic hits"
        "mood-happy" -> "Uplifting beats & feel-good anthems"
        "mood-sad" -> "Melancholic tracks & deep emotional melodies"
        "mood-workout" -> "High-octane gym motivation & energetic beats"
        "mood-focus" -> "Lo-fi beats, ambient soundscapes & study tunes"
        "mood-funk" -> "Groovy basslines, upbeat rhythms & modern funk"
        else -> "Curated mood tracks and music mix"
    }

    val featuredArtists = listOf(
        // Punjabi Artists
        FeaturedArtist("artist-diljit", "Diljit Dosanjh", "Punjabi", Color(0xFF8B2635), R.drawable.artist_diljit),
        FeaturedArtist("artist-aujla", "Karan Aujla", "Punjabi", Color(0xFFD48B38), R.drawable.artist_aujla),
        FeaturedArtist("artist-sidhu", "Sidhu Moose Wala", "Punjabi", Color(0xFFB71C1C), R.drawable.artist_sidhu),
        FeaturedArtist("artist-ap-dhillon", "AP Dhillon", "Punjabi", Color(0xFF303F9F), R.drawable.artist_ap_dhillon),
        FeaturedArtist("artist-shubh", "Shubh", "Punjabi", Color(0xFF00796B), R.drawable.artist_shubh),
        FeaturedArtist("artist-ammy", "Ammy Virk", "Punjabi", Color(0xFFE65100), R.drawable.artist_ammy),
        FeaturedArtist("artist-gurdas", "Gurdas Maan", "Punjabi", Color(0xFF5D4037), R.drawable.artist_gurdas),

        // Hindi / Bollywood Artists
        FeaturedArtist("artist-arijit", "Arijit Singh", "Hindi", Color(0xFF2B5B84), R.drawable.artist_arijit),
        FeaturedArtist("artist-atif", "Atif Aslam", "Hindi", Color(0xFF1E88E5), R.drawable.artist_atif),
        FeaturedArtist("artist-shreya", "Shreya Ghoshal", "Hindi", Color(0xFFAD1457), R.drawable.artist_shreya),
        FeaturedArtist("artist-sonu", "Sonu Nigam", "Hindi", Color(0xFF00897B), R.drawable.artist_sonu),
        FeaturedArtist("artist-neha", "Neha Kakkar", "Hindi", Color(0xFFC2185B), R.drawable.artist_neha),
        FeaturedArtist("artist-kishore", "Kishore Kumar", "Hindi", Color(0xFF6D4C41), R.drawable.artist_kishore),
        FeaturedArtist("artist-lata", "Lata Mangeshkar", "Hindi", Color(0xFF5E35B1), R.drawable.artist_lata),
        FeaturedArtist("artist-mohit", "Mohit Chauhan", "Hindi", Color(0xFF43A047), R.drawable.artist_mohit),

        // English Artists
        FeaturedArtist("artist-weeknd", "The Weeknd", "English", Color(0xFF9C27B0), R.drawable.artist_weeknd),
        FeaturedArtist("artist-taylor", "Taylor Swift", "English", Color(0xFFC2185B), R.drawable.artist_taylor),
        FeaturedArtist("artist-drake", "Drake", "English", Color(0xFF37474F), R.drawable.artist_drake),
        FeaturedArtist("artist-edsheeran", "Ed Sheeran", "English", Color(0xFFEF6C00), R.drawable.artist_edsheeran),
        FeaturedArtist("artist-billie", "Billie Eilish", "English", Color(0xFF2E7D32), R.drawable.artist_billie),
        FeaturedArtist("artist-postmalone", "Post Malone", "English", Color(0xFF455A64), R.drawable.artist_postmalone),
        FeaturedArtist("artist-dualipa", "Dua Lipa", "English", Color(0xFF880E4F), R.drawable.artist_dualipa),

        // Regional Artists
        FeaturedArtist("artist-sushin", "Sushin Shyam", "Regional", Color(0xFF00897B), R.drawable.artist_sushin),
        FeaturedArtist("artist-anirudh", "Anirudh Ravichander", "Regional", Color(0xFFD84315), R.drawable.artist_anirudh)
    )

    val onlineMixes = listOf(
        OnlineMixItem(
            id = "mix-punjabi",
            title = "Punjabi Hits Mix",
            subtitle = "High-energy Punjabi chartbusters",
            description = "Latest and most popular Punjabi hit tracks & bhangra beats",
            imageResId = R.drawable.mix_punjabi,
            gradientColors = listOf(Color(0xFFE65100), Color(0xFFFF8F00)),
            queries = listOf("Punjabi hit songs official", "Punjabi top hit music songs")
        ),
        OnlineMixItem(
            id = "mix-hindi",
            title = "Hindi Hits Mix",
            subtitle = "Bollywood chart toppers & melodies",
            description = "Current Bollywood favorites, melodic hits and cinema chartbusters",
            imageResId = R.drawable.mix_hindi,
            gradientColors = listOf(Color(0xFF880E4F), Color(0xFFC2185B)),
            queries = listOf("Hindi Bollywood hit songs official audio", "top hindi bollywood music songs")
        ),
        OnlineMixItem(
            id = "mix-english",
            title = "English Hits Mix",
            subtitle = "Global pop & chart-topping anthems",
            description = "International pop hits, viral singles and top billboard tracks",
            imageResId = R.drawable.mix_english,
            gradientColors = listOf(Color(0xFF0D47A1), Color(0xFF1976D2)),
            queries = listOf("English pop hits official music audio", "international top pop music songs")
        ),
        OnlineMixItem(
            id = "mix-chill",
            title = "Chill Mix",
            subtitle = "Mellow beats & acoustic serenity",
            description = "Relaxing chill music, acoustic sessions and peaceful ambient melodies",
            imageResId = R.drawable.mix_chill,
            gradientColors = listOf(Color(0xFF004D40), Color(0xFF00796B)),
            queries = listOf("relaxing chill music official", "chill acoustic music songs")
        ),
        OnlineMixItem(
            id = "mix-romantic",
            title = "Romantic Mix",
            subtitle = "Soulful love ballads & duets",
            description = "Heartfelt romantic songs, timeless love duets and acoustic ballads",
            imageResId = R.drawable.mix_romantic,
            gradientColors = listOf(Color(0xFF4A148C), Color(0xFF7B1FA2)),
            queries = listOf("romantic songs official audio", "best romantic love music songs")
        ),
        OnlineMixItem(
            id = "mix-trending",
            title = "Trending Mix",
            subtitle = "Viral sounds & fresh global drops",
            description = "Currently trending music tracks and viral releases right now",
            imageResId = R.drawable.mix_trending,
            gradientColors = listOf(Color(0xFFBF360C), Color(0xFFD84315)),
            queries = listOf("currently trending music official songs", "trending viral hit music songs")
        )
    )

    val medleyCollections = listOf(
        MedleyCollection(
            id = "col-new-releases",
            title = "New Releases",
            subtitle = "Fresh official music drops",
            description = "Discover the newest official song releases and trending singles",
            gradientColors = listOf(Color(0xFF0D47A1), Color(0xFF1976D2)),
            icon = Icons.Outlined.NewReleases,
            queries = listOf(
                "latest official music video songs 2026",
                "new release songs official audio",
                "latest hit songs official release",
                "new hindi punjabi english songs official audio"
            )
        ),
        MedleyCollection(
            id = "col-classic-bollywood",
            title = "Classic Bollywood",
            subtitle = "Golden era evergreen hits",
            description = "Timeless retro melodies and iconic Bollywood classics",
            gradientColors = listOf(Color(0xFF4A148C), Color(0xFF7B1FA2)),
            icon = Icons.Outlined.Radio,
            queries = listOf(
                "classic bollywood songs evergreen hits",
                "retro bollywood songs audio",
                "old hindi songs evergreen melody",
                "golden era bollywood classic songs"
            )
        ),
        MedleyCollection(
            id = "col-popular-songs",
            title = "Most Popular Songs",
            subtitle = "Top hits & billion-view songs",
            description = "The world's most played music and record-breaking hits",
            gradientColors = listOf(Color(0xFFBF360C), Color(0xFFF4511E)),
            icon = Icons.Outlined.TrendingUp,
            queries = listOf(
                "all time most viewed songs official music video",
                "billion views popular songs official",
                "top 100 most popular songs of all time",
                "all time greatest hit songs official"
            )
        )
    )

    private val collectionIndices = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    fun nextCollectionQuery(id: String, queries: List<String>): String {
        if (queries.isEmpty()) return "popular music songs"
        val counter = collectionIndices.computeIfAbsent(id) { java.util.concurrent.atomic.AtomicInteger(0) }
        val idx = counter.getAndIncrement().mod(queries.size)
        return queries[idx]
    }

    fun trackGradient(id: String): List<Color> {
        val palettes = listOf(
            listOf(Color(0xFF6B1D2F), Color(0xFF2C1B4D)),
            listOf(Color(0xFF1B3B5F), Color(0xFF121B2A)),
            listOf(Color(0xFF683B19), Color(0xFF281E15)),
            listOf(Color(0xFF5E2B63), Color(0xFF1B142A)),
            listOf(Color(0xFF1B4931), Color(0xFF0F261A)),
            listOf(Color(0xFF5C3317), Color(0xFF2E190B))
        )
        return palettes[abs(id.hashCode()) % palettes.size]
    }
}

// Discover Home YouTube Music-inspired visual styling tokens (strictly local to DiscoverHome)
private val isHomeDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.background == MangoDarkColors.background

private val HomeSurfaceCard: Color
    @Composable get() = if (isHomeDark) Color(0xFF19191C) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

private val HomeSurfaceElevated: Color
    @Composable get() = if (isHomeDark) Color(0xFF222226) else MaterialTheme.colorScheme.surfaceContainerHigh

private val HomeChipBg: Color
    @Composable get() = if (isHomeDark) Color(0xFF212124) else Color(0xFFECECEF)

private val HomeChipSelected: Color
    @Composable get() = if (isHomeDark) Color(0xFFF5F5F7) else Color(0xFF1D1D20)

private val HomeChipSelectedText: Color
    @Composable get() = if (isHomeDark) Color(0xFF121214) else Color(0xFFFFFFFF)

private val HomeTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

private val HomeTextMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private val HomeBorderSubtle: Color
    @Composable get() = if (isHomeDark) Color(0xFF2E2E33) else Color(0xFFE2E2E8)

private val HomeGold: Color = Color(0xFFE5B842)

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    topPadding: Dp = 24.dp,
    bottomPadding: Dp = 12.dp,
    onActionClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                letterSpacing = (-0.3).sp,
                color = HomeTextPrimary
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = HomeTextMuted
                )
            }
        }
        if (actionLabel != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = HomeChipBg,
                border = BorderStroke(0.5.dp, HomeBorderSubtle),
                onClick = onActionClick
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = HomeTextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) HomeChipSelected else HomeChipBg,
                border = BorderStroke(0.5.dp, if (isSelected) Color.Transparent else HomeBorderSubtle),
                onClick = { onSelectCategory(category) }
            ) {
                Text(
                    text = category,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isSelected) HomeChipSelectedText else HomeTextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun FeaturedArtistsRow(
    artists: List<FeaturedArtist>,
    offline: Boolean = false,
    onArtistClick: (FeaturedArtist) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(artists, key = { it.id }) { artist ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = HomeSurfaceCard,
                border = BorderStroke(0.5.dp, HomeBorderSubtle),
                modifier = Modifier
                    .width(124.dp)
                    .height(176.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onArtistClick(artist) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    ArtistPortraitCircle(
                        artist = artist,
                        offline = offline,
                        size = 88.dp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = HomeTextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${artist.category} • Artist",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        color = HomeTextMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistPortraitCircle(
    artist: FeaturedArtist,
    offline: Boolean,
    size: Dp = 80.dp
) {
    val shouldLoad = artist.imageUrl != null && !offline

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(
                    listOf(
                        artist.accentColor.copy(alpha = 0.85f),
                        artist.accentColor.copy(alpha = 0.35f),
                        artist.accentColor.copy(alpha = 0.85f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = HomeBorderSubtle,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (shouldLoad) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artist.imageUrl)
                    .crossfade(true)
                    .setHeader("User-Agent", "MangoMusic/2.0 (Android; open-source)")
                    .listener(
                        onError = { _, result ->
                            android.util.Log.e("MangoCoil", "Failed to load ${artist.name}: ${result.throwable}")
                        }
                    )
                    .build(),
                contentDescription = "${artist.name} portrait",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ArtistInitials(artist)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ArtistInitials(artist)
                    }
                },
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            )
        } else {
            ArtistInitials(artist)
        }
    }
}

@Composable
private fun ArtistInitials(artist: FeaturedArtist) {
    val initials = artist.name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
    Text(
        text = initials,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
}

@Composable
fun BrowseByMoodRow(
    moods: List<MoodItem> = DiscoverCatalog.moods,
    onMoodClick: (MoodItem) -> Unit = {}
) {
    val pairedMoods = remember(moods) { moods.chunked(2) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pairedMoods) { pair ->
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { mood ->
                    MoodCard(mood = mood, onClick = { onMoodClick(mood) })
                }
            }
        }
    }
}

@Composable
fun MoodCard(
    mood: MoodItem,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = HomeSurfaceCard,
        border = BorderStroke(0.5.dp, HomeBorderSubtle),
        modifier = Modifier
            .width(160.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            mood.gradientColors[0].copy(alpha = 0.30f),
                            Color(0xFF141417).copy(alpha = 0.90f)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Genuine artwork thumbnail
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(mood.gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mood.artworkResId != null) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(mood.artworkResId),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = mood.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mood.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = HomeTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = DiscoverCatalog.moodSubtitle(mood.id),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        color = HomeTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingGridPager(
    tracks: List<Track>,
    saved: List<SavedTrack>,
    currentTrackId: String?,
    connected: Boolean,
    offline: Boolean,
    onTrackClick: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    onNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onCancel: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayTracks = remember(tracks) { tracks.take(8) }
    val pages = remember(displayTracks) {
        if (displayTracks.isEmpty()) emptyList()
        else displayTracks.chunked(4)
    }

    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val pageTracks = pages[pageIndex]
            val rows = remember(pageTracks) { pageTracks.chunked(2) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { rowTracks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowTracks.forEach { track ->
                            val stored = saved.find { it.id == track.id }
                            Box(modifier = Modifier.weight(1f)) {
                                TrendingTrackGridCell(
                                    track = track,
                                    saved = stored,
                                    selected = currentTrackId == track.id,
                                    connected = connected,
                                    offline = offline,
                                    onClick = { onTrackClick(track) },
                                    onFavorite = { onFavorite(track) },
                                    onQueue = { onQueue(track) },
                                    onNext = { onNext(track) },
                                    onDownload = { onDownload(track) },
                                    onCancel = { onCancel(track) },
                                    onDelete = { onDelete(track) },
                                    onAddToPlaylist = onAddToPlaylist?.let { { it(track) } }
                                )
                            }
                        }
                        if (rowTracks.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Subtle Page Indicator (active pill, inactive dot)
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (isActive) 16.dp else 6.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else HomeTextMuted.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingTrackGridCell(
    track: Track,
    saved: SavedTrack?,
    selected: Boolean,
    connected: Boolean,
    offline: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onQueue: () -> Unit,
    onNext: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }

    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Remove saved audio?") },
            text = { Text("This removes the downloaded copy of ${track.title}. Favorites and listening history are kept.") },
            confirmButton = {
                TextButton(onClick = { delete = false; onDelete() }) {
                    Text("Remove download")
                }
            },
            dismissButton = {
                TextButton(onClick = { delete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) HomeSurfaceElevated else HomeSurfaceCard,
        border = BorderStroke(
            width = 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else HomeBorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = connected, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track artwork thumbnail (48x48dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(DiscoverCatalog.trackGradient(track.id))),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUrl != null && (!offline || track.thumbnailUrl.startsWith("file:"))) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("online-thumbnail"),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.mango_logo),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Track title & artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary else HomeTextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = HomeTextMuted
                )
            }

            // Options menu button
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More for ${track.title}",
                        modifier = Modifier.size(18.dp),
                        tint = HomeTextMuted
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Play next") }, onClick = { onNext(); menu = false })
                    DropdownMenuItem(text = { Text("Add to Up Next") }, onClick = { onQueue(); menu = false })
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = { onAddToPlaylist(); menu = false },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (saved?.favorite == true) "Remove favorite" else "Favorite") },
                        onClick = { onFavorite(); menu = false },
                        leadingIcon = { Icon(if (saved?.favorite == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null) }
                    )
                    if (!offline && com.mangomusic.downloads.DownloadPolicy.eligible(track) && saved?.localUri == null && (saved?.downloadId ?: 0L) == 0L && saved?.downloadState !in com.mangomusic.downloads.DownloadPolicy.active) {
                        DropdownMenuItem(
                            text = { Text(if (saved?.downloadState in listOf("FAILED", "PAUSED", "CANCELLED", "MISSING")) "Retry download" else "Download") },
                            onClick = { onDownload(); menu = false }
                        )
                    }
                    if (saved?.downloadState in com.mangomusic.downloads.DownloadPolicy.active) {
                        DropdownMenuItem(text = { Text("Cancel download") }, onClick = { onCancel(); menu = false })
                    }
                    if (saved?.localUri != null) {
                        DropdownMenuItem(text = { Text("Remove download") }, onClick = { delete = true; menu = false })
                    }
                }
            }
        }
    }
}

@Composable
fun PopularPicksRow(
    tracks: List<Track>,
    saved: List<SavedTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    offline: Boolean,
    onTrackClick: (Track) -> Unit,
    onFavorite: (Track) -> Unit
) {
    val columns = remember(tracks) {
        if (tracks.size >= 4) tracks.chunked(4) else tracks.chunked(maxOf(1, tracks.size))
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(columns) { columnTracks ->
            Column(
                modifier = Modifier.width(310.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                columnTracks.forEach { track ->
                    val stored = saved.find { it.id == track.id }
                    val isFavorite = stored?.favorite == true
                    val isSelected = currentTrackId == track.id
                    QuickPickTrackRow(
                        track = track,
                        isFavorite = isFavorite,
                        isSelected = isSelected,
                        isPlaying = isPlaying && isSelected,
                        offline = offline,
                        onClick = { onTrackClick(track) },
                        onFavorite = { onFavorite(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickPickTrackRow(
    track: Track,
    isFavorite: Boolean,
    isSelected: Boolean,
    isPlaying: Boolean,
    offline: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) HomeSurfaceElevated else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(DiscoverCatalog.trackGradient(track.id))),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUrl != null && (!offline || track.thumbnailUrl.startsWith("file:"))) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.mango_logo),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (isPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = HomeGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) HomeGold else HomeTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = HomeTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Unfavorite ${track.title}" else "Favorite ${track.title}",
                    tint = if (isFavorite) Color(0xFFFF5364) else HomeTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun PopularTrackCard(
    track: Track,
    isFavorite: Boolean,
    isSelected: Boolean,
    isPlaying: Boolean,
    offline: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, HomeBorderSubtle, RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(DiscoverCatalog.trackGradient(track.id))
                )
        ) {
            if (track.thumbnailUrl != null && (!offline || track.thumbnailUrl.startsWith("file:"))) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.28f)
                                )
                            )
                        )
                )
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.GraphicEq else Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.Center)
                )
            }

            // Quick favorite action directly on the card
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            ) {
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(32.dp).align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Unfavorite ${track.title}" else "Favorite ${track.title}",
                        tint = if (isFavorite) Color(0xFFFF5364) else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) HomeGold else HomeTextPrimary
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = HomeTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MadeForYouRow(
    mixes: List<CuratedMix>
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(mixes, key = { it.id }) { mix ->
            val bgTint = remember(mix.id) {
                when (mix.id) {
                    "medley-offline" -> Color(0xFF0F2420)
                    "col-new-releases" -> Color(0xFF121F33)
                    "col-classic-bollywood" -> Color(0xFF261528)
                    else -> Color(0xFF2B1710)
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = HomeSurfaceCard,
                border = BorderStroke(0.5.dp, HomeBorderSubtle),
                modifier = Modifier
                    .width(240.dp)
                    .height(244.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = mix.onPlay)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    bgTint,
                                    Color(0xFF141417)
                                )
                            )
                        )
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Section: Large Cover Artwork Square (matching Screenshot 1)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(mix.gradientColors)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mix.artworkUrl != null) {
                                AsyncImage(
                                    model = mix.artworkUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (mix.artworkResId != null) {
                                Image(
                                    painter = androidx.compose.ui.res.painterResource(mix.artworkResId),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = mix.icon ?: Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        // Middle Section: Title, Subtitle and Round Play Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mix.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = mix.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.95f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play ${mix.title}",
                                    tint = Color(0xFF141416),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Bottom Section: Highlight Badge Row
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = HomeChipBg,
                            border = BorderStroke(0.5.dp, HomeBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = when (mix.id) {
                                        "medley-offline" -> "Downloaded tracks ready offline"
                                        "col-new-releases" -> "Latest 2026 singles & releases"
                                        "col-classic-bollywood" -> "Evergreen retro classics & hits"
                                        else -> "Top global chartbusters"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = HomeTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    tint = HomeGold,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentlyPlayedList(
    tracks: List<Track>,
    saved: List<SavedTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    offline: Boolean,
    onTrackClick: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        val displayTracks = tracks.take(8)
        displayTracks.forEachIndexed { index, track ->
            val stored = saved.find { it.id == track.id }
            val isSelected = currentTrackId == track.id
            RecentlyPlayedTrackRow(
                track = track,
                stored = stored,
                isSelected = isSelected,
                isPlaying = isPlaying && isSelected,
                offline = offline,
                onPlay = { onTrackClick(track) },
                onFavorite = { onFavorite(track) },
                onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } }
            )
            if (index < displayTracks.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 74.dp, end = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
fun RecentlyPlayedTrackRow(
    track: Track,
    stored: SavedTrack?,
    isSelected: Boolean,
    isPlaying: Boolean,
    offline: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) HomeSurfaceElevated else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, HomeBorderSubtle, RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(DiscoverCatalog.trackGradient(track.id))),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUrl != null && (!offline || track.thumbnailUrl.startsWith("file:"))) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (isPlaying) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = "Playing",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) HomeGold else HomeTextPrimary
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = if (stored != null && stored.playCount > 0) {
                    "${track.artist} · ${stored.playCount} ${if (stored.playCount == 1) "play" else "plays"}"
                } else {
                    track.artist
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onFavorite) {
                val isFav = stored?.favorite == true
                Icon(
                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFav) "Unfavorite ${track.title}" else "Favorite ${track.title}",
                    tint = if (isFav) Color(0xFFFF5364) else HomeTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (onAddToPlaylist != null) {
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More options for ${track.title}",
                            tint = HomeTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = {
                                menuOpen = false
                                onAddToPlaylist()
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreMusicSection(
    onOpenSearchOnline: () -> Unit,
    onOpenMyLibrary: () -> Unit,
    onOpenOnlineMix: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        SectionHeader(
            title = "Get Started",
            subtitle = "Explore channels & audio collections",
            topPadding = 20.dp,
            bottomPadding = 12.dp
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ExploreCard(
                    title = "Online Mix",
                    subtitle = "6 curated mixes & chart hits",
                    badge = "6 MIXES",
                    icon = Icons.Outlined.GraphicEq,
                    accentColor = HomeGold,
                    artworkResId = R.drawable.mix_punjabi,
                    onClick = onOpenOnlineMix
                )
            }

            item {
                ExploreCard(
                    title = "Search Online",
                    subtitle = "Stream millions on YouTube",
                    badge = "ONLINE",
                    icon = Icons.Outlined.TravelExplore,
                    accentColor = Color(0xFF2E7D32),
                    artworkResId = R.drawable.mix_trending,
                    onClick = onOpenSearchOnline
                )
            }

            item {
                ExploreCard(
                    title = "My Library",
                    subtitle = "Saved & downloaded music",
                    badge = "OFFLINE",
                    icon = Icons.Outlined.LibraryMusic,
                    accentColor = Color(0xFF1976D2),
                    artworkResId = R.drawable.mix_chill,
                    onClick = onOpenMyLibrary
                )
            }
        }
    }
}

@Composable
fun ExploreCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    artworkResId: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = HomeSurfaceCard,
        border = BorderStroke(0.5.dp, HomeBorderSubtle),
        modifier = Modifier
            .width(160.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Upper visual area with genuine artwork thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(artworkResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient scrim at bottom and dark tint for contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.65f),
                                    Color(0xFF141417)
                                )
                            )
                        )
                )

                // Top row with badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Frosted round icon container at bottom-left of artwork
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = 6.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                        .align(Alignment.BottomStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Lower text area with dark background
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF161619))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExploreCompactCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color = Color(0xFFE5B842),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = HomeSurfaceCard,
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 0.5.dp,
                color = HomeBorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(0.5.dp, accentColor.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = HomeTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun OnlineMixCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HomeSurfaceCard,
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = HomeGold.copy(alpha = 0.28f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF201B14),
                            Color(0xFF181614),
                            Color(0xFF1E1A14)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HomeGold.copy(alpha = 0.16f))
                        .border(1.dp, HomeGold.copy(alpha = 0.32f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = HomeGold,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeTextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = HomeGold.copy(alpha = 0.14f),
                            border = BorderStroke(0.5.dp, HomeGold.copy(alpha = 0.30f))
                        ) {
                            Text(
                                text = "6 MIXES",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HomeGold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = HomeTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Open Online Mix",
                    tint = HomeGold.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

