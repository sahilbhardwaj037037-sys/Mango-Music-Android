package com.mangomusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mangomusic.data.PlaylistEntity
import com.mangomusic.data.PlaylistWithTracks
import com.mangomusic.data.SavedTrack
import com.mangomusic.domain.Track

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistWithTracks>,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onEditPlaylist: (PlaylistEntity) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            FilledTonalButton(
                onClick = onCreatePlaylist,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("New playlist", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (playlists.isEmpty()) {
            EmptyPlaylistsState(onCreatePlaylist = onCreatePlaylist)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize().testTag("playlists-grid")
            ) {
                items(playlists, key = { it.playlist.id }) { item ->
                    PlaylistCard(
                        playlistWithTracks = item,
                        onClick = { onOpenPlaylist(item.playlist.id) },
                        onEdit = { onEditPlaylist(item.playlist) },
                        onDelete = { onDeletePlaylist(item.playlist) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlistWithTracks: PlaylistWithTracks,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val playlist = playlistWithTracks.playlist
    val trackCount = playlistWithTracks.tracks.size

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("playlist-card-${playlist.id}"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Artwork / Collage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                PlaylistArtworkCollage(
                    tracks = playlistWithTracks.domainTracks,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Title & Overflow Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$trackCount ${if (trackCount == 1) "song" else "songs"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Playlist options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit playlist") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (playlist.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = playlist.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PlaylistArtworkCollage(
    tracks: List<Track>,
    modifier: Modifier = Modifier
) {
    val thumbs = remember(tracks) {
        tracks.mapNotNull { it.thumbnailUrl }.filter { it.isNotBlank() }.distinct().take(4)
    }

    when {
        thumbs.size >= 4 -> {
            // 2x2 collage
            Column(modifier = modifier) {
                Row(modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = thumbs[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    AsyncImage(
                        model = thumbs[1],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = thumbs[2],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    AsyncImage(
                        model = thumbs[3],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
        thumbs.isNotEmpty() -> {
            // Single primary artwork
            AsyncImage(
                model = thumbs[0],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        else -> {
            // Clean gradient placeholder
            Box(
                modifier = modifier.background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaylistsState(
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "No playlists yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Create your own playlists to organize your favorite songs and listen anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.85f)
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onCreatePlaylist,
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create playlist")
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistWithTracks: PlaylistWithTracks,
    saved: List<SavedTrack>,
    playback: PlaybackState,
    offline: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onFavorite: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    onNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onEditPlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onDownload: (Track) -> Unit,
    onCancelDownload: (Track) -> Unit,
    onDeleteDownload: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlist = playlistWithTracks.playlist
    val tracks = playlistWithTracks.tracksInOrder
    val domainTracks = playlistWithTracks.domainTracks
    var menuOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Header
        item(key = "playlist-hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Artwork / Collage (140dp)
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    PlaylistArtworkCollage(
                        tracks = domainTracks,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Playlist Name
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Optional Description
                if (playlist.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Song count
                Text(
                    text = "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                // Actions: Play All, Shuffle, Overflow Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayAll,
                        enabled = tracks.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all")
                    }

                    Spacer(Modifier.width(10.dp))

                    OutlinedButton(
                        onClick = onShuffle,
                        enabled = tracks.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }

                    Spacer(Modifier.width(8.dp))

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, "More playlist options")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit playlist") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = {
                                    menuOpen = false
                                    onEditPlaylist()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    onDeletePlaylist()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Section divider / title
        item(key = "songs-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Songs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (tracks.isEmpty()) {
            item(key = "empty-playlist") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "This playlist is empty",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Browse songs on Discover Home or Search, tap the 3-dot menu on any song, and choose 'Add to playlist'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(
                items = tracks,
                key = { _, item -> "${item.playlistId}_${item.trackId}" }
            ) { index, item ->
                val track = item.toTrack()
                val stored = saved.find { it.id == track.id }
                val isSelected = playback.id == track.id

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TrackRow(
                        track = track,
                        saved = stored,
                        selected = isSelected,
                        queued = false,
                        showHistory = false,
                        connected = playback.connected,
                        offline = offline,
                        onPlay = { onPlayTrack(track, domainTracks) },
                        onFavorite = { onFavorite(track) },
                        onQueue = { onQueue(track) },
                        onNext = { onNext(track) },
                        onRemove = {},
                        onDownload = { onDownload(track) },
                        onCancel = { onCancelDownload(track) },
                        onDelete = { onDeleteDownload(track) },
                        automatic = false,
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onRemoveFromPlaylist = { onRemoveTrack(track.id) },
                        onMoveUp = if (index > 0) { { onMoveTrack(index, index - 1) } } else null,
                        onMoveDown = if (index < tracks.size - 1) { { onMoveTrack(index, index + 1) } } else null
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 40.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateOrEditPlaylistDialog(
    initialName: String = "",
    initialDescription: String = "",
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var showError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Playlist" else "New Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(100)
                        if (it.isNotBlank()) showError = false
                    },
                    label = { Text("Playlist name") },
                    placeholder = { Text("My Favorites, Road Trip...") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) { { Text("Name is required") } } else null,
                    modifier = Modifier.fillMaxWidth().testTag("playlist-name-input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(300) },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Give your playlist a vibe...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("playlist-desc-input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isBlank()) {
                        showError = true
                    } else {
                        onConfirm(name.trim(), description.trim())
                    }
                },
                modifier = Modifier.testTag("playlist-confirm-button")
            ) {
                Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist?") },
        text = { Text("Are you sure you want to delete \"$playlistName\"? The songs in your library will not be deleted.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    playlists: List<PlaylistWithTracks>,
    onDismiss: () -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onSelectPlaylist: (playlistId: Long) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add to playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${track.title} · ${track.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "Close")
                }
            }

            Spacer(Modifier.height(14.dp))

            // Create New Playlist Shortcut
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onCreateNewPlaylist)
                    .testTag("sheet-create-playlist"),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "New playlist",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = "No existing playlists. Tap 'New playlist' above to start one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    itemsIndexed(playlists, key = { _, it -> it.playlist.id }) { _, item ->
                        val inThisPlaylist = item.tracks.any { it.trackId == track.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectPlaylist(item.playlist.id) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                PlaylistArtworkCollage(
                                    tracks = item.domainTracks,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.playlist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.tracks.size} ${if (item.tracks.size == 1) "song" else "songs"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (inThisPlaylist) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Already in playlist",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Added",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
