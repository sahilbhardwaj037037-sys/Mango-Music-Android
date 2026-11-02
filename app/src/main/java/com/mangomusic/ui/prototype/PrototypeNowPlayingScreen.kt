package com.mangomusic.ui.prototype

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangomusic.R
import com.mangomusic.domain.DiscMotion
import com.mangomusic.domain.timeLabel
import kotlinx.coroutines.isActive

/**
 * Screen B: Now Playing Prototype
 * 
 * Key Principles:
 * 1. NON-NEGOTIABLE: Rotating vinyl remains the central hero element!
 * 2. Circular song artwork centered inside the rotating vinyl (falling back to Mango logo).
 * 3. Dynamic ambient background inspired by Apple Music's soft diffused lighting,
 *    blending artwork colors into Mango Music's dark/light surfaces without hurting text readability.
 * 4. Settings toggle for Dynamic Background ON vs OFF.
 */
@Composable
fun PrototypeNowPlayingScreen(
    track: PrototypeTrack = PrototypeData.trendingTracks[0],
    dynamicBackgroundEnabled: Boolean = true,
    onToggleDynamicBackground: () -> Unit = {},
    onClose: () -> Unit = {},
    onOpenQueue: () -> Unit = {}
) {
    var isPlaying by rememberSaveable { mutableStateOf(true) }
    var isFavorite by rememberSaveable { mutableStateOf(track.isFavorite) }
    var shuffleOn by rememberSaveable { mutableStateOf(false) }
    var repeatMode by rememberSaveable { mutableIntStateOf(0) } // 0: Off, 1: One, 2: All
    var positionMs by rememberSaveable { mutableLongStateOf(72000L) }
    var seekingPos by remember { mutableStateOf<Float?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val discAngle = rememberSaveable { mutableFloatStateOf(0f) }

    // Smooth vinyl rotation loop
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var previous = 0L
            while (isActive) {
                withInfiniteAnimationFrameNanos { now ->
                    if (previous != 0L) {
                        discAngle.floatValue = DiscMotion.advance(
                            discAngle.floatValue,
                            (now - previous) / 1_000_000f,
                            true
                        )
                    }
                    previous = now
                }
            }
        }
    }

    // Dynamic background color transitions
    val targetAmbientPrimary = if (dynamicBackgroundEnabled) track.dominantColor else MaterialTheme.colorScheme.background
    val targetAmbientSecondary = if (dynamicBackgroundEnabled) track.secondaryColor else MaterialTheme.colorScheme.background
    val animatedPrimary by animateColorAsState(targetAmbientPrimary, animationSpec = tween(650), label = "ambientPrimary")
    val animatedSecondary by animateColorAsState(targetAmbientSecondary, animationSpec = tween(650), label = "ambientSecondary")

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Dynamic Ambient Background Layer
        if (dynamicBackgroundEnabled) {
            AmbientBackgroundMesh(
                primaryColor = animatedPrimary,
                secondaryColor = animatedSecondary,
                backgroundColor = MaterialTheme.colorScheme.background,
                isDark = isDark
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val compact = maxHeight < 660.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with original Mango Music controls + Three-Dot Menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Close Now Playing",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Minimal Ambient Background Toggle Chip
                    FilterChip(
                        selected = dynamicBackgroundEnabled,
                        onClick = onToggleDynamicBackground,
                        label = {
                            Text(
                                if (dynamicBackgroundEnabled) "Ambient: ON" else "Ambient: OFF",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (dynamicBackgroundEnabled) Icons.Outlined.AutoAwesome else Icons.Outlined.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(Modifier.width(4.dp))

                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            Icons.AutoMirrored.Outlined.QueueMusic,
                            contentDescription = "Open Up Next",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Original Top-Right Three-Dot Menu
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "More options for ${track.title}",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play next") },
                                onClick = { menuExpanded = false },
                                leadingIcon = { Icon(Icons.Outlined.PlaylistPlay, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Up Next") },
                                onClick = { menuExpanded = false },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Save offline") },
                                onClick = { menuExpanded = false },
                                leadingIcon = { Icon(Icons.Outlined.DownloadForOffline, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (dynamicBackgroundEnabled) "Turn ambient glow off" else "Turn ambient glow on") },
                                onClick = {
                                    onToggleDynamicBackground()
                                    menuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }
                            )
                        }
                    }
                }

                if (compact) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(0.7f))

                // HERO ROTATING VINYL WITH EMBEDDED CIRCULAR ARTWORK
                PrototypeVinyl(
                    angle = discAngle,
                    track = track,
                    modifier = Modifier.size(if (compact) 200.dp else 256.dp)
                )

                if (compact) Spacer(Modifier.height(16.dp)) else Spacer(Modifier.weight(0.9f))

                // Track Title & Artist (Clean, compact, Apple Music-inspired typography)
                Text(
                    text = track.title,
                    fontSize = if (compact) 19.sp else 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(3.dp))

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Progress Bar / Slider
                val currentSliderValue = seekingPos ?: positionMs.toFloat().coerceIn(0f, track.durationMs.toFloat().coerceAtLeast(1f))
                Slider(
                    value = currentSliderValue,
                    onValueChange = { seekingPos = it },
                    onValueChangeFinished = {
                        seekingPos?.let { positionMs = it.toLong() }
                        seekingPos = null
                    },
                    valueRange = 0f..track.durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Playback position" }
                )

                // Time Labels
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = timeLabel(seekingPos?.toLong() ?: positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = timeLabel(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Primary Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = { shuffleOn = !shuffleOn }) {
                        Icon(
                            imageVector = Icons.Outlined.Shuffle,
                            contentDescription = if (shuffleOn) "Shuffle On" else "Shuffle Off",
                            tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = { positionMs = 0L },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous track",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Play / Pause Circle
                    FilledIconButton(
                        onClick = { isPlaying = !isPlaying },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Next
                    IconButton(
                        onClick = { positionMs = 0L },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next track",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Repeat
                    IconButton(onClick = { repeatMode = (repeatMode + 1) % 3 }) {
                        Icon(
                            imageVector = if (repeatMode == 1) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                            contentDescription = when (repeatMode) {
                                1 -> "Repeat One"
                                2 -> "Repeat All"
                                else -> "Repeat Off"
                            },
                            tint = if (repeatMode != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Secondary Actions Row (Stop, Favorite, Download)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { isPlaying = false; positionMs = 0L }) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }

                    Spacer(Modifier.width(12.dp))

                    TextButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isFavorite) "Saved" else "Favorite")
                    }

                    Spacer(Modifier.width(12.dp))

                    TextButton(onClick = { }) {
                        Icon(Icons.Outlined.DownloadForOffline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }
                }

                if (compact) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(0.4f))
            }
        }
    }
}

/**
 * Ambient background mesh derived from song artwork colors.
 * Optimized with pure static multi-stop gradient washes: zero expensive runtime blurs.
 */
@Composable
private fun AmbientBackgroundMesh(
    primaryColor: Color,
    secondaryColor: Color,
    backgroundColor: Color,
    isDark: Boolean
) {
    val primaryAlpha = if (isDark) 0.20f else 0.10f
    val secondaryAlpha = if (isDark) 0.13f else 0.06f

    Box(modifier = Modifier.fillMaxSize()) {
        // Top-left radial aura
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = primaryAlpha), Color.Transparent),
                        center = Offset(x = 250f, y = 280f),
                        radius = 850f
                    )
                )
        )

        // Mid-right radial aura
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(secondaryColor.copy(alpha = secondaryAlpha), Color.Transparent),
                        center = Offset(x = 750f, y = 550f),
                        radius = 700f
                    )
                )
        )

        // Soft vertical wash that transitions into base theme color at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.45f to backgroundColor.copy(alpha = 0.5f),
                            0.75f to backgroundColor.copy(alpha = 0.92f),
                            1.0f to backgroundColor
                        )
                    )
                )
        )
    }
}

/**
 * Enhanced Rotating Vinyl with Circular Center Artwork.
 * Keeps Mango Music's authentic grooved vinyl disc, and cleanly embeds
 * the current track's artwork in the circular center label.
 */
@Composable
internal fun PrototypeVinyl(
    angle: MutableFloatState,
    track: PrototypeTrack,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .semantics { contentDescription = "Mango Music rotating vinyl disc" }
            .graphicsLayer { rotationZ = angle.floatValue },
        contentAlignment = Alignment.Center
    ) {
        val isSmall = maxWidth < 250.dp
        val centerArtworkSize = if (isSmall) 68.dp else 90.dp

        // Authentic Vinyl Grooves & Specular Sheen
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            // Disc base
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF343438), Color(0xFF09090B), Color(0xFF29292D)),
                    Offset.Zero,
                    Offset(size.width, size.height)
                ),
                radius = radius
            )
            // Concentric sound grooves
            for (i in 0..20) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.035f),
                    radius = radius * (0.42f + i * 0.025f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f)
                )
            }
            // Specular reflection arc
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = 225f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = Offset(radius * 0.10f, radius * 0.10f),
                size = androidx.compose.ui.geometry.Size(radius * 1.8f, radius * 1.8f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(radius * 0.10f)
            )
            // Center label outer ring border
            drawCircle(Color(0xFF242427), radius * 0.36f)
            drawCircle(Color.Black.copy(alpha = 0.6f), radius * 0.35f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }

        // CIRCULAR CENTER ARTWORK (Rotates with Vinyl)
        Box(
            modifier = Modifier
                .size(centerArtworkSize)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(track.dominantColor, track.secondaryColor))
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Track Art Mock Graphic
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(centerArtworkSize * 0.45f)
            )

            // Center spindle hole
            Box(
                modifier = Modifier
                    .size(if (isSmall) 10.dp else 12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF09090B))
                    .border(1.dp, Color(0xFF323238), CircleShape)
            )
        }
    }
}
