package com.mangomusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mangomusic.data.SavedTrack
import com.mangomusic.domain.Track

@Composable
fun ArtistProfileScreen(
    artist: FeaturedArtist,
    state: ArtistProfileState,
    saved: List<SavedTrack>,
    playback: PlaybackState,
    offline: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onFavorite: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    onNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onCancelDownload: (Track) -> Unit,
    onDeleteDownload: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = (screenHeight * 0.34f).coerceIn(240.dp, 310.dp)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Hero Section (Occupies approximately top 1/3 of the screen)
        item(key = "artist-hero") {
            ArtistHero(
                artist = artist,
                heroHeight = heroHeight,
                trackCount = state.tracks.size,
                offline = offline,
                onPlayAll = {
                    if (state.tracks.isNotEmpty()) {
                        onPlayTrack(state.tracks.first(), state.tracks)
                    }
                }
            )
        }

        // 2. Section Header for Songs
        item(key = "songs-header") {
            SectionHeader(
                title = "Popular Songs",
                actionLabel = if (state.tracks.isNotEmpty()) "${state.tracks.size} songs" else null,
                topPadding = 16.dp,
                bottomPadding = 8.dp
            )
        }

        // 3. Loading State
        if (state.loading) {
            item(key = "artist-loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (state.tracks.isNotEmpty()) 12.dp else 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (state.tracks.isNotEmpty()) "Searching more online songs…" else "Loading songs for ${artist.name}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 4. Error State
        if (!state.loading && state.error != null && state.tracks.isEmpty()) {
            item(key = "artist-error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // 5. Empty State
        if (!state.loading && state.error == null && state.tracks.isEmpty()) {
            item(key = "artist-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No songs found for ${artist.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Check your connection or try another artist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 6. Songs List (Reusing TrackRow)
        itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
            val stored = saved.find { it.id == track.id }
            Box(Modifier.padding(horizontal = 16.dp)) {
                TrackRow(
                    track = track,
                    saved = stored,
                    selected = playback.id == track.id,
                    queued = false,
                    showHistory = false,
                    connected = playback.connected,
                    offline = offline,
                    onPlay = { onPlayTrack(track, state.tracks) },
                    onFavorite = { onFavorite(track) },
                    onQueue = { onQueue(track) },
                    onNext = { onNext(track) },
                    onRemove = {},
                    onDownload = { onDownload(track) },
                    onCancel = { onCancelDownload(track) },
                    onDelete = { onDeleteDownload(track) },
                    automatic = false,
                    onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } }
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ArtistHero(
    artist: FeaturedArtist,
    heroHeight: Dp,
    trackCount: Int,
    offline: Boolean,
    onPlayAll: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        artist.accentColor.copy(alpha = 0.55f),
                        artist.accentColor.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        // Hero Content: Portrait, Name, Category & Action
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Circular Portrait (96dp)
            ArtistHeroPortrait(
                artist = artist,
                size = 96.dp,
                offline = offline
            )

            Spacer(Modifier.height(10.dp))

            // Prominent Artist Name
            Text(
                text = artist.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(4.dp))

            // Category & Track Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = artist.accentColor.copy(alpha = 0.20f)
                ) {
                    Text(
                        text = "Featured · ${artist.category}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (trackCount > 0) {
                    Text(
                        text = "$trackCount songs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (trackCount > 0) {
                Spacer(Modifier.height(10.dp))

                FilledTonalButton(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Play All",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistHeroPortrait(
    artist: FeaturedArtist,
    size: Dp = 96.dp,
    offline: Boolean = false
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
                        artist.accentColor.copy(alpha = 0.40f),
                        artist.accentColor.copy(alpha = 0.85f)
                    )
                )
            )
            .border(
                width = 2.dp,
                color = artist.accentColor.copy(alpha = 0.75f),
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
                    .build(),
                contentDescription = "${artist.name} portrait",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HeroInitials(artist)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HeroInitials(artist)
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
            HeroInitials(artist)
        }
    }
}

@Composable
private fun HeroInitials(artist: FeaturedArtist) {
    val initials = artist.name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
    Text(
        text = initials,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
}
