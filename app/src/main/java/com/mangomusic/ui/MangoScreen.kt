package com.mangomusic.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.mangomusic.R
import com.mangomusic.data.SavedTrack
import com.mangomusic.domain.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val destinations = listOf("Search", "Up Next", "Favorites", "History", "Downloads")
private val navIcons = listOf(
    Icons.Outlined.Search, Icons.AutoMirrored.Outlined.QueueMusic,
    Icons.Outlined.FavoriteBorder, Icons.Outlined.History, Icons.Outlined.DownloadForOffline
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangoScreen(vm: MusicViewModel) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val onlineResults by vm.onlineSearch.collectAsStateWithLifecycle()
    var onlineSelected by rememberSaveable { mutableStateOf(false) }
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf("Search") }
    var query by rememberSaveable { mutableStateOf("") }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var addLink by rememberSaveable { mutableStateOf(false) }
    var clearHistory by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(query, onlineSelected, offline, destination) {
        vm.searchOnline(query, onlineSelected && !offline && destination == "Search")
    }
    val discAngle = rememberSaveable { mutableFloatStateOf(0f) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screenState = rememberSaveableStateHolder()
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    val all = saved.map { it.track() }
    val current = all.find { it.id == playback.id }
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    val view = LocalView.current
    LaunchedEffect(view, dark, settings, addLink, clearHistory) {
        // Apply after dialog disposal so a closing sheet cannot restore stale system-bar contrast.
        withFrameNanos { }
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val consent by vm.downloadConsent.collectAsStateWithLifecycle()
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    MaterialTheme(colorScheme = if (dark) MangoDarkColors else MangoLightColors) {
        if(consent!=null) AlertDialog(onDismissRequest={ vm.downloadConsent.value=null },
            title={ Text("Save audio offline?") },
            text={ Text("Download only if the source and content owner permit saving this audio. Public availability alone is not permission. Mango Music cannot bypass DRM, sign-in, access controls or source restrictions.") },
            confirmButton={ TextButton(onClick={ vm.confirmDownload();if(android.os.Build.VERSION.SDK_INT>=33)notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Download") } },
            dismissButton={ TextButton(onClick={ vm.downloadConsent.value=null }) { Text("Cancel") } })
        BackHandler(nowPlaying && !drawer.isOpen) { nowPlaying = false }
        BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = !nowPlaying || drawer.isOpen,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.widthIn(max = 300.dp).testTag("navigation-drawer"),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerTonalElevation = 0.dp
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                        Brand(Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
                        destinations.forEachIndexed { index, label ->
                            NavigationDrawerItem(
                                label = { Text(label) },
                                icon = { Icon(navIcons[index], null, Modifier.size(21.dp)) },
                                selected = !settings && destination == label,
                                onClick = {
                                    destination = label
                                    nowPlaying = false
                                    scope.launch { drawer.close() }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.padding(vertical = 2.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationDrawerItem(
                            label = { Text("Settings") },
                            icon = { Icon(Icons.Outlined.Settings, null, Modifier.size(21.dp)) },
                            selected = settings,
                            onClick = { scope.launch { drawer.close(); settings = true } },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    val wide = maxWidth >= 840.dp
                    Scaffold(
                        containerColor = Color.Transparent,
                        topBar = {
                            if (!nowPlaying || wide) Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Open navigation menu") }
                                Brand(Modifier.weight(1f).padding(start = 8.dp))
                                IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, "Settings") }
                            }
                        },
                        bottomBar = {
                            if (current != null && !nowPlaying && !wide) MiniPlayer(current, playback, vm) { nowPlaying = true }
                        }
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            if (error != null) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(error!!, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    if (playback.failed) TextButton(onClick = vm::retry) { Text("Retry") }
                                    IconButton(onClick = { vm.error.value = null }) { Icon(Icons.Outlined.Close, "Dismiss error") }
                                }
                            }
                            if (busy || !playback.connected) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Row(Modifier.weight(1f)) {
                                if (!nowPlaying || wide) Box(Modifier.weight(1f).fillMaxHeight().testTag("library-content")) {
                                    screenState.SaveableStateProvider(destination) {
                                        LibraryScreen(
                                            destination, query, { query = it }, saved, all, playback, offline, vm,
                                            { import.launch(arrayOf("audio/*")) }, { addLink = true },
                                            { settings = true }, { clearHistory = true }, onlineSelected, { onlineSelected = it }, onlineResults
                                        )
                                    }
                                }
                                if (nowPlaying || (wide && current != null)) {
                                    if (wide) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Box(Modifier.then(if (wide) Modifier.width(360.dp) else Modifier.weight(1f)).fillMaxHeight()) {
                                        NowPlaying(current, playback, vm, discAngle, saved.any { it.id == playback.id && it.favorite }, wide,
                                            { nowPlaying = false }, { destination = "Up Next"; nowPlaying = false })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (settings) ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = { settings = false }) { Icon(Icons.Outlined.Close, "Close Settings") }
                }
                Spacer(Modifier.height(20.dp))
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEach { label ->
                        FilterChip(selected = theme == label, onClick = { vm.appearance(label) }, label = { Text(label) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Offline Mode", style = MaterialTheme.typography.bodyMedium)
                        Text("Play only saved audio and demos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(offline, onCheckedChange = vm::offline, modifier = Modifier.semantics { contentDescription = "Offline Mode" })
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Mango Music · V1", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text("Online search streams permitted public audio. Online music is experimental; restricted content cannot be played or saved. Download only with source and content-owner permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (addLink) AddLinkDialog({ addLink = false }) { title, url, allowed -> vm.addStream(title, url, allowed); addLink = false }
        if (clearHistory) AlertDialog(
            onDismissRequest = { clearHistory = false }, title = { Text("Clear listening history?") },
            text = { Text("This removes your listening history and play counts.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); clearHistory = false }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { clearHistory = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.mango_logo), "Mango Music logo", Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)))
        Spacer(Modifier.width(10.dp))
        Text("Mango Music", fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryScreen(
    destination: String, query: String, onQuery: (String) -> Unit, saved: List<SavedTrack>, all: List<Track>,
    playback: PlaybackState, offline: Boolean, vm: MusicViewModel,
    import: () -> Unit, addLink: () -> Unit, settings: () -> Unit, clearHistory: () -> Unit,
    onlineSelected: Boolean, selectOnline: (Boolean) -> Unit, onlineState: com.mangomusic.online.OnlineSearchState
) {
    val visible = when (destination) {
        "Search" -> if (onlineSelected && !offline) onlineState.tracks else Catalog.search(all.filter { !it.id.startsWith("youtube:") && vm.available(it) }, query)
        "Favorites" -> saved.filter { it.favorite }.map { it.track() }
        "History" -> saved.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.map { it.track() }
        "Downloads" -> saved.filter { it.localUri != null || it.uri.startsWith("file:") || it.uri.startsWith("asset:") || it.downloadId > 0 || it.downloadError != null || it.downloadState != "NONE" }.map { it.track() }
        else -> playback.queue.mapNotNull { id -> all.find { it.id == id } }
    }
    // Each destination owns its saved list position; the header scrolls with its content on small screens.
    LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
        item(key = "header") {
            if (destination == "Search") {
                OutlinedTextField(
                    value = query, onValueChange = { onQuery(it.filterNot { char -> char.isISOControl() }.take(200)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(if (onlineSelected && !offline) "Search online music" else "Search your library") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(21.dp)) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Clear search") } },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !onlineSelected || offline, onClick = { selectOnline(false) }, label = { Text("Library") })
                    FilterChip(selected = onlineSelected && !offline, onClick = { selectOnline(true) }, enabled = !offline, label = { Text("Online") })
                    if(onlineSelected) MixToggle(vm,offline)
                }
                // FlowRow keeps both 48dp actions accessible at narrow widths / large font scales.
                if (!onlineSelected || offline) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = import) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Import audio") }
                    TextButton(onClick = addLink, enabled = !offline) { Icon(Icons.Outlined.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add audio link") }
                }
            } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(destination, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (destination == "History" && visible.isNotEmpty()) TextButton(onClick = clearHistory) { Text("Clear") }
                if (destination == "Favorites" && visible.isNotEmpty()) TextButton(onClick = { vm.play(visible.first(), visible) }) { Text("Play all") }
            }
            if(destination=="Downloads") {
                val used=saved.sumOf { it.downloadedBytes.takeIf { _ -> it.localUri!=null } ?: 0L }
                Text("${used/(1024*1024)} MB saved · ${androidx.compose.ui.platform.LocalContext.current.filesDir.usableSpace/(1024*1024)} MB available",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(destination=="Up Next") {
                MixToggle(vm,offline)
                MixStatusLine(vm)
            }
            if (offline) TextButton(onClick = settings, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Outlined.OfflineBolt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Offline Mode", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (destination == "Search" && onlineSelected && !offline) {
            if (onlineState.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Searching…", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium) }
            else if (onlineState.error != null) item(key = "online-error") {
                Text(onlineState.error, Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.searchOnline(query, true) }) { Text("Retry search") }
            }
            else if (visible.isEmpty()) item(key = "online-empty") {
                Text(if (query.isBlank()) "Search by title, artist or keywords." else "No online results. Try another search.", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (visible.isEmpty()) item(key = "empty") { EmptyState(destination, query) }
        itemsIndexed(visible, key = { index, track -> if (destination == "Up Next") "$index:${track.id}" else track.id }) { index, track ->
            val stored = saved.find { it.id == track.id }
            TrackRow(track, stored, playback.id == track.id, destination == "Up Next", destination == "History", playback.connected, offline,
                onPlay = { if (destination == "Up Next") vm.queuePlay(index) else vm.play(track, if(destination=="Search" && onlineSelected && vm.mixEnabled.value) listOf(track) else visible) },
                onFavorite = { vm.favorite(track) }, onQueue = { vm.enqueue(track) }, onNext = { vm.enqueue(track, true) },
                onRemove = { vm.remove(index) }, onDownload = { vm.download(track) }, onCancel={ vm.cancelDownload(track) }, onDelete={ vm.deleteDownload(track) }, automatic = destination=="Up Next" && index in playback.automaticEntries)
            HorizontalDivider(Modifier.padding(start = 40.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        }
    }
}

@Composable
private fun TrackRow(
    track: Track, saved: SavedTrack?, selected: Boolean, queued: Boolean, showHistory: Boolean, connected: Boolean, offline: Boolean,
    onPlay: () -> Unit, onFavorite: () -> Unit, onQueue: () -> Unit, onNext: () -> Unit, onRemove: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, automatic: Boolean=false
) {
    var menu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    if(delete) AlertDialog(onDismissRequest={ delete=false },title={ Text("Remove saved audio?") },text={ Text("This removes the downloaded copy of ${track.title}. Favorites and listening history are kept.") },confirmButton={ TextButton(onClick={ delete=false;onDelete() }) { Text("Remove download") } },dismissButton={ TextButton(onClick={ delete=false }) { Text("Cancel") } })
    Surface(shape = RoundedCornerShape(6.dp), color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .06f) else Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(enabled = connected, onClick = onPlay).padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (track.thumbnailUrl != null && (!offline || track.thumbnailUrl.startsWith("file:"))) coil.compose.AsyncImage(model = track.thumbnailUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).testTag("online-thumbnail"), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(if (selected) Icons.Outlined.GraphicEq else Icons.Outlined.MusicNote, null, Modifier.size(22.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (track.id.startsWith("youtube:")) Text(if (offline && saved?.localUri==null) "Online · not saved offline" else if(automatic) "Mix" else "Online", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if(saved?.downloadState in com.mangomusic.downloads.DownloadPolicy.active) {
                    val percent=com.mangomusic.downloads.DownloadPolicy.percent(saved!!.downloadedBytes,saved.totalBytes)
                    Text(if(saved.downloadState=="VERIFYING") "Verifying audio…" else "${saved.downloadState.lowercase().replaceFirstChar { it.uppercase() }} · ${percent?.let { "$it%" } ?: "${saved.downloadedBytes/1024} KB"}",style=MaterialTheme.typography.labelSmall)
                    if(percent!=null) LinearProgressIndicator(progress={percent/100f},modifier=Modifier.fillMaxWidth())
                }
                if(saved?.downloadState in listOf("CANCELLED","PAUSED")) Text(saved!!.downloadState.lowercase().replaceFirstChar { it.uppercase() },style=MaterialTheme.typography.labelSmall)
                if (saved != null && saved.downloadId != 0L) Text("Downloading…", style = MaterialTheme.typography.labelSmall)
                if (saved?.downloadError != null) Text(saved.downloadError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                if (saved?.localUri != null || track.uri.startsWith("file:")) Text("Saved offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showHistory && saved != null && saved.lastPlayed != 0L) Text("${saved.playCount} ${if (saved.playCount == 1) "play" else "plays"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (saved?.favorite == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (saved?.favorite == true) "Unfavorite ${track.title}" else "Favorite ${track.title}",
                    tint = if (saved?.favorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More for ${track.title}", Modifier.size(20.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Play next") }, onClick = { onNext(); menu = false })
                    DropdownMenuItem(text = { Text("Add to Up Next") }, onClick = { onQueue(); menu = false })
                    if (!offline && com.mangomusic.downloads.DownloadPolicy.eligible(track) && saved?.localUri == null && (saved?.downloadId ?: 0L)==0L && saved?.downloadState !in com.mangomusic.downloads.DownloadPolicy.active) DropdownMenuItem(text = { Text(if(saved?.downloadState in listOf("FAILED","PAUSED","CANCELLED","MISSING")) "Retry download" else "Download") }, onClick = { onDownload(); menu = false })
                    if(saved?.downloadState in com.mangomusic.downloads.DownloadPolicy.active) DropdownMenuItem(text={ Text("Cancel download") },onClick={ onCancel();menu=false })
                    if(saved?.localUri!=null) DropdownMenuItem(text={ Text("Remove download") },onClick={ delete=true;menu=false })
                    if (queued) DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { onRemove(); menu = false })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(destination: String, query: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (query.isNotBlank()) "No matches yet" else when (destination) {
            "Favorites" -> "No favorites"; "History" -> "No listening history"; "Up Next" -> "Queue is empty"; else -> "No saved audio"
        }, style = MaterialTheme.typography.titleMedium)
        Text(if (query.isNotBlank()) "Try a different title or artist." else when (destination) {
            "Favorites" -> "Tap a song’s heart to save it here."
            "History" -> "Songs appear here after playback starts."
            "Up Next" -> "Use a song’s menu to add it to Up Next."
            else -> "Import audio or download an authorized audio link for offline playback."
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniPlayer(track: Track, state: PlaybackState, vm: MusicViewModel, open: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = open).semantics { contentDescription = "Open Now Playing" }
                .padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Album, null, Modifier.size(25.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(if (state.buffering) "Buffering…" else track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = vm::toggle) { Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.playing) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, "Next") }
            }
        }
    }
}

@Composable
private fun NowPlaying(
    track: Track?, state: PlaybackState, vm: MusicViewModel, angle: MutableFloatState, favorite: Boolean,
    wide: Boolean, close: () -> Unit, queue: () -> Unit
) {
    val mixEnabled by vm.mixEnabled.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.playing, lifecycle) {
        if (state.playing) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (isActive) withInfiniteAnimationFrameNanos { now ->
                if (previous != 0L) angle.floatValue = DiscMotion.advance(angle.floatValue, (now - previous) / 1_000_000f, true)
                previous = now
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 620.dp
        Column(Modifier.fillMaxSize().then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!wide) IconButton(onClick = close) { Icon(Icons.Outlined.KeyboardArrowDown, "Close Now Playing") }
                Text("Now Playing", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = queue) { Icon(Icons.AutoMirrored.Outlined.QueueMusic, "Open Up Next") }
            }
            if (compact) Spacer(Modifier.height(16.dp)) else Spacer(Modifier.weight(1f))
            Vinyl(angle, Modifier.size(if (compact) 200.dp else 268.dp))
            if (compact) Spacer(Modifier.height(24.dp)) else Spacer(Modifier.weight(1f))
            Text(track?.title ?: "Nothing playing", Modifier.fillMaxWidth(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(track?.artist ?: "Choose a song to get started", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(20.dp))
            var seeking by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = seeking ?: state.position.toFloat().coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { seeking = it }, onValueChangeFinished = { seeking?.let { vm.seek(it.toLong()) }; seeking = null },
                valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f), enabled = state.duration > 0,
                modifier = Modifier.semantics { contentDescription = "Playback position" }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(timeLabel(seeking?.toLong() ?: state.position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(if (state.buffering) "Buffering…" else timeLabel(state.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ModeButton(Icons.Outlined.Shuffle, "Shuffle ${if (state.shuffle) "on" else "off"}", state.shuffle, vm::shuffle)
                IconButton(onClick = vm::previous, enabled = state.id != null) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(30.dp)) }
                FilledIconButton(onClick = vm::toggle, enabled = state.id != null, modifier = Modifier.size(64.dp)) {
                    Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(32.dp))
                }
                IconButton(onClick = vm::next, enabled = state.id != null) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(30.dp)) }
                ModeButton(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                    "Repeat ${when (state.repeat) { 1 -> "One"; 2 -> "All"; else -> "Off" }}", state.repeat != 0, vm::repeat)
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = vm::stop, enabled = state.id != null) { Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Stop") }
                if (track != null) TextButton(onClick = { vm.favorite(track) }) {
                    Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text(if (favorite) "Saved" else "Favorite")
                }
            }
            if(track!=null && com.mangomusic.downloads.DownloadPolicy.eligible(track)) {
                val rows by vm.saved.collectAsStateWithLifecycle()
                val stored=rows.find { it.id==track.id }
                if(stored?.downloadState in com.mangomusic.downloads.DownloadPolicy.active) TextButton(onClick={ vm.cancelDownload(track) }) { Text("Cancel download · ${com.mangomusic.downloads.DownloadPolicy.percent(stored!!.downloadedBytes,stored.totalBytes)?.let { "$it%" } ?: "Saving…"}") }
                else if(stored?.localUri!=null) Text("Saved offline",style=MaterialTheme.typography.labelSmall)
                else TextButton(enabled=!offline,onClick={ vm.download(track) }) { Icon(Icons.Outlined.DownloadForOffline,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text(if(stored?.downloadState=="FAILED") "Retry download" else "Download") }
            }
            if(track?.id?.startsWith("youtube:")==true || mixEnabled) MixToggle(vm, offline)
            if (compact) Spacer(Modifier.height(16.dp)) else Spacer(Modifier.weight(.5f))
        }
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, active: Boolean, action: () -> Unit) {
    IconButton(onClick = action) { Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
internal fun Vinyl(angle: MutableFloatState, modifier: Modifier) {
    BoxWithConstraints(modifier.semantics { contentDescription = "Mango Music vinyl disc" }.graphicsLayer { rotationZ = angle.floatValue }, contentAlignment = Alignment.Center) {
        val small = maxWidth < 250.dp
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(Brush.linearGradient(listOf(Color(0xFF343438), Color(0xFF09090B), Color(0xFF29292D)), Offset.Zero, Offset(size.width, size.height)), radius)
            for (i in 0..20) drawCircle(Color.White.copy(alpha = .035f), radius * (.42f + i * .025f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
            drawArc(Color.White.copy(alpha = .06f), 225f, 48f, false, topLeft = Offset(radius * .10f, radius * .10f),
                size = androidx.compose.ui.geometry.Size(radius * 1.8f, radius * 1.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(radius * .10f))
            drawCircle(Color(0xFF242427), radius * .34f)
            drawCircle(Color(0xFF09090B), radius * .035f)
        }
        // Center the original asset on the same pivot as the rotating disc at every size.
        Image(painterResource(R.drawable.mango_logo), null, Modifier.align(Alignment.Center).size(if (small) 32.dp else 40.dp).testTag("vinyl-logo").clip(RoundedCornerShape(if (small) 8.dp else 10.dp)))
    }
}

@Composable
private fun AddLinkDialog(dismiss: () -> Unit, add: (String, String, Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var allowed by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add audio link") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add a direct HTTPS audio file you have permission to play. Web pages and subscription links won’t play.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(title, { title = it.take(200) }, label = { Text("Track title") }, singleLine = true)
            OutlinedTextField(url, { url = it.take(2048) }, label = { Text("Audio link (https://)") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allowed, { allowed = it }); Text("I have permission to download this audio.", style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = { add(title, url, allowed) }, enabled = title.isNotBlank() && url.startsWith("https://")) { Text("Add track") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun MixToggle(vm: MusicViewModel, offline: Boolean) {
    val enabled by vm.mixEnabled.collectAsStateWithLifecycle()
    val status by vm.mixStatus.collectAsStateWithLifecycle()
    FilterChip(selected=enabled,onClick={ vm.mix(!enabled) },enabled=!offline || enabled,
        label={ Text(if(enabled) "Mix on" else "Mix off") },
        leadingIcon={ if(status.loading) CircularProgressIndicator(Modifier.size(14.dp),strokeWidth=2.dp) else Icon(Icons.AutoMirrored.Outlined.QueueMusic,null,Modifier.size(16.dp)) })
}

@Composable
private fun MixStatusLine(vm: MusicViewModel) {
    val enabled by vm.mixEnabled.collectAsStateWithLifecycle()
    val status by vm.mixStatus.collectAsStateWithLifecycle()
    if(enabled) {
        Text(if(status.loading) "Finding Mix songs…" else status.message ?: status.source?.let { "Mix · $it" } ?: "Manual songs play first.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(status.retryable) TextButton(onClick=vm::retryMix) { Text("Retry Mix") }
    }
}
