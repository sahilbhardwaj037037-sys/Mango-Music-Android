package com.mangomusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalConfiguration
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

@Composable
fun MedleyDiscoveryScreen(
    collection: MedleyCollection,
    state: MedleyCollectionState,
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
    val heroHeight = (screenHeight * 0.28f).coerceIn(190.dp, 240.dp)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Collection Hero Banner
        item(key = "medley-hero") {
            MedleyHero(
                collection = collection,
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

        // 2. Section Header
        item(key = "medley-tracks-header") {
            SectionHeader(
                title = "Featured Songs",
                actionLabel = if (state.tracks.isNotEmpty()) "${state.tracks.size} songs" else null,
                topPadding = 16.dp,
                bottomPadding = 8.dp
            )
        }

        // 3. Loading State
        if (state.loading) {
            item(key = "medley-loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (state.tracks.isNotEmpty()) 12.dp else 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                    Text(
                        text = "Discovering ${collection.title}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4. Error State
        if (state.error != null && !state.loading && state.tracks.isEmpty()) {
            item(key = "medley-error") {
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

        // 5. Empty State
        if (!state.loading && state.tracks.isEmpty() && state.error == null) {
            item(key = "medley-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No songs found for ${collection.title}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap retry to load another set of tracks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // 6. Tracks List
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
                    onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } }
                )
            }
            if (index < state.tracks.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 74.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun MedleyHero(
    collection: MedleyCollection,
    heroHeight: Dp,
    trackCount: Int,
    offline: Boolean,
    onPlayAll: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        collection.gradientColors[0],
                        collection.gradientColors[1].copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Decorative ambient glow
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-20).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon & Category Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = collection.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = if (offline) "Offline Available" else "Medley Collection",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Title & Subtitle + Play Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = collection.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = collection.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (trackCount > 0) {
                    FilledTonalButton(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
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
}
