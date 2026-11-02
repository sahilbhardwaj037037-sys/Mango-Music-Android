package com.mangomusic.ui.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangomusic.R

/**
 * Screen A: Discover Home Prototype
 * Apple Music-inspired elegance + Mango Music original identity.
 * Isolated Jetpack Compose prototype supporting New User and Returning User states.
 */
@Composable
fun DiscoverHomeScreen(
    isReturningUser: Boolean = true,
    onToggleUserState: () -> Unit = {},
    onTrackSelect: (PrototypeTrack) -> Unit = {},
    onOpenNowPlaying: () -> Unit = {}
) {
    var selectedCategory by rememberSaveable { mutableStateOf("All Music") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val currentTrack = PrototypeData.trendingTracks[0]

    val filteredTrending = remember(selectedCategory, searchQuery) {
        PrototypeData.trendingTracks.filter { track ->
            (selectedCategory == "All Music" || track.language.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() || "${track.title} ${track.artist}".contains(searchQuery, ignoreCase = true))
        }
    }

    val filteredArtists = remember(selectedCategory) {
        if (selectedCategory == "All Music") PrototypeData.featuredArtists
        else PrototypeData.featuredArtists.filter { it.language.equals(selectedCategory, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp) // Room for mini player
        ) {
            // 1. Header Bar with Mango Branding & Prototype State Controls
            item(key = "header") {
                DiscoverHeader(
                    isReturningUser = isReturningUser,
                    onToggleUserState = onToggleUserState
                )
            }

            // 2. Prominent Search Field
            item(key = "search") {
                DiscoverSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
            }

            // 3. Music Categories Horizontal Carousel
            item(key = "categories") {
                CategoryChipsRow(
                    categories = PrototypeData.categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            // 4. FEATURED ARTISTS (Moved above Trending Now for calm, editorial browse hierarchy)
            item(key = "artists") {
                SectionHeader(
                    title = "Featured Artists"
                )
                FeaturedArtistsRow(
                    artists = filteredArtists,
                    onArtistClick = { }
                )
            }

            // 5. TRENDING NOW (Clean artwork focus, no decorative badges)
            item(key = "trending") {
                SectionHeader(
                    title = "Trending Now",
                    actionLabel = "See All"
                )
                if (filteredTrending.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No trending tracks match your filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    TrendingNowRow(
                        tracks = filteredTrending,
                        onTrackClick = onTrackSelect
                    )
                }
            }

            // 6. RETURNING USER: "Made For You" & "Recently Played"
            if (isReturningUser && searchQuery.isBlank()) {
                item(key = "made-for-you") {
                    SectionHeader(
                        title = "Made For You"
                    )
                    MadeForYouRow(
                        mixes = PrototypeData.curatedMixes,
                        onMixClick = { onTrackSelect(currentTrack) }
                    )
                }

                item(key = "recently-played") {
                    SectionHeader(
                        title = "Recently Played"
                    )
                    RecentlyPlayedRow(
                        tracks = PrototypeData.recentlyPlayed,
                        onTrackClick = onTrackSelect
                    )
                }
            }

            // 7. NEW USER ONLY: Regional Discovery & Explore Cards
            if (!isReturningUser && searchQuery.isBlank()) {
                item(key = "explore-regional") {
                    SectionHeader(
                        title = "Explore Language Hits"
                    )
                    MadeForYouRow(
                        mixes = PrototypeData.curatedMixes.drop(1),
                        onMixClick = { onTrackSelect(currentTrack) }
                    )
                }
            }

            // Bottom Spacing
            item(key = "bottom-space") {
                Spacer(Modifier.height(16.dp))
            }
        }

        // Persistent Mini Player at Bottom
        PrototypeMiniPlayer(
            track = currentTrack,
            modifier = Modifier.align(Alignment.BottomCenter),
            onOpenNowPlaying = onOpenNowPlaying
        )
    }
}

@Composable
private fun DiscoverHeader(
    isReturningUser: Boolean,
    onToggleUserState: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.mango_logo),
            contentDescription = "Mango Music logo",
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Mango Music",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))

        // State Toggle Pill (Prototype control for reviewing New vs Returning user experience)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .clickable(onClick = onToggleUserState)
                .semantics { contentDescription = "Toggle user state between New and Returning user" }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isReturningUser) Icons.Outlined.Person else Icons.Outlined.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isReturningUser) "Returning User" else "New User",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DiscoverSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        singleLine = true,
        placeholder = {
            Text(
                "Search songs, artists, or genres…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun CategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(category) },
                label = {
                    Text(
                        category,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun TrendingNowRow(
    tracks: List<PrototypeTrack>,
    onTrackClick: (PrototypeTrack) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            TrendingTrackCard(track = track, onClick = { onTrackClick(track) })
        }
    }
}

@Composable
private fun TrendingTrackCard(
    track: PrototypeTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick)
    ) {
        // Pure Artwork Container: uncluttered, calm, no decorative badges or overlay buttons
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(track.dominantColor, track.secondaryColor)
                    )
                )
        ) {
            // Subtle specular depth sheen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.28f))
                        )
                    )
            )

            // Centered Music Glyph
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.Center)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Title
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(1.dp))

        // Artist
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeaturedArtistsRow(
    artists: List<PrototypeArtist>,
    onArtistClick: (PrototypeArtist) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(artists, key = { it.id }) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clickable { onArtistClick(artist) }
            ) {
                // Circular Portrait Container: compact and balanced
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(artist.accentColor.copy(alpha = 0.85f), artist.accentColor.copy(alpha = 0.35f), artist.accentColor.copy(alpha = 0.85f))
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = artist.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(5.dp))

                // Artist name only (repetitive subtitles removed per minimal Apple Music aesthetic)
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun MadeForYouRow(
    mixes: List<PrototypeMixCard>,
    onMixClick: (PrototypeMixCard) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(mixes, key = { it.id }) { mix ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .width(160.dp)
                    .height(84.dp)
                    .clickable { onMixClick(mix) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(mix.gradientColors)
                        )
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = mix.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = mix.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play ${mix.title}",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedRow(
    tracks: List<PrototypeTrack>,
    onTrackClick: (PrototypeTrack) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .width(200.dp)
                    .clickable { onTrackClick(track) }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(listOf(track.dominantColor, track.secondaryColor))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrototypeMiniPlayer(
    track: PrototypeTrack,
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .clickable(onClick = onOpenNowPlaying)
            .semantics { contentDescription = "Open Now Playing" }
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini Artwork
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(track.dominantColor, track.secondaryColor))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Album,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next track",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
