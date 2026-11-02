package com.mangomusic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangomusic.data.SavedTrack
import com.mangomusic.domain.Track

data class OnlineMixDetailState(
    val mix: OnlineMixItem? = null,
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val lastFetchedAt: Long = 0L,
    val isFromCache: Boolean = false
)

/**
 * Screen 1: Online Mix Overview Screen
 * Displays the 6 curated mix collections.
 */
@Composable
fun OnlineMixOverviewScreen(
    mixes: List<OnlineMixItem>,
    onBack: () -> Unit,
    onSelectMix: (OnlineMixItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "overview-header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Online Mix",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Curated collections across cultures, languages & vibes. Updated daily.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(mixes, key = { it.id }) { mix ->
            OnlineMixOverviewCard(
                mix = mix,
                onClick = { onSelectMix(mix) }
            )
        }

        item(key = "overview-bottom-spacer") {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun OnlineMixOverviewCard(
    mix: OnlineMixItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = mix.gradientColors[0].copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mix Artwork Thumbnail
            Image(
                painter = painterResource(mix.imageResId),
                contentDescription = "${mix.title} artwork",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                contentScale = ContentScale.Crop
            )

            // Content Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = mix.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = mix.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = mix.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(20.dp)
            )
        }
    }
}

/**
 * Screen 2: Individual Mix Screen
 * Dedicated screen for a chosen Mix, following Artist Profile / Mood Discovery design patterns.
 */
@Composable
fun OnlineMixDetailScreen(
    mix: OnlineMixItem,
    state: OnlineMixDetailState,
    saved: List<SavedTrack>,
    playback: PlaybackState,
    offline: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
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
    val heroHeight = (screenHeight * 0.32f).coerceIn(220.dp, 280.dp)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Hero Artwork Banner
        item(key = "mix-hero") {
            OnlineMixHero(
                mix = mix,
                heroHeight = heroHeight,
                trackCount = state.tracks.size,
                offline = offline,
                isRefreshing = state.refreshing,
                onPlayAll = {
                    if (state.tracks.isNotEmpty()) {
                        onPlayTrack(state.tracks.first(), state.tracks)
                    }
                },
                onShuffle = {
                    if (state.tracks.isNotEmpty()) {
                        val shuffled = state.tracks.shuffled()
                        onPlayTrack(shuffled.first(), shuffled)
                    }
                },
                onRefresh = onRefresh
            )
        }

        // 2. Refreshing indicator bar
        if (state.refreshing) {
            item(key = "mix-refreshing-bar") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 3. Offline indicator row when offline
        if (offline) {
            item(key = "mix-offline-indicator") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Offline · Displaying cached tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Section Header for Songs
        item(key = "mix-tracks-header") {
            SectionHeader(
                title = "Mix Tracks",
                subtitle = if (state.refreshing) "Updating in background…" else mix.subtitle,
                actionLabel = if (state.tracks.isNotEmpty()) "${state.tracks.size} tracks" else null,
                topPadding = 16.dp,
                bottomPadding = 8.dp
            )
        }

        // 5. First-time Loading Skeleton
        if (state.loading && state.tracks.isEmpty()) {
            item(key = "mix-loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                    Text(
                        text = "Curating ${mix.title}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 6. Error State (No tracks cached)
        if (state.error != null && !state.loading && state.tracks.isEmpty()) {
            item(key = "mix-error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    FilledTonalButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // 7. Empty State
        if (!state.loading && state.tracks.isEmpty() && state.error == null) {
            item(key = "mix-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No songs available right now",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap retry to fetch fresh tracks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // 8. Track List
        itemsIndexed(state.tracks, key = { index, track -> "$index:${track.id}" }) { index, track ->
            val stored = saved.find { it.id == track.id }
            val isSelected = playback.id == track.id
            val isPlaying = isSelected && playback.playing

            TrackRow(
                track = track,
                saved = stored,
                selected = isSelected,
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

            if (index < state.tracks.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun OnlineMixHero(
    mix: OnlineMixItem,
    heroHeight: Dp,
    trackCount: Int,
    offline: Boolean,
    isRefreshing: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
    ) {
        // Hero Background Artwork Image
        Image(
            painter = painterResource(mix.imageResId),
            contentDescription = "${mix.title} banner",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay for contrast and legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            mix.gradientColors[0].copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Category Pill & Refresh Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFFF5A623),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "ONLINE MIX",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !offline && !isRefreshing,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh mix",
                        tint = if (!offline) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bottom Column: Title, Description, and Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text(
                        text = mix.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = mix.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Buttons: Play All & Shuffle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayAll,
                        enabled = trackCount > 0,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Play All",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onShuffle,
                        enabled = trackCount > 0,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Outlined.Shuffle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Shuffle",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
