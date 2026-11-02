package com.mangomusic.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.mangomusic.data.PlaylistEntity
import com.mangomusic.data.PlaylistWithTracks
import com.mangomusic.data.SavedTrack
import com.mangomusic.domain.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val destinations = listOf("Search", "Up Next", "Favorites", "Playlists", "History", "Downloads")
private val navIcons = listOf(
    Icons.Outlined.Search, Icons.AutoMirrored.Outlined.QueueMusic,
    Icons.Outlined.FavoriteBorder, Icons.AutoMirrored.Outlined.PlaylistPlay,
    Icons.Outlined.History, Icons.Outlined.DownloadForOffline
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
    var showingOnlineMixOverview by rememberSaveable { mutableStateOf(false) }
    var selectedOnlineMixId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSearchMode by rememberSaveable { mutableStateOf<String?>(null) }
    val currentOnlineMix = selectedOnlineMixId?.let { id -> DiscoverCatalog.onlineMixes.find { it.id == id } }
    val onlineMixState by vm.onlineMixState.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf("Search") }
    var selectedArtistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMoodId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creatingPlaylist by rememberSaveable { mutableStateOf(false) }
    var creatingPlaylistWithTrack by rememberSaveable { mutableStateOf<Track?>(null) }
    var editingPlaylist by rememberSaveable { mutableStateOf<PlaylistEntity?>(null) }
    var deletingPlaylist by rememberSaveable { mutableStateOf<PlaylistEntity?>(null) }
    var addingTrackToPlaylist by rememberSaveable { mutableStateOf<Track?>(null) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val currentPlaylist = selectedPlaylistId?.let { id -> playlists.find { it.playlist.id == id } }
    val artistProfileState by vm.artistProfile.collectAsStateWithLifecycle()
    val currentArtist = selectedArtistId?.let { id -> DiscoverCatalog.featuredArtists.find { it.id == id } }
    val moodDiscoveryState by vm.moodDiscovery.collectAsStateWithLifecycle()
    val currentMood = selectedMoodId?.let { id -> DiscoverCatalog.moods.find { it.id == id } }
    var selectedCollectionId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentCollection = selectedCollectionId?.let { id -> DiscoverCatalog.medleyCollections.find { it.id == id } }
    val medleyCollectionState by vm.medleyCollection.collectAsStateWithLifecycle()
    val trendingTracks by vm.trendingTracks.collectAsStateWithLifecycle()
    val trendingLoading by vm.trendingLoading.collectAsStateWithLifecycle()
    val updateState by vm.updateUiState.collectAsStateWithLifecycle()
    val updateDialogVisible by vm.updateDialogVisible.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var showingFeedback by rememberSaveable { mutableStateOf(false) }
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
        BackHandler(showingFeedback && !nowPlaying && !drawer.isOpen) { showingFeedback = false }
        BackHandler(selectedOnlineMixId != null && !nowPlaying && !drawer.isOpen) { selectedOnlineMixId = null }
        BackHandler(showingOnlineMixOverview && !nowPlaying && !drawer.isOpen && selectedOnlineMixId == null) { showingOnlineMixOverview = false }
        BackHandler(activeSearchMode != null && !nowPlaying && !drawer.isOpen && selectedOnlineMixId == null && !showingOnlineMixOverview) { activeSearchMode = null; query = "" }
        BackHandler(selectedArtistId != null && !nowPlaying && !drawer.isOpen) { selectedArtistId = null }
        BackHandler(selectedMoodId != null && !nowPlaying && !drawer.isOpen) { selectedMoodId = null }
        BackHandler(selectedCollectionId != null && !nowPlaying && !drawer.isOpen) { selectedCollectionId = null }
        BackHandler(selectedPlaylistId != null && !nowPlaying && !drawer.isOpen) { selectedPlaylistId = null }
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
                                selected = !settings && !showingFeedback && destination == label && selectedArtistId == null && selectedMoodId == null && selectedCollectionId == null && selectedPlaylistId == null && selectedOnlineMixId == null && !showingOnlineMixOverview && activeSearchMode == null,
                                onClick = {
                                    destination = label
                                    selectedArtistId = null
                                    selectedMoodId = null
                                    selectedCollectionId = null
                                    selectedPlaylistId = null
                                    selectedOnlineMixId = null
                                    showingOnlineMixOverview = false
                                    activeSearchMode = null
                                    showingFeedback = false
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
                            if (!nowPlaying || wide) {
                                var topMenu by remember { mutableStateOf(false) }
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (showingFeedback) {
                                        IconButton(onClick = { showingFeedback = false }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Discover Home")
                                        }
                                        Text(
                                            text = "Send Feedback",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    } else if (destination == "Search" && selectedArtistId != null && currentArtist != null) {
                                        IconButton(onClick = { selectedArtistId = null }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Discover Home")
                                        }
                                        Text(
                                            text = currentArtist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    } else if (destination == "Search" && selectedMoodId != null && currentMood != null) {
                                        IconButton(onClick = { selectedMoodId = null }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Discover Home")
                                        }
                                        Text(
                                            text = "${currentMood.name} Music",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    } else if (destination == "Search" && selectedCollectionId != null && currentCollection != null) {
                                        IconButton(onClick = { selectedCollectionId = null }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Discover Home")
                                        }
                                        Text(
                                            text = currentCollection.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    } else if (destination == "Search" && selectedOnlineMixId != null && currentOnlineMix != null) {
                                        IconButton(onClick = { selectedOnlineMixId = null }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Online Mixes")
                                        }
                                        Text(
                                            text = currentOnlineMix.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    } else if (destination == "Search" && showingOnlineMixOverview) {
                                        IconButton(onClick = { showingOnlineMixOverview = false }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Discover Home")
                                        }
                                        Text(
                                            text = "Online Mix",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    } else if (destination == "Search" && activeSearchMode != null) {
                                        IconButton(onClick = { activeSearchMode = null; query = "" }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Discover Home")
                                        }
                                        Text(
                                            text = if (activeSearchMode == "online") "Search Online Music" else "My Library",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    } else if (destination == "Playlists" && selectedPlaylistId != null && currentPlaylist != null) {
                                        IconButton(onClick = { selectedPlaylistId = null }) {
                                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Playlists")
                                        }
                                        Text(
                                            text = currentPlaylist.playlist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                                        )
                                    } else {
                                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Open navigation menu") }
                                        Spacer(Modifier.weight(1f))
                                    }
                                    Box {
                                        IconButton(onClick = { topMenu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                                            DropdownMenuItem(text = { Text("Settings") }, onClick = { topMenu = false; settings = true }, leadingIcon = { Icon(Icons.Outlined.Settings, null) })
                                            DropdownMenuItem(text = { Text("Send feedback") }, onClick = { topMenu = false; showingFeedback = true }, leadingIcon = { Icon(Icons.Outlined.Feedback, null) })
                                            DropdownMenuItem(text = { Text("Import audio") }, onClick = { topMenu = false; import.launch(arrayOf("audio/*")) }, leadingIcon = { Icon(Icons.Outlined.Add, null) })
                                            DropdownMenuItem(text = { Text("Add audio link") }, onClick = { topMenu = false; addLink = true }, leadingIcon = { Icon(Icons.Outlined.Link, null) }, enabled = !offline)
                                        }
                                    }
                                }
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
                                    if (showingFeedback) {
                                        FeedbackScreen(onBack = { showingFeedback = false })
                                    } else if (destination == "Search" && selectedArtistId != null && currentArtist != null) {
                                        ArtistProfileScreen(
                                            artist = currentArtist,
                                            state = artistProfileState,
                                            saved = saved,
                                            playback = playback,
                                            offline = offline,
                                            onBack = { selectedArtistId = null },
                                            onRetry = { vm.retryArtist() },
                                            onPlayTrack = { track, list -> vm.play(track, list) },
                                            onFavorite = { vm.favorite(it) },
                                            onQueue = { vm.enqueue(it) },
                                            onNext = { vm.enqueue(it, true) },
                                            onDownload = { vm.download(it) },
                                            onCancelDownload = { vm.cancelDownload(it) },
                                            onDeleteDownload = { vm.deleteDownload(it) },
                                            onAddToPlaylist = { addingTrackToPlaylist = it }
                                        )
                                    } else if (destination == "Search" && selectedMoodId != null && currentMood != null) {
                                        MoodDiscoveryScreen(
                                            mood = currentMood,
                                            state = moodDiscoveryState,
                                            saved = saved,
                                            playback = playback,
                                            offline = offline,
                                            onBack = { selectedMoodId = null },
                                            onRetry = { vm.retryMood() },
                                            onPlayTrack = { track, list -> vm.play(track, list) },
                                            onFavorite = { vm.favorite(it) },
                                            onQueue = { vm.enqueue(it) },
                                            onNext = { vm.enqueue(it, true) },
                                            onDownload = { vm.download(it) },
                                            onCancelDownload = { vm.cancelDownload(it) },
                                            onDeleteDownload = { vm.deleteDownload(it) },
                                            onAddToPlaylist = { addingTrackToPlaylist = it }
                                        )
                                    } else if (destination == "Search" && selectedCollectionId != null && currentCollection != null) {
                                        MedleyDiscoveryScreen(
                                            collection = currentCollection,
                                            state = medleyCollectionState,
                                            saved = saved,
                                            playback = playback,
                                            offline = offline,
                                            onBack = { selectedCollectionId = null },
                                            onRetry = { vm.retryMedleyCollection() },
                                            onPlayTrack = { track, list -> vm.play(track, list) },
                                            onFavorite = { vm.favorite(it) },
                                            onQueue = { vm.enqueue(it) },
                                            onNext = { vm.enqueue(it, true) },
                                            onDownload = { vm.download(it) },
                                            onCancelDownload = { vm.cancelDownload(it) },
                                            onDeleteDownload = { vm.deleteDownload(it) },
                                            onAddToPlaylist = { addingTrackToPlaylist = it }
                                        )
                                    } else if (destination == "Search" && selectedOnlineMixId != null && currentOnlineMix != null) {
                                        OnlineMixDetailScreen(
                                            mix = currentOnlineMix,
                                            state = onlineMixState,
                                            saved = saved,
                                            playback = playback,
                                            offline = offline,
                                            onBack = { selectedOnlineMixId = null },
                                            onRefresh = { vm.refreshOnlineMix() },
                                            onRetry = { vm.retryOnlineMix() },
                                            onPlayTrack = { track, list -> vm.play(track, list) },
                                            onFavorite = { vm.favorite(it) },
                                            onQueue = { vm.enqueue(it) },
                                            onNext = { vm.enqueue(it, true) },
                                            onDownload = { vm.download(it) },
                                            onCancelDownload = { vm.cancelDownload(it) },
                                            onDeleteDownload = { vm.deleteDownload(it) },
                                            onAddToPlaylist = { addingTrackToPlaylist = it }
                                        )
                                    } else if (destination == "Search" && showingOnlineMixOverview) {
                                        OnlineMixOverviewScreen(
                                            mixes = DiscoverCatalog.onlineMixes,
                                            onBack = { showingOnlineMixOverview = false },
                                            onSelectMix = { mix ->
                                                selectedOnlineMixId = mix.id
                                                vm.openOnlineMix(mix)
                                            }
                                        )
                                    } else if (destination == "Playlists") {
                                        if (selectedPlaylistId != null && currentPlaylist != null) {
                                            PlaylistDetailScreen(
                                                playlistWithTracks = currentPlaylist,
                                                saved = saved,
                                                playback = playback,
                                                offline = offline,
                                                onBack = { selectedPlaylistId = null },
                                                onPlayAll = { vm.playPlaylist(currentPlaylist, false) },
                                                onShuffle = { vm.playPlaylist(currentPlaylist, true) },
                                                onPlayTrack = { track, list -> vm.play(track, list) },
                                                onFavorite = { vm.favorite(it) },
                                                onQueue = { vm.enqueue(it) },
                                                onNext = { vm.enqueue(it, true) },
                                                onAddToPlaylist = { addingTrackToPlaylist = it },
                                                onRemoveTrack = { trackId -> vm.removeTrackFromPlaylist(currentPlaylist.playlist.id, trackId) },
                                                onMoveTrack = { from, to -> vm.moveTrackInPlaylist(currentPlaylist.playlist.id, from, to) },
                                                onEditPlaylist = { editingPlaylist = currentPlaylist.playlist },
                                                onDeletePlaylist = { deletingPlaylist = currentPlaylist.playlist },
                                                onDownload = { vm.download(it) },
                                                onCancelDownload = { vm.cancelDownload(it) },
                                                onDeleteDownload = { vm.deleteDownload(it) }
                                            )
                                        } else {
                                            PlaylistsScreen(
                                                playlists = playlists,
                                                onCreatePlaylist = { creatingPlaylist = true },
                                                onOpenPlaylist = { selectedPlaylistId = it },
                                                onEditPlaylist = { editingPlaylist = it },
                                                onDeletePlaylist = { deletingPlaylist = it }
                                            )
                                        }
                                    } else {
                                        screenState.SaveableStateProvider(destination) {
                                            LibraryScreen(
                                                destination, query, { query = it }, saved, all, playback, offline, vm,
                                                { import.launch(arrayOf("audio/*")) }, { addLink = true },
                                                { settings = true }, { clearHistory = true }, onlineSelected, { onlineSelected = it }, onlineResults,
                                                activeSearchMode = activeSearchMode,
                                                onSetSearchMode = { activeSearchMode = it },
                                                onOpenOnlineMix = { showingOnlineMixOverview = true },
                                                onSelectArtist = { artist ->
                                                    selectedArtistId = artist.id
                                                    vm.selectArtist(artist)
                                                },
                                                onAddToPlaylist = { addingTrackToPlaylist = it },
                                                onSelectMood = { mood ->
                                                    selectedMoodId = mood.id
                                                    selectedArtistId = null
                                                    selectedPlaylistId = null
                                                    selectedCollectionId = null
                                                    selectedOnlineMixId = null
                                                    showingOnlineMixOverview = false
                                                    vm.selectMood(mood)
                                                },
                                                onSelectCollection = { col ->
                                                    selectedCollectionId = col.id
                                                    selectedArtistId = null
                                                    selectedPlaylistId = null
                                                    selectedMoodId = null
                                                    selectedOnlineMixId = null
                                                    showingOnlineMixOverview = false
                                                    vm.selectMedleyCollection(col)
                                                },
                                                onOpenDownloads = {
                                                    destination = "Downloads"
                                                }
                                            )
                                        }
                                    }
                                }
                                if (nowPlaying || (wide && current != null)) {
                                    if (wide) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Box(Modifier.then(if (wide) Modifier.width(360.dp) else Modifier.weight(1f)).fillMaxHeight()) {
                                        NowPlaying(current, playback, vm, discAngle, saved.any { it.id == playback.id && it.favorite }, wide,
                                            { nowPlaying = false }, { destination = "Up Next"; nowPlaying = false },
                                            onAddToPlaylist = { track -> addingTrackToPlaylist = track })
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
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("App Updates", style = MaterialTheme.typography.bodyMedium)
                        Text("Version ${vm.updater.currentVersionName} (Build ${vm.updater.currentVersionCode})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (updateState) {
                        is com.mangomusic.updater.UpdateUiState.Checking -> {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFE5B842))
                        }
                        is com.mangomusic.updater.UpdateUiState.Available -> {
                            Button(
                                onClick = { vm.showUpdateDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5B842), contentColor = Color(0xFF121214)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Update", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { vm.checkForUpdates(isManual = true) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Check for updates", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                if (updateState is com.mangomusic.updater.UpdateUiState.UpToDate) {
                    Text("Mango Music is up to date.", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE5B842), modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                } else if (updateState is com.mangomusic.updater.UpdateUiState.Error && !updateDialogVisible) {
                    Text((updateState as com.mangomusic.updater.UpdateUiState.Error).message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
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
        if (creatingPlaylist) {
            CreateOrEditPlaylistDialog(
                onDismiss = { creatingPlaylist = false },
                onConfirm = { name, desc ->
                    vm.createPlaylist(name, desc) { id -> selectedPlaylistId = id }
                    creatingPlaylist = false
                }
            )
        }
        if (creatingPlaylistWithTrack != null) {
            val track = creatingPlaylistWithTrack!!
            CreateOrEditPlaylistDialog(
                onDismiss = { creatingPlaylistWithTrack = null },
                onConfirm = { name, desc ->
                    vm.createPlaylist(name, desc, initialTrack = track) { id -> selectedPlaylistId = id }
                    creatingPlaylistWithTrack = null
                }
            )
        }
        if (editingPlaylist != null) {
            CreateOrEditPlaylistDialog(
                initialName = editingPlaylist!!.name,
                initialDescription = editingPlaylist!!.description,
                isEditing = true,
                onDismiss = { editingPlaylist = null },
                onConfirm = { name, desc ->
                    vm.updatePlaylist(editingPlaylist!!.id, name, desc)
                    editingPlaylist = null
                }
            )
        }
        if (deletingPlaylist != null) {
            DeletePlaylistDialog(
                playlistName = deletingPlaylist!!.name,
                onDismiss = { deletingPlaylist = null },
                onConfirm = {
                    vm.deletePlaylist(deletingPlaylist!!.id)
                    deletingPlaylist = null
                }
            )
        }
        if (addingTrackToPlaylist != null) {
            val track = addingTrackToPlaylist!!
            AddToPlaylistSheet(
                track = track,
                playlists = playlists,
                onDismiss = { addingTrackToPlaylist = null },
                onCreateNewPlaylist = {
                    val t = addingTrackToPlaylist
                    addingTrackToPlaylist = null
                    creatingPlaylistWithTrack = t
                },
                onSelectPlaylist = { pId ->
                    vm.addTrackToPlaylist(pId, track) { success, msg ->
                        vm.error.value = msg
                    }
                    addingTrackToPlaylist = null
                }
            )
        }
        if (updateDialogVisible) {
            com.mangomusic.updater.UpdateDialog(
                state = updateState,
                currentVersionName = vm.updater.currentVersionName,
                onStartDownload = vm::startUpdateDownload,
                onCancelDownload = vm::cancelUpdateDownload,
                onInstall = vm::installUpdate,
                onDismiss = vm::dismissUpdateDialog
            )
        }
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
    onlineSelected: Boolean, selectOnline: (Boolean) -> Unit, onlineState: com.mangomusic.online.OnlineSearchState,
    activeSearchMode: String? = null,
    onSetSearchMode: (String?) -> Unit = {},
    onOpenOnlineMix: () -> Unit = {},
    onSelectArtist: (FeaturedArtist) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onSelectMood: (MoodItem) -> Unit = {},
    onSelectCollection: (MedleyCollection) -> Unit = {},
    onOpenDownloads: () -> Unit = {}
) {
    var selectedCategory by rememberSaveable { mutableStateOf("All Music") }
    LaunchedEffect(selectedCategory) {
        vm.loadTrending(selectedCategory)
    }
    val isSearching = destination == "Search" && (activeSearchMode != null || query.isNotBlank() || (onlineSelected && !offline && (onlineState.loading || onlineState.error != null || onlineState.tracks.isNotEmpty())))
    val visible = remember(destination, onlineSelected, offline, onlineState.tracks, all, query, saved, playback.queue) {
        when (destination) {
            "Search" -> if (onlineSelected && !offline) onlineState.tracks else Catalog.search(all.filter { !it.id.startsWith("youtube:") && vm.available(it) }, query)
            "Favorites" -> saved.filter { it.favorite }.map { it.track() }
            "History" -> saved.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.map { it.track() }
            "Downloads" -> saved.filter { it.localUri != null || it.uri.startsWith("file:") || it.uri.startsWith("asset:") || it.downloadId > 0 || it.downloadError != null || it.downloadState != "NONE" }.map { it.track() }
            else -> playback.queue.mapNotNull { id -> all.find { it.id == id } }
        }
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
                if (isSearching) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !onlineSelected || offline,
                            onClick = {
                                selectOnline(false)
                                onSetSearchMode("library")
                            },
                            label = { Text("Library") }
                        )
                        FilterChip(
                            selected = onlineSelected && !offline,
                            onClick = {
                                selectOnline(true)
                                onSetSearchMode("online")
                            },
                            enabled = !offline,
                            label = { Text("Online") }
                        )
                        if (onlineSelected) MixToggle(vm, offline)
                    }
                    if (!onlineSelected || offline) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = import) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Import audio") }
                        TextButton(onClick = addLink, enabled = !offline) { Icon(Icons.Outlined.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add audio link") }
                    }
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

        // Discover Home Experience when query is blank on Search/Home
        if (destination == "Search" && !isSearching) {
            // 2. Music Category Chips
            item(key = "discover-categories") {
                CategoryChipsRow(
                    categories = DiscoverCatalog.categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            // Explore Music: Search Online & My Library + Online Mix
            item(key = "discover-explore-music") {
                ExploreMusicSection(
                    onOpenSearchOnline = {
                        selectOnline(true)
                        onSetSearchMode("online")
                    },
                    onOpenMyLibrary = {
                        selectOnline(false)
                        onSetSearchMode("library")
                    },
                    onOpenOnlineMix = onOpenOnlineMix
                )
            }

            // 3. Featured Artists
            item(key = "discover-artists") {
                val filteredArtists = remember(selectedCategory) {
                    if (selectedCategory == "All Music") DiscoverCatalog.featuredArtists
                    else DiscoverCatalog.featuredArtists.filter { it.category == selectedCategory || it.category == "All Music" }
                }
                SectionHeader(title = "Featured Artists", topPadding = 20.dp, bottomPadding = 12.dp)
                FeaturedArtistsRow(
                    artists = filteredArtists,
                    offline = offline,
                    onArtistClick = onSelectArtist
                )
            }

            // 4. Browse by Mood
            item(key = "discover-moods") {
                SectionHeader(title = "Browse by Mood", topPadding = 24.dp, bottomPadding = 12.dp)
                BrowseByMoodRow(onMoodClick = onSelectMood)
            }

            // 5. Popular Picks (Trending Now)
            item(key = "discover-popular") {
                val popularTracks = remember(all, selectedCategory) {
                    val availableTracks = all.filter { vm.available(it) }
                    if (selectedCategory == "All Music") availableTracks
                    else availableTracks.filter { it.artist.contains(selectedCategory, ignoreCase = true) || it.title.contains(selectedCategory, ignoreCase = true) }.ifEmpty { availableTracks }
                }
                SectionHeader(
                    title = "Popular Picks",
                    actionLabel = if (popularTracks.isNotEmpty()) "Play all" else null,
                    topPadding = 24.dp,
                    bottomPadding = 12.dp,
                    onActionClick = { if (popularTracks.isNotEmpty()) vm.play(popularTracks.first(), popularTracks) }
                )
                PopularPicksRow(
                    tracks = popularTracks,
                    saved = saved,
                    currentTrackId = playback.id,
                    isPlaying = playback.playing,
                    offline = offline,
                    onTrackClick = { track -> vm.play(track, popularTracks) },
                    onFavorite = { track -> vm.favorite(track) }
                )
            }

            // 5. Medley Section
            item(key = "discover-medley") {
                val downloaded = remember(saved) {
                    saved.filter { it.localUri != null || it.uri.startsWith("asset:") }.map { it.track() }
                }
                val medleyCards = remember(downloaded.size) {
                    listOf(
                        CuratedMix(
                            id = "medley-offline",
                            title = "Offline Library",
                            subtitle = if (downloaded.isNotEmpty()) "${downloaded.size} tracks saved offline" else "Browse downloaded audio",
                            gradientColors = listOf(Color(0xFF004D40), Color(0xFF00796B)),
                            onPlay = { onOpenDownloads() },
                            icon = Icons.Outlined.DownloadDone
                        ),
                        CuratedMix(
                            id = "col-new-releases",
                            title = "New Releases",
                            subtitle = "Fresh official music releases",
                            gradientColors = listOf(Color(0xFF0D47A1), Color(0xFF1976D2)),
                            onPlay = {
                                val col = DiscoverCatalog.medleyCollections.first { it.id == "col-new-releases" }
                                onSelectCollection(col)
                            },
                            icon = Icons.Outlined.NewReleases
                        ),
                        CuratedMix(
                            id = "col-classic-bollywood",
                            title = "Classic Bollywood",
                            subtitle = "Golden era evergreen hits",
                            gradientColors = listOf(Color(0xFF4A148C), Color(0xFF7B1FA2)),
                            onPlay = {
                                val col = DiscoverCatalog.medleyCollections.first { it.id == "col-classic-bollywood" }
                                onSelectCollection(col)
                            },
                            icon = Icons.Outlined.Radio
                        ),
                        CuratedMix(
                            id = "col-popular-songs",
                            title = "Most Popular Songs",
                            subtitle = "Top hits & billion-view songs",
                            gradientColors = listOf(Color(0xFFBF360C), Color(0xFFF4511E)),
                            onPlay = {
                                val col = DiscoverCatalog.medleyCollections.first { it.id == "col-popular-songs" }
                                onSelectCollection(col)
                            },
                            icon = Icons.Outlined.TrendingUp
                        )
                    )
                }
                SectionHeader(title = "Medley", topPadding = 24.dp, bottomPadding = 12.dp)
                MadeForYouRow(mixes = medleyCards)
            }

            // 6. Trending Songs (Replaces Recently Played)
            item(key = "discover-trending") {
                val trendingTracks by vm.trendingTracks.collectAsStateWithLifecycle()
                val isTrendingLoading by vm.trendingLoading.collectAsStateWithLifecycle()
                SectionHeader(
                    title = "Trending Songs",
                    subtitle = "Currently trending music",
                    actionLabel = if (trendingTracks.isNotEmpty()) "Play all" else null,
                    topPadding = 24.dp,
                    bottomPadding = 12.dp,
                    onActionClick = { if (trendingTracks.isNotEmpty()) vm.play(trendingTracks.first(), trendingTracks) }
                )
                if (isTrendingLoading && trendingTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (trendingTracks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        trendingTracks.take(8).forEach { track ->
                            val stored = saved.find { it.id == track.id }
                            TrackRow(
                                track = track,
                                saved = stored,
                                selected = playback.id == track.id,
                                queued = false,
                                showHistory = false,
                                connected = playback.connected,
                                offline = offline,
                                onPlay = { vm.play(track, trendingTracks) },
                                onFavorite = { vm.favorite(track) },
                                onQueue = { vm.enqueue(track) },
                                onNext = { vm.enqueue(track, true) },
                                onRemove = {},
                                onDownload = { vm.download(track) },
                                onCancel = { vm.cancelDownload(track) },
                                onDelete = { vm.deleteDownload(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) }
                            )
                        }
                    }
                }
            }

            // Section header for complete library listing
            item(key = "discover-all-header") {
                SectionHeader(
                    title = "All Tracks",
                    actionLabel = "${all.size} songs",
                    topPadding = 24.dp,
                    bottomPadding = 8.dp
                )
            }
        }

        if (destination == "Search" && onlineSelected && !offline && isSearching) {
            if (onlineState.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Searching…", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium) }
            else if (onlineState.error != null) item(key = "online-error") {
                Text(onlineState.error, Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.searchOnline(query, true) }) { Text("Retry search") }
            }
            else if (visible.isEmpty()) item(key = "online-empty") {
                Text(if (query.isBlank()) "Search by title, artist or keywords." else "No online results. Try another search.", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (visible.isEmpty() && isSearching) item(key = "empty") { EmptyState(destination, query) }
        else if (visible.isEmpty() && destination != "Search") item(key = "empty") { EmptyState(destination, query) }

        itemsIndexed(visible, key = { index, track -> if (destination == "Up Next") "$index:${track.id}" else track.id }) { index, track ->
            val stored = saved.find { it.id == track.id }
            TrackRow(track, stored, playback.id == track.id, destination == "Up Next", destination == "History", playback.connected, offline,
                onPlay = { if (destination == "Up Next") vm.queuePlay(index) else vm.play(track, if(destination=="Search" && onlineSelected && vm.mixEnabled.value) listOf(track) else visible) },
                onFavorite = { vm.favorite(track) }, onQueue = { vm.enqueue(track) }, onNext = { vm.enqueue(track, true) },
                onRemove = { vm.remove(index) }, onDownload = { vm.download(track) }, onCancel={ vm.cancelDownload(track) }, onDelete={ vm.deleteDownload(track) }, automatic = destination=="Up Next" && index in playback.automaticEntries,
                onAddToPlaylist = { onAddToPlaylist(track) })
            HorizontalDivider(Modifier.padding(start = 40.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        }
    }
}

@Composable
internal fun TrackRow(
    track: Track, saved: SavedTrack?, selected: Boolean, queued: Boolean, showHistory: Boolean, connected: Boolean, offline: Boolean,
    onPlay: () -> Unit, onFavorite: () -> Unit, onQueue: () -> Unit, onNext: () -> Unit, onRemove: () -> Unit, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, automatic: Boolean=false,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
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
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = { onAddToPlaylist(); menu = false },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) }
                        )
                    }
                    if (onMoveUp != null) {
                        DropdownMenuItem(
                            text = { Text("Move up") },
                            onClick = { onMoveUp(); menu = false },
                            leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) }
                        )
                    }
                    if (onMoveDown != null) {
                        DropdownMenuItem(
                            text = { Text("Move down") },
                            onClick = { onMoveDown(); menu = false },
                            leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) }
                        )
                    }
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from playlist") },
                            onClick = { onRemoveFromPlaylist(); menu = false },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) }
                        )
                    }
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
    wide: Boolean, close: () -> Unit, queue: () -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null
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
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val artUrl = track?.thumbnailUrl
        Crossfade(
            targetState = artUrl,
            animationSpec = tween(durationMillis = 500),
            label = "nowPlayingBlurredBackground"
        ) { url ->
            if (!url.isNullOrBlank()) {
                Box(Modifier.fillMaxSize()) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.25f
                                scaleY = 1.25f
                            }
                            .blur(radius = 42.dp)
                    )
                    val scrim = MaterialTheme.colorScheme.background
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        scrim.copy(alpha = 0.65f),
                                        scrim.copy(alpha = 0.78f),
                                        scrim.copy(alpha = 0.90f)
                                    )
                                )
                            )
                    )
                }
            }
        }
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
                if (track != null && onAddToPlaylist != null) TextButton(onClick = { onAddToPlaylist(track) }) {
                    Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("Playlist")
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
