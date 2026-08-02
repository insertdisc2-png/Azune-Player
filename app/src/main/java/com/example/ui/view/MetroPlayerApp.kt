package com.example.ui.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Track
import com.example.ui.components.*
import com.example.ui.viewmodel.MetroViewModel
import kotlinx.coroutines.launch

fun copyUriToLocalFile(context: android.content.Context, uri: android.net.Uri): String {
    return try {
        val storageDir = context.cacheDir
        // Clean up any old bg_custom_image files to prevent caching and save space
        storageDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("bg_custom_image")) {
                file.delete()
            }
        }
        val destFile = java.io.File(storageDir, "bg_custom_image_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) {
                android.util.Log.e("MetroPlayerApp", "contentResolver.openInputStream(uri) returned null for URI: $uri")
                return ""
            }
            java.io.FileOutputStream(destFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        android.util.Log.i("MetroPlayerApp", "Successfully copied URI to local file: ${destFile.absolutePath}, size: ${destFile.length()} bytes")
        destFile.absolutePath
    } catch (e: Exception) {
        android.util.Log.e("MetroPlayerApp", "Error copying URI to local file: ${e.message}", e)
        e.printStackTrace()
        ""
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Composable
fun MetroPlayerApp(
    viewModel: MetroViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State bindings
    val isSettingsLoaded by viewModel.isSettingsLoaded.collectAsState()
    val settings by viewModel.userSettings.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val localSongs by viewModel.localTracksList.collectAsState()
    val allSongs by viewModel.allAvailableTracks.collectAsState()

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlayingState.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val playbackDuration by viewModel.playbackDuration.collectAsState()

    val playbackQueueState by viewModel.playbackQueueState.collectAsState()
    val customLyricsMap by viewModel.customLyricsMap.collectAsState()
    val playbackSpeedVal by viewModel.playbackSpeedVal.collectAsState()
    val playbackPitchVal by viewModel.playbackPitchVal.collectAsState()
    val isShuffle by viewModel.isShuffleState.collectAsState()
    val isRepeat by viewModel.isRepeatState.collectAsState()

    var isSplashTimerRunning by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500) // 2.5 second delay to allow databases/assets to initialize smoothly
        isSplashTimerRunning = false
    }

    if (!isSettingsLoaded || isSplashTimerRunning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Azune Music",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                        color = Color.White
                    ),
                    fontSize = 36.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color(0xFF0078D7),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        return
    }

    val favTrackIdsSet = remember(settings.favoriteTrackIds) {
        settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
    }

    // Active sub-views states
    var activePivotIndex by remember { mutableStateOf(0) } // 0: Hub, 1: Music, 2: Playlists, 3: Artists, 4: Albums, 5: Settings
    var selectedPlaylistIdDetail by remember { mutableStateOf<Int?>(null) }
    var isFullscreenConsoleVisible by remember { mutableStateOf(false) }
    var isLyricsExpanded by remember { mutableStateOf(false) }
    var isTileEditMode by remember { mutableStateOf(false) }

    // Resolve system dark/light theme options (Follow System)
    val isLight = when (settings.themeMode) {
        "light" -> true
        "dark" -> false
        "amoled" -> false
        else -> !androidx.compose.foundation.isSystemInDarkTheme()
    }

    val activity = context as? android.app.Activity
    if (activity != null) {
        SideEffect {
            val window = activity.window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = isLight
            controller.isAppearanceLightNavigationBars = isLight
        }
    }

    // Dynamic drag-and-drop tiles ordering definition
    var tileOrder by remember { mutableStateOf(listOf("now_playing", "pinned_tracks", "my_music", "playlists", "favorites", "artists", "albums")) }

    // Synchronize tiles layout order when database loads
    LaunchedEffect(settings.tileOrder) {
        if (settings.tileOrder.isNotEmpty()) {
            val list = settings.tileOrder.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (list.size >= 5) {
                tileOrder = list
            }
        }
    }

    // Robust back gesture interception
    androidx.activity.compose.BackHandler(
        enabled = isFullscreenConsoleVisible || selectedPlaylistIdDetail != null || activePivotIndex != 0 || isTileEditMode
    ) {
        if (isTileEditMode) {
            isTileEditMode = false
        } else if (isFullscreenConsoleVisible) {
            if (isLyricsExpanded) {
                isLyricsExpanded = false
            } else {
                isFullscreenConsoleVisible = false
            }
        } else if (selectedPlaylistIdDetail != null) {
            selectedPlaylistIdDetail = null
        } else if (activePivotIndex != 0) {
            activePivotIndex = 0
        }
    }

    // Dialog sheets states
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddTrackToPlaylistDialog by remember { mutableStateOf<Track?>(null) }

    // Search query states
    var searchMusicQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("all") } // "all", "favorites"

    // Permission states
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
        if (isGranted) {
            viewModel.loadLocalLibrary()
        }
    }

    // Colors mapping
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)

    // Scan directories when permission changes
    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            viewModel.loadLocalLibrary()
        }
    }

    val currentFontFamily = getMetroFontFamily(settings.fontFamily)
    val customTypography = remember(currentFontFamily) {
        androidx.compose.material3.Typography(
            displayLarge = androidx.compose.material3.Typography().displayLarge.copy(fontFamily = currentFontFamily),
            displayMedium = androidx.compose.material3.Typography().displayMedium.copy(fontFamily = currentFontFamily),
            displaySmall = androidx.compose.material3.Typography().displaySmall.copy(fontFamily = currentFontFamily),
            headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(fontFamily = currentFontFamily),
            headlineMedium = androidx.compose.material3.Typography().headlineMedium.copy(fontFamily = currentFontFamily),
            headlineSmall = androidx.compose.material3.Typography().headlineSmall.copy(fontFamily = currentFontFamily),
            titleLarge = androidx.compose.material3.Typography().titleLarge.copy(fontFamily = currentFontFamily),
            titleMedium = androidx.compose.material3.Typography().titleMedium.copy(fontFamily = currentFontFamily),
            titleSmall = androidx.compose.material3.Typography().titleSmall.copy(fontFamily = currentFontFamily),
            bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(fontFamily = currentFontFamily),
            bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(fontFamily = currentFontFamily),
            bodySmall = androidx.compose.material3.Typography().bodySmall.copy(fontFamily = currentFontFamily),
            labelLarge = androidx.compose.material3.Typography().labelLarge.copy(fontFamily = currentFontFamily),
            labelMedium = androidx.compose.material3.Typography().labelMedium.copy(fontFamily = currentFontFamily),
            labelSmall = androidx.compose.material3.Typography().labelSmall.copy(fontFamily = currentFontFamily)
        )
    }

    val resolvedColorScheme = remember(isLight, settings.themeMode) {
        if (isLight) {
            androidx.compose.material3.lightColorScheme(
                primary = Color(0xFF0078D7),
                onPrimary = Color.White,
                surface = Color.White,
                onSurface = Color.Black,
                background = Color.White,
                onBackground = Color.Black
            )
        } else {
            androidx.compose.material3.darkColorScheme(
                primary = Color(0xFF0078D7),
                onPrimary = Color.White,
                surface = if (settings.themeMode == "amoled") Color.Black else Color(0xFF141414),
                onSurface = Color.White,
                background = if (settings.themeMode == "amoled") Color.Black else Color(0xFF141414),
                onBackground = Color.White
            )
        }
    }

    androidx.compose.material3.MaterialTheme(
        colorScheme = resolvedColorScheme,
        typography = customTypography
    ) {
        MetroBackgroundContainer(
            settings = settings,
            transparency = settings.backgroundTransparency,
            bgStyle = settings.backgroundStyle,
            themeMode = settings.themeMode,
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent
            ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Header standard Zune/WP layout
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Sliding tab Pivot Header mimicking original device slider design
                    ScrollableTabRow(
                        selectedTabIndex = if (selectedPlaylistIdDetail != null) 2 else activePivotIndex,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {},
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        listOf("Hub", "My Music", "Playlists", "Artists", "Albums", "Settings").forEachIndexed { index, label ->
                            val isActive = activePivotIndex == index && selectedPlaylistIdDetail == null
                            Tab(
                                selected = isActive,
                                onClick = {
                                    selectedPlaylistIdDetail = null
                                    activePivotIndex = index
                                },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isActive) (if (isLight) Color.Black else Color.White) else (if (isLight) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f)),
                                    fontFamily = getMetroFontFamily(settings.fontFamily),
                                    fontWeight = if (isActive) FontWeight.Light else FontWeight.ExtraLight,
                                    fontSize = if (isActive) 34.sp else 24.sp,
                                    letterSpacing = (-1).sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable workspace content
                Box(modifier = Modifier.weight(1f)) {
                    // Slide transition animation for panels
                    AnimatedContent(
                        targetState = if (selectedPlaylistIdDetail != null) 99 else activePivotIndex,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut()
                                )
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut()
                                )
                            }
                        },
                        label = "pivot_flow"
                    ) { index ->
                        when (index) {
                            0 -> {
                                // Pivot HUB (Live Tiles Screen)
                                val favoritesCount = remember(allSongs, settings.favoriteTrackIds) {
                                    val favIds = settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
                                    allSongs.count { it.id in favIds }
                                }
                                MainTilesHub(
                                     currentTrack = currentTrack,
                                     isPlaying = isPlaying,
                                     playbackPosition = playbackPosition,
                                     settings = settings,
                                    allSongs = allSongs,
                                    allSongsCount = allSongs.size,
                                    playlistsCount = playlists.size,
                                    favoritesCount = favoritesCount,
                                    onTileClick = { targetIndex ->
                                        activePivotIndex = targetIndex
                                    },
                                    onConsoleTrigger = {
                                        if (currentTrack != null) {
                                            isFullscreenConsoleVisible = true
                                        }
                                    },
                                    onPlayTrack = { track ->
                                        viewModel.playTrack(track, allSongs)
                                    },
                                    tileOrder = tileOrder,
                                    onSwapTiles = { id, direction ->
                                        val list = tileOrder.toMutableList()
                                        val idx = list.indexOf(id)
                                        if (idx != -1) {
                                            val targetIdx = idx + direction
                                            if (targetIdx in list.indices) {
                                                val tmp = list[idx]
                                                list[idx] = list[targetIdx]
                                                list[targetIdx] = tmp
                                                tileOrder = list
                                                viewModel.saveTileOrder(list)
                                            }
                                        }
                                    },
                                    onTileSpansChange = { newSpans ->
                                        viewModel.saveTileSpans(newSpans)
                                    },
                                    isEditMode = isTileEditMode,
                                    onEditModeChange = { isTileEditMode = it }
                                )
                            }
                            1 -> {
                                // Pivot MY MUSIC
                                MyMusicLibPanel(
                                    allSongs = allSongs,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    settings = settings,
                                    query = searchMusicQuery,
                                    onQueryChange = { searchMusicQuery = it },
                                    category = filterCategory,
                                    onCategoryChange = { filterCategory = it },
                                    onSortChange = { sBy, asc ->
                                        viewModel.saveMusicSort(sBy, asc)
                                    },
                                    onTrackPlay = { track, customQueue ->
                                        viewModel.playTrack(track, customQueue)
                                    },
                                    onToggleFavorite = { trackId ->
                                        viewModel.toggleFavorite(trackId)
                                    },
                                    isFavorite = { trackId ->
                                        favTrackIdsSet.contains(trackId)
                                    },
                                    onAddToPlaylistTrigger = { track ->
                                        showAddTrackToPlaylistDialog = track
                                    },
                                    hasPermission = hasStoragePermission,
                                    onRequestPermission = {
                                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            Manifest.permission.READ_MEDIA_AUDIO
                                        } else {
                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                        }
                                        permissionLauncher.launch(permission)
                                    }
                                )
                            }
                            2 -> {
                                // Pivot PLAYLISTS list
                                PlaylistsPanel(
                                    playlists = playlists,
                                    settings = settings,
                                    onCreateClick = { showCreatePlaylistDialog = true },
                                    onPlaylistSelect = { id ->
                                        viewModel.selectPlaylist(id)
                                        selectedPlaylistIdDetail = id
                                    },
                                    onDeleteClick = { id ->
                                        viewModel.deletePlaylist(id)
                                    }
                                )
                            }
                            3 -> {
                                // Pivot ARTISTS (Alphabetical artist grouping list)
                                ArtistsPanel(
                                    allSongs = allSongs,
                                    settings = settings,
                                    onTrackPlay = { track, customQueue ->
                                        viewModel.playTrack(track, customQueue)
                                    }
                                )
                            }
                            4 -> {
                                // Pivot ALBUMS (Grouped album list with cover artwork representation)
                                AlbumsPanel(
                                    allSongs = allSongs,
                                    settings = settings,
                                    onTrackPlay = { track, customQueue ->
                                        viewModel.playTrack(track, customQueue)
                                    }
                                )
                            }
                            5 -> {
                                // Pivot SETTINGS (Design + Theme Modes + Scanners + About Info)
                                SettingsPanel(
                                    settings = settings,
                                    onAccentChange = { viewModel.setAccentColor(it) },
                                    onFontChange = { viewModel.setFontFamily(it) },
                                    onTransparencyChange = { viewModel.setBackgroundTransparency(it) },
                                    onBgStyleChange = { viewModel.setBackgroundStyle(it) },
                                    onThemeChange = { viewModel.setThemeMode(it) },
                                    onFolderChange = { viewModel.setTargetMusicFolder(it) },
                                    onScanMusic = { viewModel.startLibraryScan(settings.targetMusicFolder) },
                                    onPreviewLyricsChange = { viewModel.setPreviewLyrics(it) },
                                    onLyricsFontChange = { viewModel.setLyricsFontFamily(it) },
                                    onLyricsSpacingChange = { viewModel.setLyricsSpacing(it) },
                                    onLyricsAlignmentChange = { viewModel.setLyricsAlignment(it) },
                                    onLyricsFontSizeChange = { viewModel.setLyricsFontSize(it) },
                                    onBgOpacityChange = { viewModel.setAppBackgroundSettings(settings.appBackgroundImage, settings.appBackgroundBlur, it) },
                                    onUploadCustomBackground = { localPath -> viewModel.setAppBackgroundSettings(localPath, settings.appBackgroundBlur, settings.appBackgroundOpacity) },
                                    onPlayerBgStyleChange = { viewModel.setPlayerBackgroundStyle(it) },
                                    onPlayerBgIntensityChange = { viewModel.setPlayerBackgroundIntensity(it) },
                                    onShowPlaceholderSongsChange = { viewModel.setShowPlaceholderSongs(it) },
                                    onEnableCoverArtChange = { viewModel.setEnableCoverArt(it) },
                                    onCoverArtResolutionChange = { viewModel.setCoverArtResolution(it) },
                                    onEnableListCoverArtChange = { viewModel.setEnableListCoverArt(it) },
                                    
                                    onRememberLastPlayedChange = { viewModel.setRememberLastPlayed(it) },
                                    onVisibleTilesChange = { viewModel.setVisibleTiles(it) },
                                    onCoverArtBorderThicknessChange = { viewModel.setCoverArtBorderThickness(it) },
                                    onArtistSeparatorsChange = { viewModel.setArtistSeparators(it) },
                                    onReRunOnboarding = { viewModel.setWelcomeCompleted(false) }
                                )
                            }
                            99 -> {
                                // SUBVIEW: Playlist Detail view
                                val selectedId = selectedPlaylistIdDetail
                                val activePlaylist = playlists.find { it.id == selectedId }
                                val playlistTracks by viewModel.currentPlaylistTracks.collectAsState()

                                if (activePlaylist != null) {
                                    PlaylistDetailView(
                                        playlistName = activePlaylist.name,
                                        playlistDesc = activePlaylist.description,
                                        playlistTracks = playlistTracks,
                                        currentTrack = currentTrack,
                                        settings = settings,
                                        onBackClick = {
                                            selectedPlaylistIdDetail = null
                                            viewModel.selectPlaylist(null)
                                        },
                                        onPlayTrack = { track ->
                                            viewModel.playTrack(track, playlistTracks)
                                        },
                                        onToggleFavorite = { trackId ->
                                            viewModel.toggleFavorite(trackId)
                                        },
                                        isFavorite = { trackId ->
                                            favTrackIdsSet.contains(trackId)
                                        },
                                        onRemoveTrack = { trackId ->
                                            viewModel.removeTrackFromPlaylist(activePlaylist.id, trackId)
                                        },
                                        allSongsList = allSongs,
                                        onAddSongToPlaylist = { track ->
                                            viewModel.addTrackToPlaylist(activePlaylist.id, track)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Mini Media Controls strip at coordinates bottom
                if (currentTrack != null) {
                    MiniPlaybackStrip(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        positionMs = playbackPosition,
                        durationMs = playbackDuration,
                        settings = settings,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.playNextTrack() },
                        onStripClick = { isFullscreenConsoleVisible = true },
                        onStopPlayback = { viewModel.stopPlayback() }
                    )
                }
            }
        }
    }

    // WELCOME OVERLAY SCREEN
    if (!settings.welcomeCompleted) {
        val isScanning by viewModel.isScanning.collectAsState()
        val scanProgress by viewModel.scanProgress.collectAsState()
        val scanStatusMessage by viewModel.scanStatusMessage.collectAsState()

        WelcomeScreen(
            settings = settings,
            isScanning = isScanning,
            scanProgress = scanProgress,
            scanStatusMessage = scanStatusMessage,
            onStartScan = { viewModel.startLibraryScan(settings.targetMusicFolder) },
            onThemeChange = { viewModel.setThemeMode(it) },
            onAccentChange = { viewModel.setAccentColor(it) },
            onGridlinesChange = { viewModel.setBackgroundStyle(it) },
            onLaunch = { viewModel.setWelcomeCompleted(true) },
            onUploadCustomBackground = { localPath -> viewModel.setAppBackgroundSettings(localPath, settings.appBackgroundBlur, settings.appBackgroundOpacity) },
            hasPermission = hasStoragePermission,
            onRequestPermission = {
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                permissionLauncher.launch(perm)
            }
        )
    }

    // GLOBAL BLOCKING SCANNING POPUP
    val globalIsScanning by viewModel.isScanning.collectAsState()
    val globalScanProgress by viewModel.scanProgress.collectAsState()
    val globalScanStatus by viewModel.scanStatusMessage.collectAsState()

    LaunchedEffect(globalIsScanning) {
        if (!globalIsScanning && viewModel.scanProgress.value >= 1.0f) {
            android.widget.Toast.makeText(context, "Scan completed successfully!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (globalIsScanning) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { /* Denied to dismiss */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (isLight) Color.White else Color(0xFF141414)
                ),
                border = androidx.compose.foundation.BorderStroke(3.dp, getThemeAccentColor(settings.accentColorHex))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "scanning music library".uppercase(),
                        color = getThemeAccentColor(settings.accentColorHex),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        text = "Indexing storage directories for media catalog synchronization...",
                        color = if (isLight) Color.Black else Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    androidx.compose.material3.LinearProgressIndicator(
                        progress = globalScanProgress,
                        color = getThemeAccentColor(settings.accentColorHex),
                        trackColor = (if (isLight) Color.Black else Color.White).copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )

                    Text(
                        text = "${(globalScanProgress * 100).toInt()}% • ${globalScanStatus}",
                        color = if (isLight) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // DIALOG: Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var newPlaylistName by remember { mutableStateOf("") }
        var newPlaylistDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            confirmButton = {
                MetroButton(
                    text = "save",
                    accentHex = settings.accentColorHex,
                    onClick = {
                        if (newPlaylistName.isNotEmpty()) {
                            viewModel.createPlaylist(newPlaylistName, newPlaylistDesc, settings.accentColorHex)
                            showCreatePlaylistDialog = false
                            newPlaylistName = ""
                            newPlaylistDesc = ""
                        }
                    }
                )
            },
            dismissButton = {
                MetroButton(
                    text = "cancel",
                    accentHex = settings.accentColorHex,
                    isOutlined = true,
                    onClick = {
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                        newPlaylistDesc = ""
                    }
                )
            },
            title = {
                Text(
                    text = "new playlist".uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = getMetroFontFamily(settings.fontFamily),
                    fontSize = 20.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetroTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = "playlist name"
                    )
                    MetroTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        placeholder = "description (optional)"
                    )
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = Color(0xFF151515)
        )
    }

    // DIALOG: Add Track to Playlist selection Dialog
    if (showAddTrackToPlaylistDialog != null) {
        val selectedTrack = showAddTrackToPlaylistDialog!!
        
        AlertDialog(
            onDismissRequest = { showAddTrackToPlaylistDialog = null },
            confirmButton = {},
            dismissButton = {
                MetroButton(
                    text = "close",
                    accentHex = settings.accentColorHex,
                    isOutlined = true,
                    onClick = { showAddTrackToPlaylistDialog = null }
                )
            },
            title = {
                Text(
                    text = "add to playlist".uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = getMetroFontFamily(settings.fontFamily)
                )
            },
            text = {
                if (playlists.isEmpty()) {
                    Text(
                        text = "no playlists available. create one in the playlists tab first!",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    ) {
                        items(playlists) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        viewModel.addTrackToPlaylist(item.id, selectedTrack)
                                        showAddTrackToPlaylistDialog = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name.lowercase(),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = "add",
                                    tint = themeAccentColor
                                )
                            }
                        }
                    }
                }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = Color(0xFF151515)
        )
    }

    // SCREEN SHEET: Fullscreen Immersive Playback Zune/Metro console
    AnimatedVisibility(
        visible = isFullscreenConsoleVisible,
        enter = slideInVertically { height -> height } + fadeIn(),
        exit = slideOutVertically { height -> height } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        val activeTrack = currentTrack
        if (activeTrack != null) {
            FullscreenPlaybackConsole(
                track = activeTrack,
                isPlaying = isPlaying,
                positionMs = playbackPosition,
                durationMs = playbackDuration,
                settings = settings,
                onTogglePlay = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.playNextTrack() },
                onSkipPrevious = { viewModel.playPreviousTrack() },
                onSeek = { viewModel.seekTo(it) },
                onFavoriteToggle = { viewModel.toggleFavorite(activeTrack.id) },
                isFavorite = favTrackIdsSet.contains(activeTrack.id),
                onDismiss = { isFullscreenConsoleVisible = false },
                isLyricsExpanded = isLyricsExpanded,
                onToggleLyricsExpanded = { isLyricsExpanded = it },
                queue = playbackQueueState,
                customLyricsMap = customLyricsMap,
                speedValue = playbackSpeedVal,
                pitchValue = playbackPitchVal,
                onPlayQueueTrack = { t -> viewModel.playTrack(t, playbackQueueState) },
                onUpdateLyrics = { id, text -> viewModel.updateLyricsForTrack(id, text) },
                onDeleteTrack = { t -> viewModel.deleteTrack(t) },
                onUpdatePlaybackParams = { s, p -> viewModel.updatePlaybackParams(s, p) },
                onTogglePinned = { viewModel.togglePinnedTrack(activeTrack.id) },
                isPinned = viewModel.isTrackPinned(activeTrack.id),
                isShuffle = isShuffle,
                isRepeat = isRepeat,
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeat() }
            )
        }
    }
    }
}

// ==========================================
// PANE COMPOSABLES
// ==========================================

// PANE 0: Tiles Hub
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainTilesHub(
    currentTrack: Track?,
    isPlaying: Boolean,
    playbackPosition: Long = 0L,
    settings: com.example.data.database.UserSettingsEntity,
    allSongs: List<Track>,
    allSongsCount: Int,
    playlistsCount: Int,
    favoritesCount: Int,
    onTileClick: (Int) -> Unit,
    onConsoleTrigger: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    tileOrder: List<String>,
    onSwapTiles: (String, Int) -> Unit,
    onTileSpansChange: (Map<String, Int>) -> Unit,
    isEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit
) {
    val gridState = remember { androidx.compose.foundation.lazy.grid.LazyGridState(0, 0) }
    LaunchedEffect(Unit) {
        gridState.scrollToItem(0)
    }

    val visibleTilesList = remember(settings.visibleTiles) {
        settings.visibleTiles.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    val filteredTileOrder = remember(tileOrder, visibleTilesList) {
        tileOrder.filter { it in visibleTilesList }
    }

    val pinnedTracks = remember(settings.pinnedTrackIds, allSongs) {
        val pinnedIds = settings.pinnedTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
        allSongs.filter { it.id in pinnedIds }.take(5)
    }

    val tileSpans = remember(settings.tileSpans) {
        val defaultMap = mapOf(
            "now_playing" to 2,
            "pinned_tracks" to 2,
            "my_music" to 1,
            "playlists" to 1,
            "favorites" to 1,
            "artists" to 1,
            "albums" to 1
        )
        if (settings.tileSpans.isNotEmpty()) {
            val map = mutableMapOf<String, Int>()
            settings.tileSpans.split(",").forEach { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().toIntOrNull() ?: 1
                    map[key] = value
                }
            }
            if (map.isNotEmpty()) {
                defaultMap + map
            } else {
                defaultMap
            }
        } else {
            defaultMap
        }
    }

    fun resizeTile(tileId: String, direction: String) {
        val current = tileSpans[tileId] ?: 1
        val updated = if (direction == "width") {
            if (current == 2) 1 else 2
        } else {
            if (current == 3) 1 else 3
        }
        val newSpans = tileSpans.toMutableMap().apply { put(tileId, updated) }
        onTileSpansChange(newSpans)
    }

    // Tracks which tile is currently held/dragged by the user to apply dynamic enlargement
    var activeDraggingTileId by remember { mutableStateOf<String?>(null) }

    // Custom drag gesture modifier that triggers swap operations AND local scale shifts during Edit Mode
    fun Modifier.dragToSwap(tileId: String): Modifier = if (isEditMode) {
        this.pointerInput(tileId) {
            var accumulatedY = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    accumulatedY = 0f
                    activeDraggingTileId = tileId
                },
                onDragEnd = {
                    accumulatedY = 0f
                    activeDraggingTileId = null
                },
                onDragCancel = {
                    accumulatedY = 0f
                    activeDraggingTileId = null
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    accumulatedY += dragAmount.y
                    if (accumulatedY > 150f) {
                        onSwapTiles(tileId, 1) // Slide down
                        accumulatedY = 0f
                    } else if (accumulatedY < -150f) {
                        onSwapTiles(tileId, -1) // Slide up
                        accumulatedY = 0f
                    }
                }
            )
        }
    } else {
        this
    }

    // Resolve system themes
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isLightGrid = when (settings.themeMode) {
        "light" -> true
        "dark" -> false
        "amoled" -> false
        else -> !isSystemDark
    }

    @Composable
    fun ResizeHandles(tileId: String, modifier: Modifier = Modifier) {
        val accentColor = getThemeAccentColor(settings.accentColorHex)
        Box(modifier = modifier.fillMaxSize()) {
            // Right handle wrapper (width)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .align(Alignment.CenterEnd)
                    .clickable { resizeTile(tileId, "width") },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(6.dp)
                        .height(32.dp)
                        .background(accentColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                )
            }

            // Bottom handle wrapper (height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .clickable { resizeTile(tileId, "height") },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .width(32.dp)
                        .height(6.dp)
                        .background(accentColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isEditMode) {
                detectTapGestures(
                    onLongPress = {
                        if (!isEditMode) {
                            onEditModeChange(true)
                        }
                    },
                    onTap = {
                        if (isEditMode) {
                            onEditModeChange(false)
                        }
                    }
                )
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp, start = 4.dp, end = 4.dp) // extra bottom padding for the edit bar
        ) {
            items(
                items = filteredTileOrder,
                key = { it },
                span = { tileId ->
                    val sizeType = tileSpans[tileId] ?: 1
                    val spanVal = if (sizeType == 2) 2 else 1
                    androidx.compose.foundation.lazy.grid.GridItemSpan(spanVal)
                }
            ) { tileId ->
                val sizeType = tileSpans[tileId] ?: 1
                val spanVal = if (sizeType == 2) 2 else 1
                
                // Dynamic scale matching original Windows Phone transitions
                val finalScale = when {
                    tileId == activeDraggingTileId -> 1.05f
                    isEditMode -> 0.92f
                    else -> 1.0f
                }
                val animatedScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = finalScale,
                    label = "tile_scale_$tileId"
                )

                when (tileId) {
                    "now_playing" -> {
                        val tileBg = if (currentTrack != null) {
                            getThemeAccentColor(settings.accentColorHex)
                        } else {
                            if (isLightGrid) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)
                        }.copy(alpha = (1f - settings.backgroundTransparency).coerceAtLeast(0.1f))

                        val gridTileTextColor = if (isLightGrid && settings.backgroundTransparency > 0.45f) Color.Black else Color.White
                        val gridTileTextSecondaryColor = if (isLightGrid && settings.backgroundTransparency > 0.45f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
                        val gridTileBorderColor = if (isLightGrid && settings.backgroundTransparency > 0.45f) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(animatedScale)
                                .animateItemPlacement()
                        ) {
                            Card(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth()
                                    .then(
                                        when (sizeType) {
                                            2 -> Modifier.aspectRatio(2f)
                                            3 -> Modifier.aspectRatio(0.48f)
                                            else -> Modifier.aspectRatio(1f)
                                        }
                                    )
                                    .clickable { if (!isEditMode) onConsoleTrigger() }
                                    .dragToSwap("now_playing")
                                    .border(2.dp, gridTileBorderColor),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                colors = CardDefaults.cardColors(containerColor = tileBg)
                            ) {
                                when (sizeType) {
                                    1 -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "NOW PLAYING",
                                                    color = gridTileTextColor.copy(alpha = 0.8f),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    letterSpacing = 0.5.sp
                                                )
                                                if (isPlaying) {
                                                    Icon(
                                                        imageVector = Icons.Default.VolumeUp,
                                                        contentDescription = null,
                                                        tint = getThemeAccentColor(settings.accentColorHex),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }

                                            if (currentTrack != null) {
                                                Column {
                                                    Text(
                                                        text = currentTrack.title,
                                                        color = gridTileTextColor,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Clip,
                                                        modifier = Modifier.basicMarquee()
                                                    )
                                                    Text(
                                                        text = currentTrack.artist,
                                                        color = gridTileTextSecondaryColor,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Light,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = "No Audio",
                                                    color = gridTileTextColor.copy(alpha = 0.4f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Light
                                                )
                                            }
                                        }
                                    }
                                    3 -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "NOW PLAYING",
                                                color = gridTileTextColor.copy(alpha = 0.8f),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                letterSpacing = 0.5.sp
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (currentTrack != null) {
                                                    com.example.ui.components.TrackCoverImage(
                                                        track = currentTrack,
                                                        resolution = settings.coverArtResolution,
                                                        modifier = Modifier
                                                            .size(80.dp)
                                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                                            .border(1.dp, gridTileTextColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                                        fallbackSymbol = "♬",
                                                        symbolFontSize = 32.sp,
                                                        themeAccentColor = getThemeAccentColor(settings.accentColorHex),
                                                        isLight = isLightGrid
                                                    )
                                                    if (false) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = getThemeAccentColor(settings.accentColorHex).copy(alpha = 0.8f),
                                                            modifier = Modifier.size(52.dp)
                                                        )
                                                         MetroVisualizer(
                                                             accentHex = settings.accentColorHex,
                                                             isPlaying = isPlaying,
                                                             modifier = Modifier.height(30.dp).width(70.dp), positionMs = playbackPosition, trackId = currentTrack.id
                                                         )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.VolumeMute,
                                                        contentDescription = null,
                                                        tint = gridTileTextColor.copy(alpha = 0.2f),
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                }
                                            }

                                            if (currentTrack != null) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Text(
                                                        text = currentTrack.title,
                                                        color = gridTileTextColor,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Clip,
                                                        modifier = Modifier.basicMarquee()
                                                    )
                                                    Text(
                                                        text = currentTrack.artist,
                                                        color = gridTileTextSecondaryColor,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = "Empty queue",
                                                    color = gridTileTextColor.copy(alpha = 0.4f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Light,
                                                    modifier = Modifier.align(Alignment.Start)
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight(),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Now Playing",
                                                    color = gridTileTextColor.copy(alpha = 0.8f),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    letterSpacing = 1.sp
                                                )

                                                if (currentTrack != null) {
                                                    Column {
                                                        Text(
                                                            text = currentTrack.title,
                                                            color = gridTileTextColor,
                                                            fontSize = 19.sp,
                                                            fontWeight = FontWeight.Light,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Clip,
                                                            modifier = Modifier.basicMarquee()
                                                        )
                                                        Text(
                                                            text = currentTrack.artist,
                                                            color = gridTileTextSecondaryColor,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.ExtraLight,
                                                            maxLines = 1
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = "No Audio Queued",
                                                        color = gridTileTextColor.copy(alpha = 0.4f),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Light
                                                    )
                                                }

                                                // Status subtitle labels
                                                Text(
                                                    text = if (isPlaying) "Tap to inspect console" else "Idle",
                                                    color = gridTileTextColor.copy(alpha = 0.4f),
                                                    fontSize = 11.sp
                                                )
                                            }

                                            if (currentTrack != null && spanVal == 2) {
                                                if (true) {
                                                    com.example.ui.components.TrackCoverImage(
                                                        track = currentTrack,
                                                        resolution = settings.coverArtResolution,
                                                        modifier = Modifier
                                                            .size(64.dp)
                                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                                            .border(1.dp, gridTileTextColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                                        fallbackSymbol = "♬",
                                                        symbolFontSize = 26.sp,
                                                        themeAccentColor = getThemeAccentColor(settings.accentColorHex),
                                                        isLight = isLightGrid
                                                    )
                                                } else {
                                                    MetroVisualizer(
                                                        accentHex = settings.accentColorHex,
                                                        isPlaying = isPlaying,
                                                        modifier = Modifier.padding(start = 8.dp).width(90.dp).height(48.dp), positionMs = playbackPosition,
                                                        trackId = currentTrack.id
                                                    )
                                                }
                                            } else if (false && currentTrack != null && spanVal == 2) {
                                                MetroVisualizer(
                                                    accentHex = settings.accentColorHex,
                                                    isPlaying = isPlaying,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            } else if (spanVal == 2) {
                                                Icon(
                                                    imageVector = Icons.Default.VolumeUp,
                                                    contentDescription = null,
                                                    tint = gridTileTextColor.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (isEditMode) {
                                ResizeHandles(
                                    tileId = "now_playing",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            when (sizeType) {
                                                2 -> Modifier.aspectRatio(2f)
                                                3 -> Modifier.aspectRatio(0.48f)
                                                else -> Modifier.aspectRatio(1f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                    "my_music" -> {
                        Box(
                            modifier = Modifier
                                .scale(animatedScale)
                                .animateItemPlacement()
                        ) {
                            MetroTile(
                                title = "My Music",
                                icon = Icons.Default.MusicNote,
                                accentHex = settings.accentColorHex,
                                transparency = settings.backgroundTransparency,
                                subtitle = "$allSongsCount tracks",
                                sizeType = sizeType,
                                onClick = { if (!isEditMode) onTileClick(1) },
                                modifier = Modifier.dragToSwap("my_music")
                            )
                            if (isEditMode) {
                                ResizeHandles(
                                    tileId = "my_music",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            when (sizeType) {
                                                2 -> Modifier.aspectRatio(2f)
                                                3 -> Modifier.aspectRatio(0.48f)
                                                else -> Modifier.aspectRatio(1f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                    "playlists" -> {
                        Box(
                            modifier = Modifier
                                .scale(animatedScale)
                                .animateItemPlacement()
                        ) {
                            MetroTile(
                                title = "Playlists",
                                icon = Icons.Default.QueueMusic,
                                accentHex = settings.accentColorHex,
                                transparency = settings.backgroundTransparency,
                                subtitle = "$playlistsCount total",
                                sizeType = sizeType,
                                onClick = { if (!isEditMode) onTileClick(2) },
                                modifier = Modifier.dragToSwap("playlists")
                            )
                            if (isEditMode) {
                                ResizeHandles(
                                    tileId = "playlists",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            when (sizeType) {
                                                2 -> Modifier.aspectRatio(2f)
                                                3 -> Modifier.aspectRatio(0.48f)
                                                else -> Modifier.aspectRatio(1f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                    "favorites" -> {
                        Box(
                            modifier = Modifier
                                .scale(animatedScale)
                                .animateItemPlacement()
                        ) {
                            MetroTile(
                                title = "Favorites",
                                icon = Icons.Default.Favorite,
                                accentHex = settings.accentColorHex,
                                transparency = settings.backgroundTransparency,
                                subtitle = "$favoritesCount liked",
                                sizeType = sizeType,
                                onClick = { if (!isEditMode) onTileClick(1) }, // Navigate to My Music
                                modifier = Modifier.dragToSwap("favorites")
                            )
                            if (isEditMode) {
                                ResizeHandles(
                                    tileId = "favorites",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            when (sizeType) {
                                                2 -> Modifier.aspectRatio(2f)
                                                3 -> Modifier.aspectRatio(0.48f)
                                                else -> Modifier.aspectRatio(1f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                    "pinned_tracks" -> {
                        val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
                        val tileBg = themeAccentColor.copy(alpha = (1f - settings.backgroundTransparency).coerceAtLeast(0.1f))
                        val gridTileTextColor = if (isLightGrid && settings.backgroundTransparency > 0.45f) Color.Black else Color.White
                        val gridTileTextSecondaryColor = if (isLightGrid && settings.backgroundTransparency > 0.45f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
                        val gridTileBorderColor = if (isLightGrid && settings.backgroundTransparency > 0.45f) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(animatedScale)
                                .animateItemPlacement()
                        ) {
                            Card(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth()
                                    .then(
                                        when (sizeType) {
                                            2 -> Modifier.aspectRatio(2f)
                                            3 -> Modifier.aspectRatio(0.48f)
                                            else -> Modifier.aspectRatio(1f)
                                        }
                                    )
                                    .dragToSwap("pinned_tracks")
                                    .border(2.dp, gridTileBorderColor),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                colors = CardDefaults.cardColors(containerColor = tileBg)
                            ) {
                                when (sizeType) {
                                    1 -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = null,
                                                    tint = gridTileTextColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "PINNED",
                                                    color = gridTileTextColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }

                                            if (pinnedTracks.isEmpty()) {
                                                Text(
                                                    text = "No pins",
                                                    color = gridTileTextColor.copy(alpha = 0.4f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Light
                                                )
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    pinnedTracks.take(1).forEach { track ->
                                                        val isCurrentPlay = currentTrack?.id == track.id
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable { if (!isEditMode) onPlayTrack(track) }
                                                        ) {
                                                            Text(
                                                                text = track.title,
                                                                color = if (isCurrentPlay) themeAccentColor else gridTileTextColor,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Normal,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = track.artist,
                                                                color = gridTileTextSecondaryColor,
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    if (pinnedTracks.size > 1) {
                                                        Text(
                                                            text = "+${pinnedTracks.size - 1} more pinned",
                                                            color = gridTileTextSecondaryColor,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraLight
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    3 -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = null,
                                                        tint = gridTileTextColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "PINNED",
                                                        color = gridTileTextColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                                Text(
                                                    text = "${pinnedTracks.size}",
                                                    color = gridTileTextColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }

                                            if (pinnedTracks.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "No pinned items",
                                                        color = gridTileTextColor.copy(alpha = 0.4f),
                                                        fontSize = 11.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            } else {
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    pinnedTracks.take(4).forEach { track ->
                                                        val isCurrentPlay = currentTrack?.id == track.id
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(if (isCurrentPlay) gridTileTextColor.copy(alpha = 0.08f) else Color.Transparent)
                                                                .clickable { if (!isEditMode) onPlayTrack(track) }
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = track.title,
                                                                color = if (isCurrentPlay) themeAccentColor else gridTileTextColor,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Normal,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = track.artist,
                                                                color = gridTileTextSecondaryColor,
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Text(
                                                text = "Top Pinned",
                                                color = gridTileTextColor.copy(alpha = 0.4f),
                                                fontSize = 10.sp,
                                                modifier = Modifier.align(Alignment.Start)
                                            )
                                        }
                                    }
                                    else -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = null,
                                                        tint = gridTileTextColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "PINNED TRACKS",
                                                        color = gridTileTextColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        letterSpacing = 1.sp
                                                    )
                                                }
                                                if (pinnedTracks.isNotEmpty()) {
                                                    Text(
                                                        text = "${pinnedTracks.size} tracks",
                                                        color = gridTileTextSecondaryColor,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            if (pinnedTracks.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(
                                                            text = "No pinned songs",
                                                            color = gridTileTextColor.copy(alpha = 0.6f),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = "Pin songs from options drawer in Player screen",
                                                            color = gridTileTextColor.copy(alpha = 0.4f),
                                                            fontSize = 10.sp,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            } else {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    pinnedTracks.take(2).forEach { track ->
                                                        val isCurrentPlay = currentTrack?.id == track.id
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(if (isCurrentPlay) gridTileTextColor.copy(alpha = 0.12f) else Color.Transparent)
                                                                .clickable { 
                                                                    if (!isEditMode) {
                                                                        onPlayTrack(track) 
                                                                    }
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = track.title,
                                                                    color = if (isCurrentPlay) themeAccentColor else gridTileTextColor,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = if (isCurrentPlay) FontWeight.Bold else FontWeight.Medium,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = track.artist,
                                                                    color = gridTileTextSecondaryColor,
                                                                    fontSize = 10.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                            if (isCurrentPlay && isPlaying) {
                                                                Icon(
                                                                    imageVector = Icons.Default.VolumeUp,
                                                                    contentDescription = "Playing",
                                                                    tint = themeAccentColor,
                                                                    modifier = Modifier.size(16.dp)
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
                            if (isEditMode) {
                                ResizeHandles(
                                    tileId = "pinned_tracks",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            when (sizeType) {
                                                2 -> Modifier.aspectRatio(2f)
                                                3 -> Modifier.aspectRatio(0.48f)
                                                else -> Modifier.aspectRatio(1f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                    "artists" -> {
                        Box(
                            modifier = Modifier
                                .scale(animatedScale)
                                .animateItemPlacement()
                        ) {
                            MetroTile(
                                title = "Artists",
                                icon = Icons.Default.Person,
                                accentHex = settings.accentColorHex,
                                transparency = settings.backgroundTransparency,
                                subtitle = "Library artists",
                                sizeType = sizeType,
                                onClick = { if (!isEditMode) onTileClick(3) },
                                modifier = Modifier.dragToSwap("artists")
                            )
                            if (isEditMode) {
                                ResizeHandles(
                                    tileId = "artists",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            when (sizeType) {
                                                2 -> Modifier.aspectRatio(2f)
                                                3 -> Modifier.aspectRatio(0.48f)
                                                else -> Modifier.aspectRatio(1f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                    "albums" -> {
                        Box(
                            modifier = Modifier
                                .scale(animatedScale)
                                .animateItemPlacement()
                        ) {
                            MetroTile(
                                title = "Albums",
                                icon = Icons.Default.Album,
                                accentHex = settings.accentColorHex,
                                transparency = settings.backgroundTransparency,
                                subtitle = "Library albums",
                                sizeType = sizeType,
                                onClick = { if (!isEditMode) onTileClick(4) },
                                modifier = Modifier.dragToSwap("albums")
                            )
                            if (isEditMode) {
                                ResizeHandles(
                                    tileId = "albums",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            when (sizeType) {
                                                2 -> Modifier.aspectRatio(2f)
                                                3 -> Modifier.aspectRatio(0.48f)
                                                else -> Modifier.aspectRatio(1f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overlay banner at the bottom when Edit Mode is active to exit gracefully
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.5.dp, getThemeAccentColor(settings.accentColorHex))
                    .clickable { onEditModeChange(false) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "edit mode active • tap grid empty space or here to exit".lowercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = getMetroFontFamily(settings.fontFamily),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// PANE 1: My Music Panels
@Composable
fun MyMusicLibPanel(
    allSongs: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    settings: com.example.data.database.UserSettingsEntity,
    query: String,
    onQueryChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    onSortChange: (String, Boolean) -> Unit,
    onTrackPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    isFavorite: (String) -> Boolean,
    onAddToPlaylistTrigger: (Track) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isLight = when (settings.themeMode) {
        "light" -> true
        "dark" -> false
        "amoled" -> false
        else -> !isSystemDark
    }
    val cardBg = if (isLight) Color(0xFFF3F3F3) else Color(0xFF111111)
    val textSec = if (isLight) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)

    var sortBy by androidx.compose.runtime.remember(settings.musicSortBy) { androidx.compose.runtime.mutableStateOf(settings.musicSortBy) }
    var isAscending by androidx.compose.runtime.remember(settings.musicSortAscending) { androidx.compose.runtime.mutableStateOf(settings.musicSortAscending) }
    var isSortMenuExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val dynamicGenres = androidx.compose.runtime.remember(allSongs) {
        allSongs
            .map { it.genre.trim() }
            .filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Ambient Synth", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    // Filter and Sort tracks dynamically (Extremely optimized non-stuttering configuration!)
    val sortedTracks = androidx.compose.runtime.remember(allSongs, query, category, sortBy, isAscending, settings.favoriteTrackIds) {
        val favIds = settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
        val filtered = allSongs.filter { track ->
            val matchesQuery = track.title.lowercase().contains(query.lowercase()) ||
                    track.artist.lowercase().contains(query.lowercase())
            val matchesCat = when {
                category == "favorites" -> track.id in favIds
                category == "all" -> true
                category.startsWith("genre_") -> {
                    val filterGenre = category.removePrefix("genre_")
                    track.genre.trim().lowercase() == filterGenre.lowercase()
                }
                else -> true
            }
            matchesQuery && matchesCat
        }

        val comparator = Comparator<Track> { t1, t2 ->
            val key1 = when (sortBy) {
                "A-Z" -> t1.title.lowercase()
                "Z-A" -> t1.title.lowercase()
                "Artists" -> t1.artist.lowercase()
                "Album" -> t1.album.ifEmpty { "unknown album" }.lowercase()
                "Date Added" -> t1.id.lowercase()
                "Date Updated" -> (t1.artist + t1.title).lowercase()
                else -> t1.title.lowercase()
            }
            val key2 = when (sortBy) {
                "A-Z" -> t2.title.lowercase()
                "Z-A" -> t2.title.lowercase()
                "Artists" -> t2.artist.lowercase()
                "Album" -> t2.album.ifEmpty { "unknown album" }.lowercase()
                "Date Added" -> t2.id.lowercase()
                "Date Updated" -> (t2.artist + t2.title).lowercase()
                else -> t2.title.lowercase()
            }
            if (sortBy == "Z-A") {
                key2.compareTo(key1)
            } else {
                key1.compareTo(key2)
            }
        }
        val sorted = filtered.sortedWith(comparator)
        if (!isAscending) sorted.reversed() else sorted
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        // Inline Search field
        MetroTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search title, artist, album...",
            leadingIcon = Icons.Default.Search,
            onClear = { onQueryChange("") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category and Sort controls separated: Left (scrolling categories), Right (fixed sort button)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Scrollable Categories
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val categoryList = mutableListOf(
                    "all" to "All Music",
                    "favorites" to "Liked"
                ).apply {
                    dynamicGenres.forEach { genre ->
                        add("genre_$genre" to genre)
                    }
                }
                categoryList.forEach { (id, label) ->
                    val isSel = category == id
                    val containerColor = if (isSel) themeAccentColor else (if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.07f))
                    val contentColor = if (isSel) Color.White else (if (isLight) Color.Black else Color.White)
                    
                    Button(
                        onClick = { focusManager.clearFocus(); onCategoryChange(id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = label, // Capitalized!
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // High-contrast Metro dividing line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(if (isLight) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f))
            )

            // Dynamic sort options trigger in a gorgeous Metro style dropdown (fixed on the right)
            Box {
                val containerColor = if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.07f)
                val contentColor = if (isLight) Color.Black else Color.White
                Button(
                    onClick = { isSortMenuExpanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val directionLabel = if (isAscending) "▲" else "▼"
                        Text(
                            text = "Sort: $sortBy $directionLabel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown icon",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isSortMenuExpanded,
                    onDismissRequest = { isSortMenuExpanded = false },
                    modifier = Modifier
                        .background(if (isLight) Color.White else if (settings.themeMode == "amoled") Color.Black else Color(0xFF141414))
                        .border(1.dp, themeAccentColor, androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                ) {
                    // Ascending & Descending options at the top
                    listOf(true to "Ascending", false to "Descending").forEach { (asc, label) ->
                        val isSel = isAscending == asc
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) themeAccentColor else (if (isLight) Color.Black else Color.White)
                                )
                            },
                            onClick = {
                                isAscending = asc
                                onSortChange(sortBy, asc)
                                isSortMenuExpanded = false
                            }
                        )
                    }

                    // High contrast Metro styled Horizontal Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isLight) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f))
                    )

                    val sortOptions = listOf("A-Z", "Z-A", "Date Added", "Date Updated", "Artists", "Album")
                    sortOptions.forEach { opt ->
                        val isSel = sortBy == opt
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = opt,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) themeAccentColor else (if (isLight) Color.Black else Color.White)
                                )
                            },
                            onClick = {
                                sortBy = opt
                                onSortChange(opt, isAscending)
                                isSortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tracks Listing Container
        val emptyTint = if (isLight) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)
        val emptyText = if (isLight) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f)
        val dividerColor = if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f)

        if (sortedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = emptyTint
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No songs matched results",
                        color = emptyText,
                        fontWeight = FontWeight.Light,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = sortedTracks,
                    key = { item -> item.id },
                    contentType = { "track" }
                ) { item ->
                    val playLambda = remember(item.id, sortedTracks) { { focusManager.clearFocus(); onTrackPlay(item, sortedTracks) } }
                    val favLambda = remember(item.id) { { onToggleFavorite(item.id) } }
                    val addLambda = remember(item.id) { { onAddToPlaylistTrigger(item) } }
                    PlaylistTrackItem(
                        track = item,
                        accentHex = settings.accentColorHex,
                        isCurrent = currentTrack?.id == item.id,
                        onPlay = playLambda,
                        onToggleFavorite = favLambda,
                        isFavorite = isFavorite(item.id),
                        fontFamily = settings.fontFamily,
                        onAddToPlaylist = addLambda,
                        isLightMode = isLight,
                        enableCoverArt = settings.enableCoverArt && settings.enableListCoverArt,
                        coverArtResolution = "low",
                        cornerStyle = settings.cornerCoverArt,
                        showDivider = true,
                        dividerColor = dividerColor
                    )
                }
            }
        }
    }
}

// PANE 2: Playlists panels
@Composable
fun PlaylistsPanel(
    playlists: List<com.example.data.database.PlaylistEntity>,
    settings: com.example.data.database.UserSettingsEntity,
    onCreateClick: () -> Unit,
    onPlaylistSelect: (Int) -> Unit,
    onDeleteClick: (Int) -> Unit
) {
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isLight = when (settings.themeMode) {
        "light" -> true
        "dark" -> false
        "amoled" -> false
        else -> !isSystemDark
    }
    val tileTextColor = if (isLight && settings.backgroundTransparency > 0.45f) Color.Black else Color.White
    val tileTextSecondaryColor = if (isLight && settings.backgroundTransparency > 0.45f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
    val tileBorderColor = if (isLight && settings.backgroundTransparency > 0.45f) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroButton(
                text = "Add Playlist",
                accentHex = settings.accentColorHex,
                onClick = onCreateClick
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = themeAccentColor.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "no custom playlists created yet.",
                        color = if (isLight) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "tap the button above to assemble songs into a customized Azune listening stream.",
                        color = if (isLight) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(playlists) { item ->
                    val accentColor = getThemeAccentColor(item.accentHex)
                    val tileBg = accentColor.copy(alpha = (1f - settings.backgroundTransparency).coerceAtLeast(0.1f))

                    Card(
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1.2f)
                            .clickable { onPlaylistSelect(item.id) }
                            .border(1.dp, tileBorderColor),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(containerColor = tileBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = tileTextColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.name,
                                    color = tileTextColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.description,
                                    color = tileTextSecondaryColor,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 13.sp
                                )
                            }

                            // Quick delete tag
                            IconButton(
                                onClick = { onDeleteClick(item.id) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = tileTextColor.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// PANE 3: Artists Panel
@Composable
fun ArtistsPanel(
    allSongs: List<Track>,
    settings: com.example.data.database.UserSettingsEntity,
    onTrackPlay: (Track, List<Track>) -> Unit
) {
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
    val textPrimaryColor = if (settings.themeMode == "light") Color.Black else Color.White
    val textSecondaryColor = if (settings.themeMode == "light") Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)

    // Group songs by artist name, splitting on collaborative separators
    val groupedByArtist = remember(allSongs, settings.artistSeparators) {
        val separators = settings.artistSeparators.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("/", ",", "feat.", "ft.", "&", "and") }

        val map = java.util.TreeMap<String, MutableList<Track>>(String.CASE_INSENSITIVE_ORDER)
        
        allSongs.forEach { song ->
            var list = listOf(song.artist)
            separators.forEach { sep ->
                list = list.flatMap { sub -> 
                    sub.split(Regex("(?i)${Regex.escape(sep)}"))
                }
            }
            val individualArtists = list.map { it.trim() }.filter { it.isNotEmpty() }
            val finalArtists = if (individualArtists.isEmpty()) listOf(song.artist) else individualArtists
            
            finalArtists.forEach { artist ->
                if (artist.isNotEmpty()) {
                    map.getOrPut(artist) { mutableListOf() }.add(song)
                }
            }
        }
        map
    }

    if (groupedByArtist.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "no artists indexed yet.",
                color = textSecondaryColor,
                fontFamily = getMetroFontFamily(settings.fontFamily),
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            groupedByArtist.forEach { (artistName, songs) ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = artistName.lowercase(),
                            color = if (settings.themeMode == "light") Color.Black else themeAccentColor,
                            fontFamily = getMetroFontFamily(settings.fontFamily),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "${songs.size} " + if (songs.size > 1) "tracks" else "track",
                            color = textSecondaryColor,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        songs.forEach { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTrackPlay(song, songs) }
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                                    .background(Color.White.copy(alpha = 0.02f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = song.title,
                                        color = textPrimaryColor,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (song.album.isNotEmpty()) song.album.lowercase() else "unknown album",
                                        color = textSecondaryColor,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    Divider(color = textPrimaryColor.copy(alpha = 0.08f))
                }
            }
        }
    }
}

// PANE 3.5: Albums Panel
@Composable
fun AlbumsPanel(
    allSongs: List<Track>,
    settings: com.example.data.database.UserSettingsEntity,
    onTrackPlay: (Track, List<Track>) -> Unit
) {
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
    val textPrimaryColor = if (settings.themeMode == "light") Color.Black else Color.White
    val textSecondaryColor = if (settings.themeMode == "light") Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)

    // Group songs by album name
    val groupedByAlbum = remember(allSongs) {
        allSongs.groupBy { it.album.ifEmpty { "Unknown Album" } }.toSortedMap()
    }

    if (groupedByAlbum.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "no albums indexed yet.",
                color = textSecondaryColor,
                fontFamily = getMetroFontFamily(settings.fontFamily),
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            groupedByAlbum.forEach { (albumName, songs) ->
                item {
                    val albumArtist = songs.firstOrNull()?.artist ?: "Unknown Artist"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Pseudo-CD album visual tile
                            if (settings.enableCoverArt && songs.firstOrNull() != null) {
                                com.example.ui.components.TrackCoverImage(
                                    track = songs.first(),
                                    resolution = settings.coverArtResolution,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .border(1.dp, themeAccentColor, androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
                                    fallbackSymbol = "💿",
                                    symbolFontSize = 32.sp,
                                    themeAccentColor = themeAccentColor,
                                    isLight = (settings.themeMode == "light")
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(themeAccentColor.copy(alpha = 0.15f))
                                        .border(1.dp, themeAccentColor, androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = themeAccentColor,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = albumName.lowercase(),
                                    color = if (settings.themeMode == "light") Color.Black else themeAccentColor,
                                    fontFamily = getMetroFontFamily(settings.fontFamily),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(
                                    text = "by ${albumArtist.lowercase()}",
                                    color = textPrimaryColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${songs.size} " + if (songs.size > 1) "tracks" else "track",
                                    color = textSecondaryColor,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subtracks
                        songs.forEach { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTrackPlay(song, songs) }
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                                    .background(Color.White.copy(alpha = 0.02f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = song.title,
                                    color = textPrimaryColor,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Divider(color = textPrimaryColor.copy(alpha = 0.08f))
                }
            }
        }
    }
}

// PANE 4: Settings options Panel (Personalization, Directory structure, Themes, Details)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsPanel(
    settings: com.example.data.database.UserSettingsEntity,
    onAccentChange: (String) -> Unit,
    onFontChange: (String) -> Unit,
    onTransparencyChange: (Float) -> Unit,
    onBgStyleChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onFolderChange: (String) -> Unit,
    onScanMusic: () -> Unit,
    onPreviewLyricsChange: (Boolean) -> Unit,
    onLyricsFontChange: (String) -> Unit,
    onLyricsSpacingChange: (String) -> Unit,
    onLyricsAlignmentChange: (String) -> Unit,
    onLyricsFontSizeChange: (String) -> Unit,
    onBgOpacityChange: (Float) -> Unit,
    onUploadCustomBackground: (String) -> Unit,
    onPlayerBgStyleChange: (String) -> Unit,
    onPlayerBgIntensityChange: (Float) -> Unit,
    onShowPlaceholderSongsChange: (Boolean) -> Unit,
    onEnableCoverArtChange: (Boolean) -> Unit,
    onCoverArtResolutionChange: (String) -> Unit,
    onEnableListCoverArtChange: (Boolean) -> Unit,
    
    onRememberLastPlayedChange: (Boolean) -> Unit,
    onVisibleTilesChange: (String) -> Unit,
    onCoverArtBorderThicknessChange: (String) -> Unit,
    onArtistSeparatorsChange: (String) -> Unit,
    onReRunOnboarding: () -> Unit = {}
) {
    // Determine active theme colors dynamically (allowing Follow System option)
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isLight = when (settings.themeMode) {
        "light" -> true
        "dark" -> false
        "amoled" -> false
        else -> !isSystemDark
    }
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
    val textPrimaryColor = if (isLight) Color.Black else Color.White
    val textSecondaryColor = if (isLight) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)

    var activeDialog by remember { mutableStateOf<String?>(null) }
    var showPrivacyPolicyFullScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val localPath = copyUriToLocalFile(context, uri)
            if (localPath.isNotEmpty()) {
                onUploadCustomBackground(localPath)
                onBgStyleChange("upload")
            }
        }
    }

    // Custom folder browser states
    val storageDir = java.io.File("/storage/emulated/0")
    val defaultFolders = listOf("Music", "Download", "Documents", "DCIM", "Pictures", "Podcasts", "Alarms", "Notifications", "Ringtones")
    val foldersList = remember {
        if (storageDir.exists() && storageDir.isDirectory) {
            storageDir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }?.toList()?.sortedBy { it.name } ?: emptyList()
        } else {
            defaultFolders.map { java.io.File(storageDir, it) }
        }
    }

    // Helper functions for options descriptions
    val currentThemeLabel = when (settings.themeMode) {
        "dark" -> "Dark mode"
        "amoled" -> "Amoled dark"
        "light" -> "Aero Light"
        else -> "Follow System"
    }
    val currentFontLabel = when (settings.fontFamily) {
        "Inter" -> "Inter (Default)"
        "Segoe" -> "Inter (Default)"
        "space" -> "Space Grotesk tech style"
        else -> "Inter (Default)"
    }
    val currentFolderLabel = if (settings.targetMusicFolder == "All") "Default /0/ Scanner" else settings.targetMusicFolder

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Group 1: Appearance & Style
        item {
            Text(
                text = "Appearance & Style",
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily),
                modifier = Modifier.padding(top = 10.dp)
            )
            Divider(color = textPrimaryColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
        }

        // Row 1: Theme selection
        item {
            SettingsListRow(
                title = "Theme Mode",
                description = "Choose between Dark mode, Amoled dark, Aero Light, or Follow System. Current: $currentThemeLabel",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Brightness6,
                onClick = { activeDialog = "theme" }
            )
        }

        // Row 2: Accent selection
        item {
            SettingsListRow(
                title = "Accent Color",
                description = "Adjust dynamic highlights on live tiles. Current: ${getAccentColorName(settings.accentColorHex)}",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Palette,
                onClick = { activeDialog = "accent" }
            )
        }

        // Row 3: Font Adjustment
        item {
            SettingsListRow(
                title = "App Font Family",
                description = "Configure text and typography header layout styles. Current: $currentFontLabel",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.TextFields,
                onClick = { activeDialog = "font" }
            )
        }

        // Row 4: Transparency settings
        item {
            SettingsListRow(
                title = "Acrylic Transparency",
                description = "Set backplate glass transparency depth scaling. Current: ${(settings.backgroundTransparency * 100).toInt()}%",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Opacity,
                onClick = { activeDialog = "transparency" }
            )
        }

        // Row 5: Canvas grids settings
        item {
            SettingsListRow(
                title = "App Background",
                description = "Choose dynamic blueprint designs, constellations, micro-circuits or raw vector meshes. Current: ${
                    when (settings.backgroundStyle) {
                        "solid" -> "Solid Canvas Background"
                        "grid" -> "Tech Gridlines Overlay"
                        "retro-tiles" -> "Architect Blueprints Style"
                        "constellation" -> "Constellation Glow Pattern"
                        "circuit" -> "Futuristic Circuit Matrix"
                        "mesh" -> "Scientific Vector Mesh"
                        else -> settings.backgroundStyle
                    }
                }",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Wallpaper,
                onClick = { activeDialog = "bgStyle" }
            )
        }

        // Background Opacity/Intensity setting - only shown when Custom Upload is active
        if (settings.backgroundStyle == "upload") {
            item {
                SettingsListRow(
                    title = if (isLight) "Aero Light BG Intensity" else "Dark mode BG Intensity",
                    description = "Adjust opacity and visibility of the custom background. Current: ${(settings.appBackgroundOpacity * 100).toInt()}%",
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.Opacity,
                    onClick = { activeDialog = "bgOpacity" }
                )
            }
        }

        // Group: Player Personalization
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Player Screen Customization",
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily)
            )
            Divider(color = textPrimaryColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            val currentStyleLabel = when (settings.playerBackgroundStyle) {
                "dark" -> "Dark Mode Backdrop"
                "light" -> "Light Mode Backdrop"
                "cover" -> "Cover Art Dynamic Color"
                else -> "Follow Active Theme"
            }
            SettingsListRow(
                title = "Player Background Style",
                description = "Choose background theme inside active player. Current: $currentStyleLabel",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Palette,
                onClick = { activeDialog = "playerBgStyle" }
            )
        }

        if (settings.playerBackgroundStyle == "cover") {
            item {
                SettingsListRow(
                    title = "Cover Color Intensity",
                    description = "Choose color projection intensity on player screen. Current: ${(settings.playerBackgroundIntensity * 100).toInt()}%",
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.Opacity,
                    onClick = { activeDialog = "playerBgIntensity" }
                )
            }
        }

        // Group: Cover Art Settings
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Live Tiles & Cover Art",
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily)
            )
            Divider(color = textPrimaryColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            SettingsToggleRow(
                title = "Remember Last Played Song",
                description = "Automatically load and cue up the last played track and position on app restart.",
                checked = settings.rememberLastPlayed,
                onCheckedChange = { onRememberLastPlayedChange(it) },
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.History
            )
        }

        item {
            SettingsToggleRow(
                title = "Enable Embedded Cover Art",
                description = "Extract and display embedded covers inside player screens and tiles.",
                checked = settings.enableCoverArt,
                onCheckedChange = { onEnableCoverArtChange(it) },
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Image
            )
        }

        if (settings.enableCoverArt) {
            item {
                SettingsToggleRow(
                    title = "Show Cover Art in Song Lists",
                    description = "Optionally render tiny cover art preview indicators inside song lists.",
                    checked = settings.enableListCoverArt,
                    onCheckedChange = { onEnableListCoverArtChange(it) },
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.Collections
                )
            }



            item {
                val currentResLabel = when (settings.coverArtResolution) {
                    "low" -> "Low (Fast / Sampled 160px)"
                    "medium" -> "Medium Balance (Sampled 350px)"
                    "original" -> "Original (Heavy / Full Resolution)"
                    else -> "Optimized (Auto / Sampled 600px)"
                }
                SettingsListRow(
                    title = "Cover Image Resolution",
                    description = "Choose between downscaled performance modes or native scale resolutions. Current: $currentResLabel",
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.HighQuality,
                    onClick = { activeDialog = "coverArtResolution" }
                )
            }
        }

        // Group: Lyrics Settings
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Lyrics Display",
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily)
            )
            Divider(color = textPrimaryColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
        }

        // Toggle Preview Lyrics
        item {
            SettingsToggleRow(
                title = "Preview Lyrics in Player",
                description = "Show lyric previews directly below playback controls.",
                checked = settings.previewLyrics,
                onCheckedChange = { onPreviewLyricsChange(it) },
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Lyrics
            )
        }

        if (true) {
            // Lyrics Font Family selector
            item {
                SettingsListRow(
                    title = "Lyrics Font Family",
                    description = "Change the font style of player lyric lines. Current: ${settings.lyricsFontFamily}",
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.TextFields,
                    onClick = { activeDialog = "lyricsFont" }
                )
            }
            // Lyrics Line Spacing selector
            item {
                SettingsListRow(
                    title = "Lyrics Line Spacing",
                    description = "Configure padding and spacing between lyric lines. Current: ${settings.lyricsSpacing}",
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.List,
                    onClick = { activeDialog = "lyricsSpacing" }
                )
            }
            // Lyrics Text Alignment selector
            item {
                SettingsListRow(
                    title = "Lyrics Text Alignment",
                    description = "Choose alignment: Left, Center, Right, or Follow format. Current: ${settings.lyricsAlignment}",
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.Menu,
                    onClick = { activeDialog = "lyricsAlign" }
                )
            }
            // Lyrics Font Size selector
            item {
                SettingsListRow(
                    title = "Lyrics Font Size",
                    description = "Change the lyrics preview text size in player. Current: ${settings.lyricsFontSize}",
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.FormatSize,
                    onClick = { activeDialog = "lyricsFontSize" }
                )
            }
        }

        // Group 2: Storage & Scanning
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Library Storage & Scanners",
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily)
            )
            Divider(color = textPrimaryColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
        }

        // Row 6: Folder select
        item {
            SettingsListRow(
                title = "Select Folder",
                description = "Select local folders on device to read and load music tracks. Current: $currentFolderLabel",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Folder,
                onClick = { activeDialog = "folder" }
            )
        }

        // Toggle Show Placeholder / Synth Songs
        item {
            SettingsToggleRow(
                title = "Add 5 Placeholder Songs",
                description = "Enable 5 built-in procedural ambient synth tracks to preview music if your local device folder is empty.",
                checked = settings.showPlaceholderSongs,
                onCheckedChange = { onShowPlaceholderSongsChange(it) },
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.LibraryMusic
            )
        }

        // Row 6b: SCAN MUSIC option
        item {
            var showScanToast by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()
            SettingsListRow(
                title = "SCAN MUSIC",
                description = "Trigger a physical check of directories to scan and catalog external audio tracks.",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Refresh,
                onClick = {
                    coroutineScope.launch {
                        onScanMusic()
                        showScanToast = true
                        kotlinx.coroutines.delay(2000)
                        showScanToast = false
                    }
                }
            )
            if (showScanToast) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(themeAccentColor.copy(alpha = 0.12f))
                        .border(1.dp, themeAccentColor, androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Scan complete! Music library catalog updated.",
                        color = textPrimaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Row 6c: RE-RUN PERMISSION AND MUSIC SYNC WIZARD
        item {
            SettingsListRow(
                title = "RE-RUN PERMISSION AND MUSIC SYNC",
                description = "Re-open the initial setup wizard to grant notification access and scan your offline music.",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Notifications,
                onClick = { onReRunOnboarding() }
            )
        }


        // Group: Cache & Storage
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Cache & Storage",
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily)
            )
            Divider(color = textPrimaryColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
        }

        // Cache cleanup row
        item {
            var showCachePurgeToast by remember { mutableStateOf(false) }
            var cacheSize by remember { mutableStateOf(com.example.data.model.CoverArtCache.getCacheSize()) }
            var emptyKeysSize by remember { mutableStateOf(com.example.data.model.CoverArtCache.getNoCoverCount()) }
            val cacheLabel = "Purge dynamic cover-art cache ($cacheSize images, $emptyKeysSize tracks cached as empty/no-art)."
            
            Column {
                SettingsListRow(
                    title = "PURGE APP CACHE",
                    description = cacheLabel,
                    accentColor = themeAccentColor,
                    textColor = textPrimaryColor,
                    descColor = textSecondaryColor,
                    icon = Icons.Default.Delete,
                    onClick = {
                        com.example.data.model.CoverArtCache.clear()
                        cacheSize = com.example.data.model.CoverArtCache.getCacheSize()
                        emptyKeysSize = com.example.data.model.CoverArtCache.getNoCoverCount()
                        showCachePurgeToast = true
                    }
                )
                
                if (showCachePurgeToast) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(themeAccentColor.copy(alpha = 0.12f))
                            .border(1.dp, themeAccentColor, androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                            .padding(12.dp)
                            .clickable { showCachePurgeToast = false }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Cleared",
                                tint = themeAccentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Cover art cache cleared!",
                                    color = textPrimaryColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = getMetroFontFamily(settings.fontFamily)
                                )
                                Text(
                                    text = "Memory and local state purged successfully.",
                                    color = textSecondaryColor,
                                    fontSize = 11.sp,
                                    fontFamily = getMetroFontFamily(settings.fontFamily)
                                )
                            }
                        }
                    }
                }
            }
        }


        // Group: Layout Customization
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Layout & Customization",
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily)
            )
            Divider(color = textPrimaryColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
        }

        // Hub Tiles visible toggle checkboxes
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Configure Visible Hub Tiles:",
                    color = textPrimaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                )

                val currentVisibleTilesSet = remember(settings.visibleTiles) {
                    settings.visibleTiles.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
                val allPossibleTiles = listOf(
                    "now_playing" to "Now Playing Module",
                    "pinned_tracks" to "Pinned Tracks List",
                    "my_music" to "My Music Index",
                    "playlists" to "User Playlists",
                    "favorites" to "Favorite Tracks Highlight",
                    "artists" to "Artists Grouping",
                    "albums" to "Albums Visualizer"
                )

                allPossibleTiles.forEach { (tileKey, tileName) ->
                    val checked = currentVisibleTilesSet.contains(tileKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextSet = if (checked) {
                                    currentVisibleTilesSet - tileKey
                                } else {
                                    currentVisibleTilesSet + tileKey
                                }
                                val newStr = if (nextSet.isEmpty()) "" else nextSet.joinToString(",")
                                onVisibleTilesChange(newStr)
                            }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(1.5.dp, themeAccentColor, androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                                .background(if (checked) themeAccentColor else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checked) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isLight) Color.White else Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = tileName.lowercase(),
                            color = textPrimaryColor,
                            fontFamily = getMetroFontFamily(settings.fontFamily),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Cover Art Border Thickness Select list row
        item {
            SettingsListRow(
                title = "Cover Art Border Thickness",
                description = "Customize the width and styling of cover art borders. Current: ${settings.coverArtBorderThickness}",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.BorderOuter,
                onClick = { activeDialog = "borderThickness" }
            )
        }

        // Artist separators custom layout
        item {
            SettingsListRow(
                title = "Artist Name Separators",
                description = "Configure semicolon-separated tokens (e.g., '/' or 'feat.') to split joint collaborations. Current: ${settings.artistSeparators}",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Person,
                onClick = { activeDialog = "artistSeps" }
            )
        }

        // Group 3: About App
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "About",
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily)
            )
            Divider(color = textPrimaryColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
        }

        // Row 7: Info card info clicked
        item {
            SettingsListRow(
                title = "About Azune",
                description = "Click to view info",
                accentColor = themeAccentColor,
                textColor = textPrimaryColor,
                descColor = textSecondaryColor,
                icon = Icons.Default.Info,
                onClick = { activeDialog = "info" }
            )
        }
    }

    // ==========================================
    // POPUP OPTIONS DIALOGS
    // ==========================================

    if (activeDialog == "lyricsFont") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Lyrics Font Family", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Inter" to "Inter (Default)", "Segoe" to "Inter (Default)", "space" to "Space Tech", "mono" to "Monospace Standard").forEach { (font, name) ->
                        val selected = settings.lyricsFontFamily == font
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLyricsFontChange(font)
                                    activeDialog = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected,
                                onClick = {
                                    onLyricsFontChange(font)
                                    activeDialog = null
                                },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = themeAccentColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (activeDialog == "lyricsSpacing") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Lyrics Line Spacing", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Tight" to "Tight Spacing", "Normal" to "Normal Padding", "Spacious" to "Spacious Air").forEach { (spacing, name) ->
                        val selected = settings.lyricsSpacing.equals(spacing, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLyricsSpacingChange(spacing)
                                    activeDialog = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected,
                                onClick = {
                                    onLyricsSpacingChange(spacing)
                                    activeDialog = null
                                },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = themeAccentColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (activeDialog == "lyricsAlign") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Lyrics Alignment", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Left" to "Align Left", "Center" to "Align Center", "Right" to "Align Right", "Follow" to "Follow Format").forEach { (align, name) ->
                        val selected = settings.lyricsAlignment.equals(align, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLyricsAlignmentChange(align)
                                    activeDialog = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected,
                                onClick = {
                                    onLyricsAlignmentChange(align)
                                    activeDialog = null
                                },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = themeAccentColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (activeDialog == "lyricsFontSize") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Lyrics Font Size", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Small" to "Small Tech", "Medium" to "Medium Standard", "Large" to "Large Readable").forEach { (sz, name) ->
                        val selected = settings.lyricsFontSize.equals(sz, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLyricsFontSizeChange(sz)
                                    activeDialog = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected,
                                onClick = {
                                    onLyricsFontSizeChange(sz)
                                    activeDialog = null
                                },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = themeAccentColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (activeDialog == "theme") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else if (settings.themeMode == "amoled") Color.Black else Color(0xFF141414),
            title = { Text(text = "Theme Mode", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "dark" to "Dark mode",
                        "amoled" to "Amoled dark",
                        "light" to "Aero Light",
                        "system" to "Follow System"
                    ).forEach { (mode, name) ->
                        val selected = settings.themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeChange(mode)
                                    activeDialog = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onThemeChange(mode)
                                    activeDialog = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = themeAccentColor, unselectedColor = textSecondaryColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Cancel", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "accent") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Accent Color", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(METRO_PALETTES) { (hex, name) ->
                        val selected = settings.accentColorHex.lowercase() == hex.lowercase()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAccentChange(hex)
                                    activeDialog = null
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(getThemeAccentColor(hex))
                                    .border(1.dp, textPrimaryColor.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                name,
                                modifier = Modifier.weight(1f),
                                color = textPrimaryColor,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = themeAccentColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Cancel", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "font") {
        val context = androidx.compose.ui.platform.LocalContext.current
        val importedFontKeys = remember(activeDialog) {
            try {
                val fontsDir = java.io.File(context.filesDir, "fonts")
                if (fontsDir.exists()) {
                    fontsDir.listFiles { file -> file.extension.equals("ttf", ignoreCase = true) }
                        ?.map { it.nameWithoutExtension }
                        ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        val standardFonts = listOf(
            "Inter" to "Inter (Default)",
            "space" to "Space Grotesk (Tech)",
            "Monospace" to "Monospace (Code)",
            "Slab" to "Slab Serif (Vintage)"
        )
        val allFontOptions = standardFonts + importedFontKeys.map { it to it }

        val fontLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            if (uri != null) {
                try {
                    val resolver = context.contentResolver
                    var displayName = "custom_font"
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1 && cursor.moveToFirst()) {
                            val originalName = cursor.getString(nameIdx)
                            if (originalName.contains(".", ignoreCase = true)) {
                                val ext = originalName.substringAfterLast(".").lowercase()
                                if (ext == "ttf") {
                                    displayName = originalName.substringBeforeLast(".")
                                }
                            }
                        }
                    }
                    
                    displayName = displayName.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim()
                    if (displayName.isEmpty()) displayName = "custom_font"
                    
                    val fontsDir = java.io.File(context.filesDir, "fonts")
                    if (!fontsDir.exists()) {
                        fontsDir.mkdirs()
                    }
                    val destFile = java.io.File(fontsDir, "$displayName.ttf")
                    
                    resolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    onFontChange(displayName)
                    android.widget.Toast.makeText(context, "Font '$displayName' imported!", android.widget.Toast.LENGTH_SHORT).show()
                    activeDialog = null
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "App Font Family", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allFontOptions.forEach { (fontKey, name) ->
                        val selected = settings.fontFamily == fontKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onFontChange(fontKey)
                                    activeDialog = null
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onFontChange(fontKey)
                                    activeDialog = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = themeAccentColor, unselectedColor = textSecondaryColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                color = textPrimaryColor,
                                fontSize = 14.sp,
                                fontFamily = getMetroFontFamily(fontKey)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = textSecondaryColor.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = { fontLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Upload, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "IMPORT CUSTOM FONT (.TTF)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Cancel", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "transparency") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Acrylic Transparency", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(text = "Adjust backing layer transparency", color = textSecondaryColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Solid Mode", color = textSecondaryColor, fontSize = 11.sp)
                        Text(text = "${(settings.backgroundTransparency * 100).toInt()}%", color = themeAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "Max Glass", color = textSecondaryColor, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = settings.backgroundTransparency,
                        onValueChange = onTransparencyChange,
                        valueRange = 0.0f..0.85f,
                        colors = SliderDefaults.colors(
                            thumbColor = themeAccentColor,
                            activeTrackColor = themeAccentColor,
                            inactiveTrackColor = textPrimaryColor.copy(alpha = 0.15f)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Done", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "bgStyle") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else if (settings.themeMode == "amoled") Color.Black else Color(0xFF141414),
            title = { Text(text = "App Background", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "solid" to "Solid Canvas Background", 
                        "grid" to "Tech Gridlines Overlay", 
                        "retro-tiles" to "Architect Blueprints Style",
                        "constellation" to "Constellation Glow Pattern",
                        "circuit" to "Futuristic Circuit Matrix",
                        "mesh" to "Scientific Vector Mesh"
                    ).forEach { (style, name) ->
                        val selected = settings.backgroundStyle == style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBgStyleChange(style)
                                    activeDialog = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onBgStyleChange(style)
                                    activeDialog = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = themeAccentColor, unselectedColor = textSecondaryColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Cancel", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "bgOpacity") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = if (isLight) "Aero Light Background" else "Dark mode Background", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = if (isLight) "Adjust custom image wash-out soft intensity" else "Adjust custom image dark dimming level",
                        color = textSecondaryColor,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Gentle blend", color = textSecondaryColor, fontSize = 11.sp)
                        Text(text = "${(settings.appBackgroundOpacity * 100).toInt()}%", color = themeAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "Full vision", color = textSecondaryColor, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = settings.appBackgroundOpacity,
                        onValueChange = onBgOpacityChange,
                        valueRange = 0.05f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = themeAccentColor,
                            activeTrackColor = themeAccentColor,
                            inactiveTrackColor = textPrimaryColor.copy(alpha = 0.15f)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Done", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "playerBgStyle") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else if (settings.themeMode == "amoled") Color.Black else Color(0xFF141414),
            title = { Text(text = "Player Background Style", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "theme" to "Follow Active Theme", 
                        "dark" to "Dark Mode Backdrop", 
                        "light" to "Light Mode Backdrop",
                        "cover" to "Cover Art Dynamic Color"
                    ).forEach { (style, name) ->
                        val selected = settings.playerBackgroundStyle == style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPlayerBgStyleChange(style)
                                    activeDialog = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onPlayerBgStyleChange(style)
                                    activeDialog = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = themeAccentColor, unselectedColor = textSecondaryColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Cancel", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "coverArtResolution") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else if (settings.themeMode == "amoled") Color.Black else Color(0xFF141414),
            title = { Text(text = "Cover Image Resolution", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "low" to "Low (Fast / Sampled 160px)", 
                        "medium" to "Medium Balance (Sampled 350px)", 
                        "optimized" to "Optimized (Auto / Sampled 600px)",
                        "original" to "Original (Heavy / Full Resolution)"
                    ).forEach { (resolution, name) ->
                        val selected = settings.coverArtResolution == resolution
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCoverArtResolutionChange(resolution)
                                    activeDialog = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onCoverArtResolutionChange(resolution)
                                    activeDialog = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = themeAccentColor, unselectedColor = textSecondaryColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Cancel", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "playerBgIntensity") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Player Background Intensity", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Adjust cover art dynamic color brightness and vibrancy overlay.",
                        color = textSecondaryColor,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Muted Theme", color = textSecondaryColor, fontSize = 11.sp)
                        Text(text = "${(settings.playerBackgroundIntensity * 100).toInt()}%", color = themeAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "Vibrant Color", color = textSecondaryColor, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = settings.playerBackgroundIntensity,
                        onValueChange = onPlayerBgIntensityChange,
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = themeAccentColor,
                            activeTrackColor = themeAccentColor,
                            inactiveTrackColor = textPrimaryColor.copy(alpha = 0.15f)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Done", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "privacyPolicy") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else if (settings.themeMode == "amoled") Color.Black else Color(0xFF141414),
            title = { Text(text = "Privacy Policy", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Azune Player is built with user privacy as the absolute foundation.",
                        color = textPrimaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• 100% Offline Ops: All scans, playlists, and audio synthetics operate solely inside your physical device.\n" +
                               "• 0% Telemetry: We do not collect, transmit, or process any metadata, audio files, or personal statistics.\n" +
                               "• Zero Advertisements: The app is completely ad-free with no billing, internet logins, or subscriptions.\n" +
                               "• Transparent Permissions: Storage access for music file scanning and Notification access for status bar playback controls. We never collect or transmit your data.",
                        color = textSecondaryColor,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "Close", color = themeAccentColor)
                }
            }
        )
    }

    if (activeDialog == "folder") {
        var showFolderExplorer by remember { mutableStateOf(false) }

        if (!showFolderExplorer) {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                containerColor = if (isLight) Color.White else Color(0xFF141414),
                title = { Text(text = "Select Folder", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(text = "Choose whether to auto-scan all folders or filter target directories.", color = textSecondaryColor, fontSize = 12.sp)

                        Button(
                            onClick = {
                                onFolderChange("All")
                                activeDialog = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor, contentColor = Color.White),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                        ) {
                            Text(text = "Default (All Folders in /0/)", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { showFolderExplorer = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = textPrimaryColor.copy(alpha = 0.08f), contentColor = textPrimaryColor),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                        ) {
                            Text(text = "Custom Folder Selection...", fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text(text = "Cancel", color = themeAccentColor)
                    }
                }
            )
        } else {
            // Display Custom folders with hierarchical navigation inside /storage/emulated/0
            var currentExplorerDir by remember { mutableStateOf(java.io.File("/storage/emulated/0")) }
            
            val explorerFoldersList = remember(currentExplorerDir) {
                if (currentExplorerDir.exists() && currentExplorerDir.isDirectory) {
                    currentExplorerDir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }?.toList()?.sortedBy { it.name } ?: emptyList()
                } else {
                    emptyList()
                }
            }

            val getRelativePath = { file: java.io.File ->
                val base = "/storage/emulated/0"
                if (file.absolutePath.startsWith(base)) {
                    file.absolutePath.removePrefix(base).trimStart('/')
                } else {
                    file.name
                }
            }

            val selectedFolders = remember(settings.targetMusicFolder) {
                if (settings.targetMusicFolder == "All") emptyList()
                else settings.targetMusicFolder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                containerColor = if (isLight) Color.White else Color(0xFF141414),
                title = {
                    Column {
                        Text(
                            text = "Custom Folder Selection",
                            color = textPrimaryColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val currentRel = getRelativePath(currentExplorerDir)
                        Text(
                            text = "Current location: /0/${if (currentRel.isEmpty()) "" else currentRel}",
                            color = themeAccentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Tap a folder to select it for scanning. Press and hold/hold down a folder to enter it.",
                            color = textSecondaryColor,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.height(240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Back / Up navigation folder row
                            if (currentExplorerDir.absolutePath != "/storage/emulated/0") {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentExplorerDir = currentExplorerDir.parentFile ?: java.io.File("/storage/emulated/0")
                                            }
                                            .border(1.dp, Color.Transparent)
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Go up",
                                            tint = themeAccentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = ".. (parent folder)",
                                            color = themeAccentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            items(explorerFoldersList) { folder ->
                                val relPath = getRelativePath(folder)
                                val isSelected = selectedFolders.any { it.equals(relPath, ignoreCase = true) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) themeAccentColor.copy(alpha = 0.12f) else Color.Transparent)
                                        .combinedClickable(
                                            onClick = {
                                                val updated = if (isSelected) {
                                                    selectedFolders.filter { !it.equals(relPath, ignoreCase = true) }
                                                } else {
                                                    selectedFolders + relPath
                                                }
                                                val newValue = if (updated.isEmpty()) "All" else updated.joinToString(",")
                                                onFolderChange(newValue)
                                            },
                                            onLongClick = {
                                                currentExplorerDir = folder
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) themeAccentColor else Color.Transparent,
                                            androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) themeAccentColor else textSecondaryColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = folder.name,
                                        color = textPrimaryColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "selected",
                                            tint = themeAccentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { activeDialog = null }
                    ) {
                        Text(text = "Save & Scan", color = themeAccentColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text(text = "Back", color = themeAccentColor)
                    }
                }
            )
        }
    }

    if (activeDialog == "info") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "About Azune", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Azune Music Player", color = themeAccentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Version: ${com.example.BuildConfig.VERSION_NAME}\nEngine: Jetpack Compose / Kotlin\nDeveloper: Azune Team\n\nA clean, beautiful offline MP3 music player featuring high-fidelity local playback, custom adaptive visual themes, and lyrics scrolling support.",
                        color = textSecondaryColor,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showPrivacyPolicyFullScreen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor, contentColor = Color.White),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "PRIVACY POLICY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = { throw RuntimeException("Test Crash triggered from About Azune screen!") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "CRASH TEST", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "CLOSE", color = themeAccentColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPrivacyPolicyFullScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPrivacyPolicyFullScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isLight) Color.White else Color(0xFF121212)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PRIVACY POLICY",
                            color = themeAccentColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showPrivacyPolicyFullScreen = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "close privacy policy",
                                tint = textPrimaryColor
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Body Text Scrollable
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Last updated: June 2026",
                            color = textSecondaryColor,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        
                        Text(
                            text = "Introduction",
                            color = textPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Welcome to Azune Music Player. We are committed to protecting your privacy and ensuring a secure user experience. This Privacy Policy explains our practices regarding data collection and usage inside the application.",
                            color = textSecondaryColor,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Text(
                            text = "100% Offline Operations & Data Storage",
                            color = textPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "All core functionalities of Azune Music Player, including music scanning, playlist compilation, track statistics, preference caches, and synthetic sound generation, run entirely on your physical device. We do not use, integrate, or rely on external cloud databases, and your personal data remains completely local to your device.",
                            color = textSecondaryColor,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Text(
                            text = "No Telemetry and Zero Data Collection",
                            color = textPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "We do not collect, transmit, upload, sell, or analyze your audio files, playback history, metadata, playlists, system diagnostic logs, or usage metrics. There are no tracking scripts, telemetry frameworks, analytics SDKs, or background synchronization processes integrated into the app.",
                            color = textSecondaryColor,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Text(
                            text = "Application Permissions",
                            color = textPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Azune Music Player requests standard Android device permissions strictly for offline media functionality:\n\n" +
                                   "1. Media / Storage Access: Required solely to scan, catalog, and play your local audio files.\n" +
                                   "2. Notification Access: Required solely to display playback status, current track details, and playback controls in your device status bar and lock screen.\n\n" +
                                   "We do NOT collect, transmit, upload, or share your personal data, media files, or listening activity with anyone. Everything stays 100% private on your device.",
                            color = textSecondaryColor,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Text(
                            text = "No Advertisements or Subscriptions",
                            color = textPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "The application feature set is 100% ad-free, requiring no billing setups, premium unlocks, in-app purchases, or internet requirements. Your experience remains private, uncluttered, and purely offline.",
                            color = textSecondaryColor,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Close/OK Button below
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        MetroButton(
                            text = "ok",
                            accentHex = settings.accentColorHex,
                            onClick = { showPrivacyPolicyFullScreen = false }
                        )
                    }
                }
            }
        }
    }

    if (activeDialog == "borderThickness") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Cover Art Border Thickness", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "off" to "Off (Default)",
                        "normal" to "Normal",
                        "bold" to "Bold",
                        "super-bold" to "Super Bold"
                    ).forEach { (thickness, name) ->
                        val selected = settings.coverArtBorderThickness == thickness
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCoverArtBorderThicknessChange(thickness)
                                    activeDialog = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected,
                                onClick = {
                                    onCoverArtBorderThicknessChange(thickness)
                                    activeDialog = null
                                },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = themeAccentColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = name, color = textPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (activeDialog == "artistSeps") {
        var tempSeps by remember { mutableStateOf(settings.artistSeparators) }
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = if (isLight) Color.White else Color(0xFF141414),
            title = { Text(text = "Artist Name Separators", color = textPrimaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter collaborative identifiers (e.g. '/' or ',' or 'feat.') to split joint collaborations into individual artists in the Artists tab.\n\nSemicolons ( ; ) must be used to separate multiple tokens.",
                        color = textSecondaryColor,
                        fontSize = 12.sp
                    )
                    
                    MetroTextField(
                        value = tempSeps,
                        onValueChange = { tempSeps = it },
                        placeholder = "e.g., / ; , ; feat. ; ft. ; & ; and",
                        leadingIcon = Icons.Default.Person,
                        onClear = { tempSeps = "" }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onArtistSeparatorsChange(tempSeps)
                    activeDialog = null
                }) {
                    Text(text = "SAVE", color = themeAccentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text(text = "CANCEL", color = themeAccentColor)
                }
            }
        )
    }
}

// Helpers Composable for the custom Android standard Settings rows
@Composable
fun SettingsListRow(
    title: String,
    description: String,
    accentColor: Color,
    textColor: Color,
    descColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = descColor,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    textColor: Color,
    descColor: Color,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = descColor,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = descColor,
                uncheckedTrackColor = descColor.copy(alpha = 0.2f)
            )
        )
    }
}

// Helper to resolve readable accent color label
fun getAccentColorName(hex: String): String {
    return METRO_PALETTES.firstOrNull { it.first.lowercase() == hex.lowercase() }?.second ?: hex
}

// SUBVIEW: Opened Playlist Detail view panel
@Composable
fun PlaylistDetailView(
    playlistName: String,
    playlistDesc: String,
    playlistTracks: List<Track>,
    currentTrack: Track?,
    settings: com.example.data.database.UserSettingsEntity,
    onBackClick: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleFavorite: (String) -> Unit,
    isFavorite: (String) -> Boolean,
    onRemoveTrack: (String) -> Unit,
    allSongsList: List<Track>,
    onAddSongToPlaylist: (Track) -> Unit
) {
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
    val textPrimaryColor = if (settings.themeMode == "light") Color.Black else Color.White
    val textSecondaryColor = if (settings.themeMode == "light") Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
    var showingAddTrackDropdown by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "back", tint = textPrimaryColor)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlistName,
                    color = themeAccentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    fontFamily = getMetroFontFamily(settings.fontFamily),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlistDesc.isNotEmpty()) {
                    Text(
                        text = playlistDesc,
                        color = textSecondaryColor,
                        fontSize = 11.sp,
                        fontFamily = getMetroFontFamily(settings.fontFamily),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Button to toggle the simple inline add music panel
            MetroButton(
                text = if (showingAddTrackDropdown) "hide files" else "+ insert",
                accentHex = settings.accentColorHex,
                onClick = { showingAddTrackDropdown = !showingAddTrackDropdown }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (showingAddTrackDropdown) {
            Text(
                text = "all library music (tap '+' to add)".uppercase(),
                color = themeAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = getMetroFontFamily(settings.fontFamily),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Dynamic dropdown file selection helper inside playlist
            val candidateTracks = allSongsList.filter { cand -> playlistTracks.none { it.id == cand.id } }

            if (candidateTracks.isEmpty()) {
                Text(
                    text = "all available tracks are already included in this folder.",
                    color = textSecondaryColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(textPrimaryColor.copy(alpha = 0.05f))
                        .border(1.dp, textPrimaryColor.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(candidateTracks) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddSongToPlaylist(track) }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, color = textPrimaryColor, fontSize = 13.sp, maxLines = 1)
                                Text(track.artist, color = textSecondaryColor, fontSize = 11.sp, maxLines = 1)
                            }
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = themeAccentColor)
                        }
                        Divider(color = textPrimaryColor.copy(alpha = 0.05f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Tracks inside the playlist container
        if (playlistTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = textPrimaryColor.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "this playlist is currently empty.",
                        color = textSecondaryColor,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "tap 'insert' above to load tracks.",
                        color = textSecondaryColor.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(playlistTracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (currentTrack?.id == track.id) textPrimaryColor.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { onPlayTrack(track) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (currentTrack?.id == track.id) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (currentTrack?.id == track.id) themeAccentColor else textSecondaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (currentTrack?.id == track.id) themeAccentColor else textPrimaryColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = track.artist,
                                color = textSecondaryColor,
                                fontSize = 12.sp
                            )
                        }

                        // Remove from playlist action
                        IconButton(onClick = { onRemoveTrack(track.id) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "remove",
                                tint = Color.Red.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Divider(color = textPrimaryColor.copy(alpha = 0.05f))
                }
            }
        }
    }
}

// PLAYBACK BAR COMPOSABLE
@Composable
fun MiniPlaybackStrip(
    track: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    settings: com.example.data.database.UserSettingsEntity,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onStripClick: () -> Unit,
    onStopPlayback: () -> Unit
) {
    if (track == null) return
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
    val textPrimaryColor = if (settings.themeMode == "light") Color.Black else Color.White
    val containerBgColor = when (settings.themeMode) {
        "light" -> Color(0xFFE5E5E5)
        "amoled" -> Color.Black
        else -> Color(0xFF151515)
    }

    // Progress percentage
    val percentage = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    var swipeAccumulatedX by remember { mutableStateOf(0f) }
    var swipeAccumulatedY by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(containerBgColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        swipeAccumulatedX = 0f
                        swipeAccumulatedY = 0f
                    },
                    onDragEnd = {
                        if (swipeAccumulatedX < -120f && Math.abs(swipeAccumulatedX) > Math.abs(swipeAccumulatedY)) {
                            onStopPlayback()
                        } else if (swipeAccumulatedY < -120f && Math.abs(swipeAccumulatedY) > Math.abs(swipeAccumulatedX)) {
                            onStripClick()
                        }
                    },
                    onDragCancel = {
                        swipeAccumulatedX = 0f
                        swipeAccumulatedY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        swipeAccumulatedX += dragAmount.x
                        swipeAccumulatedY += dragAmount.y
                    }
                )
            }
            .clickable { onStripClick() }
            .border(2.dp, textPrimaryColor.copy(alpha = 0.05f))
    ) {
        // Thin accent-colored horizontal flat playback loader line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(textPrimaryColor.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(themeAccentColor)
            )
        }

        // Row controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (settings.enableCoverArt) {
                com.example.ui.components.TrackCoverImage(
                    track = track,
                    resolution = settings.coverArtResolution,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(40.dp)
                        .border(1.dp, textPrimaryColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
                    fallbackSymbol = "♬",
                    symbolFontSize = 20.sp,
                    themeAccentColor = themeAccentColor,
                    isLight = (settings.themeMode == "light")
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    color = textPrimaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = track.artist,
                    color = themeAccentColor,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "play/pause",
                        tint = textPrimaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "next",
                        tint = textPrimaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// FULLSCREEN PLAYBACK CONSOLE WITH ADVANCED OPTION PANEL & MULTI-FORMAT LYRICS
@Composable
fun FullscreenPlaybackConsole(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    settings: com.example.data.database.UserSettingsEntity,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavoriteToggle: () -> Unit,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    isLyricsExpanded: Boolean,
    onToggleLyricsExpanded: (Boolean) -> Unit,
    // NEW ARGUMENTS FOR ROBUST EXPANDED SETTINGS drawer
    queue: List<Track>,
    customLyricsMap: Map<String, String>,
    speedValue: Float,
    pitchValue: Float,
    onPlayQueueTrack: (Track) -> Unit,
    onUpdateLyrics: (String, String) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onUpdatePlaybackParams: (Float, Float) -> Unit,
    onTogglePinned: () -> Unit,
    isPinned: Boolean,
    isShuffle: Boolean,
    isRepeat: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isLight = when (settings.themeMode) {
        "light" -> true
        "dark" -> false
        "amoled" -> false
        else -> !isSystemDark
    }
    val themeAccentColor = getThemeAccentColor(settings.accentColorHex)
    val coroutineScope = rememberCoroutineScope()
    
    // Determine the base background depending on selected PlayerBackgroundStyle
    val playerBaseBg = when (settings.playerBackgroundStyle) {
        "light" -> Color(0xFFF9F9F9)
        "dark" -> Color(0xFF0F0F0F)
        "theme" -> if (isLight) Color(0xFFF9F9F9) else if (settings.themeMode == "amoled") Color.Black else Color(0xFF0F0F0F)
        else -> if (isLight) Color(0xFFF9F9F9) else if (settings.themeMode == "amoled") Color.Black else Color(0xFF0F0F0F)
    }

    // Load actual cover art bitmap for accurate color extraction
    val coverBitmap = remember(track) {
        try {
            com.example.data.model.CoverArtCache.get(track.id, track.path, "low")
        } catch (e: Exception) {
            null
        }
    }

    // SOLID background representing cover art color OR standard solids
    val canvasBg = if (settings.playerBackgroundStyle == "cover") {
        val extractedColor = remember(coverBitmap, isLight) {
            if (coverBitmap != null) {
                try {
                    // Extract mathematical average color of the album art instantly
                    val scaled = android.graphics.Bitmap.createScaledBitmap(coverBitmap, 1, 1, true)
                    val pixel = scaled.getPixel(0, 0)
                    if (scaled != coverBitmap) {
                        scaled.recycle()
                    }
                    
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(pixel, hsv)
                    
                    if (isLight) {
                        hsv[1] = hsv[1].coerceAtMost(0.25f) // eye-comfy low saturation for light-mode backdrops
                        hsv[2] = 0.94f                       // bright background
                    } else {
                        hsv[1] = hsv[1].coerceAtLeast(0.65f) // colorful, rich high saturation for dark mode ambiance
                        hsv[2] = 0.16f                       // deep, premium elegant dark palette
                    }
                    Color(android.graphics.Color.HSVToColor(hsv))
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        val blendColor = extractedColor ?: run {
            // Seed fallback
            val seed = track.title + track.artist
            val h = Math.abs(seed.hashCode()) % 360
            val s = 0.55f
            val l = if (isLight) 0.82f else 0.18f
            val c = (1f - Math.abs(2f * l - 1f)) * s
            val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
            val m = l - c / 2f
            val (r, g, b) = when (h / 60) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            Color(
                ((r + m) * 255).toInt().coerceIn(0, 255),
                ((g + m) * 255).toInt().coerceIn(0, 255),
                ((b + m) * 255).toInt().coerceIn(0, 255)
            )
        }

        val ratio = settings.playerBackgroundIntensity.coerceIn(0f, 1f)
        Color(
            red = playerBaseBg.red * (1f - ratio) + blendColor.red * ratio,
            green = playerBaseBg.green * (1f - ratio) + blendColor.green * ratio,
            blue = playerBaseBg.blue * (1f - ratio) + blendColor.blue * ratio,
            alpha = 1f
        )
    } else {
        playerBaseBg
    }

    val isPlayerBgLight = if (settings.playerBackgroundStyle == "dark") {
        false
    } else if (settings.playerBackgroundStyle == "light") {
        true
    } else if (settings.playerBackgroundStyle == "cover") {
        val luminance = 0.299f * canvasBg.red + 0.587f * canvasBg.green + 0.114f * canvasBg.blue
        luminance > 0.45f
    } else {
        isLight
    }

    val textPrimary = if (isPlayerBgLight) Color.Black else Color.White
    val textSecondary = if (isPlayerBgLight) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)

    var isMoreOptionsSheetVisible by remember { mutableStateOf(false) }
    var accumulatedDragY by remember { mutableStateOf(0f) }

    // Cover custom corner style resolution
    val coverShape = when (settings.cornerCoverArt) {
        "sharp" -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
        "rounded" -> androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        "circle" -> androidx.compose.foundation.shape.CircleShape
        else -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    }

    val borderThicknessDp = when (settings.coverArtBorderThickness.lowercase()) {
        "off" -> 0.dp
        "normal" -> 2.dp
        "bold" -> 4.dp
        "super-bold" -> 8.dp
        else -> 0.dp
    }

    val coverBorderColor = remember(coverBitmap, isPlayerBgLight, themeAccentColor) {
        if (coverBitmap != null) {
            try {
                val scaled = android.graphics.Bitmap.createScaledBitmap(coverBitmap, 1, 1, true)
                val pixel = scaled.getPixel(0, 0)
                if (scaled != coverBitmap) {
                    scaled.recycle()
                }
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(pixel, hsv)
                if (isPlayerBgLight) {
                    hsv[1] = hsv[1].coerceAtLeast(0.40f)
                    hsv[2] = 0.55f 
                } else {
                    hsv[1] = hsv[1].coerceAtLeast(0.40f)
                    hsv[2] = 0.75f
                }
                Color(android.graphics.Color.HSVToColor(hsv))
            } catch (e: Exception) {
                themeAccentColor
            }
        } else {
            themeAccentColor
        }
    }

    // Formatting milliseconds into beautiful readable string
    fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBg) // SOLID, NO TRANSPARENCY
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { accumulatedDragY = 0f },
                    onDragEnd = {
                        if (accumulatedDragY > 150f) {
                            onDismiss()
                        }
                        accumulatedDragY = 0f
                    },
                    onDragCancel = { accumulatedDragY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragY += dragAmount.y
                    }
                )
            }
    ) {
        // Apple Music Styled Sync Lyrics scrolling block calculations (lifted up for access in both panels)
        var rawLyricsText by remember(track, customLyricsMap) { mutableStateOf("") }
        var lyricLines by remember(track, customLyricsMap) { mutableStateOf<List<LyricLine>>(emptyList()) }
        var isTtml by remember(track, customLyricsMap) { mutableStateOf(false) }

        LaunchedEffect(track, customLyricsMap) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                getLyricsForTrack(track, customLyricsMap)
            }
            rawLyricsText = loaded
            
            val isTtmlText = loaded.contains("<tt", ignoreCase = true) || loaded.contains("<p", ignoreCase = true)
            val parsedLines = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (isTtmlText) parseTtml(loaded) else parseLrc(loaded)
            }
            lyricLines = parsedLines
            isTtml = isTtmlText
        }
        val isTimedLyrics = remember(lyricLines) { lyricLines.any { it.timeMs > 0 } }

        val activePositionIndex = remember(lyricLines, positionMs) {
            val idx = lyricLines.indexOfLast { positionMs >= it.timeMs }
            if (idx == -1) 0 else idx
        }

        val lyricsListState = rememberLazyListState()
        val previewLyricsListState = rememberLazyListState()
        
        var isFirstScroll by remember(track) { mutableStateOf(true) }
        LaunchedEffect(track) {
            kotlinx.coroutines.delay(600)
            isFirstScroll = false
        }

        LaunchedEffect(activePositionIndex, isLyricsExpanded) {
            if (isLyricsExpanded && isTimedLyrics && lyricLines.isNotEmpty() && !lyricsListState.isScrollInProgress) {
                val expandedCenterIndex = (activePositionIndex - 2).coerceAtLeast(0)
                try {
                    if (isFirstScroll) {
                        lyricsListState.scrollToItem(expandedCenterIndex)
                    } else {
                        lyricsListState.animateScrollToItem(expandedCenterIndex)
                    }
                } catch (e: Exception) {}
            }
        }
        LaunchedEffect(activePositionIndex, isLyricsExpanded, settings.previewLyrics) {
            if (!isLyricsExpanded && settings.previewLyrics && isTimedLyrics && lyricLines.isNotEmpty() && !previewLyricsListState.isScrollInProgress) {
                val previewTargetIndex = (activePositionIndex - 1).coerceAtLeast(0)
                try {
                    if (isFirstScroll) {
                        previewLyricsListState.scrollToItem(previewTargetIndex)
                    } else {
                        previewLyricsListState.animateScrollToItem(previewTargetIndex)
                    }
                } catch (e: Exception) {}
            }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLyricsExpanded) {
                // Immersive Full Screen Lyrics Screen!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 58.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { onToggleLyricsExpanded(false) }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "back to player",
                            tint = textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 8.dp).weight(1f)
                    ) {
                        Text(
                            text = track.title,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist.uppercase(),
                            color = themeAccentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Expanded Scrolling Lyrics: Immersive & Beautiful
                val expandedSpacingVal = when (settings.lyricsSpacing.lowercase()) {
                    "tight" -> 8.dp
                    "spacious" -> 24.dp
                    else -> 16.dp
                }

                val lyricsHorizontalAlignment = when (settings.lyricsAlignment.lowercase()) {
                    "left" -> Alignment.Start
                    "right" -> Alignment.End
                    else -> Alignment.CenterHorizontally
                }

                val lyricsFont = getMetroFontFamily(settings.lyricsFontFamily)
                val alignVal = when (settings.lyricsAlignment.lowercase()) {
                    "left" -> TextAlign.Left
                    "right" -> TextAlign.Right
                    else -> TextAlign.Center
                }
                val itemSpacingVal = when (settings.lyricsSpacing.lowercase()) {
                    "tight" -> 2.dp
                    "spacious" -> 8.dp
                    else -> 4.dp
                }
                val sizeVal = when (settings.lyricsFontSize.lowercase()) {
                    "small" -> 16.sp
                    "large" -> 24.sp
                    else -> 20.sp
                }
                val scaleOrigin = when (settings.lyricsAlignment.lowercase()) {
                    "left" -> androidx.compose.ui.graphics.TransformOrigin(0.0f, 0.5f)
                    "right" -> androidx.compose.ui.graphics.TransformOrigin(1.0f, 0.5f)
                    else -> androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
                val itemAlignment = when (settings.lyricsAlignment.lowercase()) {
                    "left" -> Alignment.CenterStart
                    "right" -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
                val subColumnAlignment = when (settings.lyricsAlignment.lowercase()) {
                    "left" -> Alignment.Start
                    "right" -> Alignment.End
                    else -> Alignment.CenterHorizontally
                }

                LazyColumn(
                    state = lyricsListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(expandedSpacingVal),
                    horizontalAlignment = lyricsHorizontalAlignment
                ) {
                    if (lyricLines.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No lyric",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        itemsIndexed(lyricLines) { index, line ->
                            val isActive = isTimedLyrics && index == activePositionIndex
                            
                            val targetColor = if (!isTimedLyrics) textPrimary.copy(alpha = 0.9f) else if (isActive) themeAccentColor else textPrimary.copy(alpha = 0.35f)
                            val targetScale = if (!isTimedLyrics) 1.0f else if (isActive) 1.12f else 0.90f
                            
                            val animatedColor by animateColorAsState(
                                targetValue = targetColor,
                                animationSpec = tween(durationMillis = 250),
                                label = "color_$index"
                            )
                            
                            val animatedScale by animateFloatAsState(
                                targetValue = targetScale,
                                animationSpec = tween(durationMillis = 250),
                                label = "scale_$index"
                            )

                            if (line.text.isBlank() && isActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = itemSpacingVal, horizontal = 16.dp),
                                    contentAlignment = itemAlignment
                                ) {
                                    Column(
                                        horizontalAlignment = subColumnAlignment,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {

                                        Box(
                                            modifier = Modifier
                                                .width(180.dp)
                                                .height(4.dp)
                                                .background(themeAccentColor.copy(alpha = 0.15f))
                                        ) {
                                            val lineDurationMs = if (index < lyricLines.size - 1) {
                                                lyricLines[index + 1].timeMs - line.timeMs
                                            } else {
                                                5000L
                                            }
                                            val elapsedMs = if (isActive) (positionMs - line.timeMs).coerceIn(0L, lineDurationMs) else 0L
                                            val fraction = if (isActive && lineDurationMs > 0) (elapsedMs.toFloat() / lineDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(fraction)
                                                    .background(themeAccentColor)
                                            )
                                        }
                                    }
                                }
                            } else if (line.text.isNotBlank()) {
                                KaraokeLyricLineView(
                                    line = line,
                                    currentPlaybackPosition = if (isActive) positionMs else 0L,
                                    isActive = isActive,
                                    baseColor = animatedColor,
                                    accentColor = themeAccentColor,
                                    alignment = alignVal,
                                    fontSize = sizeVal,
                                    fontFamily = lyricsFont,
                                    modifier = Modifier
                                        .padding(vertical = itemSpacingVal + 6.dp, horizontal = 16.dp)
                                        .graphicsLayer(
                                            scaleX = animatedScale,
                                            scaleY = animatedScale,
                                            transformOrigin = scaleOrigin
                                        )
                                        .clickable(enabled = isTimedLyrics) { onSeek(line.timeMs) }
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Minimal play progress and controls bar at bottom of the full lyrics layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val range = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    var isDraggingSlider by remember { mutableStateOf(false) }
                    var localSliderVal by remember { mutableStateOf(0f) }
                    val sliderVal = if (isDraggingSlider) localSliderVal else range

                    Slider(
                        value = sliderVal,
                        onValueChange = {
                            isDraggingSlider = true
                            localSliderVal = it
                        },
                        onValueChangeFinished = {
                            onSeek((localSliderVal * durationMs).toLong())
                            isDraggingSlider = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = themeAccentColor,
                            activeTrackColor = themeAccentColor,
                            inactiveTrackColor = textPrimary.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatDuration(positionMs), color = textSecondary, fontSize = 10.sp)
                        Text(text = formatDuration(durationMs), color = textSecondary, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSkipPrevious) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "previous", tint = textPrimary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(onClick = onTogglePlay) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "play/pause",
                                tint = textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(onClick = onSkipNext) {
                            Icon(Icons.Default.SkipNext, contentDescription = "next", tint = textPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            } else {
                // Standard Playback screen!
                // Header buttons details (Centered arrow, lowered with 32dp top padding, no "synth engine" text)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "dismiss",
                            tint = textPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Dynamic player lowering spacer to push the entire layout down
                Spacer(modifier = Modifier.weight(if (settings.previewLyrics) 0.8f else 1f))

                // Cover Art Box - dynamically sized depending on whether lyrics preview is shown (optimized large designs!)
                val coverArtSize = if (settings.previewLyrics) 240.dp else 320.dp
                val symbolFontSize = if (settings.previewLyrics) 105.sp else 140.sp

                if (settings.enableCoverArt) {
                    com.example.ui.components.TrackCoverImage(
                        track = track,
                        resolution = settings.coverArtResolution,
                        modifier = Modifier
                            .size(coverArtSize)
                            .let { if (borderThicknessDp > 0.dp) it.border(borderThicknessDp, coverBorderColor, coverShape) else it }
                            .clip(coverShape),
                        fallbackSymbol = "♬",
                        symbolFontSize = symbolFontSize,
                        themeAccentColor = themeAccentColor,
                        isLight = isPlayerBgLight
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(coverArtSize)
                            .let { if (borderThicknessDp > 0.dp) it.border(borderThicknessDp, coverBorderColor, coverShape) else it }
                            .clip(coverShape)
                            .background(if (isPlayerBgLight) Color(0xFFEAF4FC) else Color(0xFF0F0F0F)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Large simple music symbol placeholder - absolutely no wave animation
                        Text(
                            text = "♬",
                            color = themeAccentColor,
                            fontSize = symbolFontSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Track details - Left-aligned perfectly matching the cover art edges
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = textPrimary,
                            fontWeight = FontWeight.Light,
                            fontSize = 25.sp,
                            letterSpacing = (-1).sp,
                            textAlign = TextAlign.Start
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth().basicMarquee()
                    )

                    Text(
                        text = track.artist.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = themeAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Start
                        ),
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }



                // Slider timeline progress loader with seek jump fix
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val range = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    var isDraggingSlider by remember { mutableStateOf(false) }
                    var localSliderVal by remember { mutableStateOf(0f) }
                    val sliderVal = if (isDraggingSlider) localSliderVal else range

                    Slider(
                        value = sliderVal,
                        onValueChange = {
                            isDraggingSlider = true
                            localSliderVal = it
                        },
                        onValueChangeFinished = {
                            onSeek((localSliderVal * durationMs).toLong())
                            isDraggingSlider = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = themeAccentColor,
                            activeTrackColor = themeAccentColor,
                            inactiveTrackColor = textPrimary.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatDuration(positionMs), color = textSecondary, fontSize = 11.sp)
                        Text(text = formatDuration(durationMs), color = textSecondary, fontSize = 11.sp)
                    }
                }

                // Primary buttons controls row (Previous, Play/Pause, Next) - UPPER row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "previous", tint = textPrimary, modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.width(28.dp))

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(textPrimary.copy(alpha = 0.08f), CircleShape)
                            .border(2.dp, themeAccentColor, CircleShape)
                            .clickable { onTogglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "play/pause",
                            tint = textPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(28.dp))

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "next", tint = textPrimary, modifier = Modifier.size(32.dp))
                    }
                }

                // Secondary buttons controls row: Symmetrical layout: [Shuffle] [Favorite] [Lyrics] [Tune] [Repeat]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // SHUFFLE
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "shuffle",
                            tint = if (isShuffle) themeAccentColor else textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // FAVORITE (LIKE)
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "favorite",
                            tint = if (isFavorite) themeAccentColor else textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // EXPAND LYRICS
                    IconButton(
                        onClick = { onToggleLyricsExpanded(true) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "expanded lyrics",
                            tint = textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // TUNE / AMP
                    IconButton(
                        onClick = { isMoreOptionsSheetVisible = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "advanced settings",
                            tint = textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // REPEAT
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "repeat",
                            tint = if (isRepeat) themeAccentColor else textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Apple Music Styled Sync Lyrics scrolling block (Preview - dynamically sized to fill empty space)
                if (settings.previewLyrics) {
                    val previewFont = getMetroFontFamily(settings.lyricsFontFamily)
                    val previewAlignVal = TextAlign.Center
                    val previewSpacingVal = when (settings.lyricsSpacing.lowercase()) {
                        "tight" -> 0.dp
                        "spacious" -> 8.dp
                        else -> 3.dp
                    }
                    val previewSizeVal = when (settings.lyricsFontSize.lowercase()) {
                        "small" -> 11.sp
                        "large" -> 17.sp
                        else -> 14.sp
                    }
                    val previewScaleOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)

                    LazyColumn(
                        state = previewLyricsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        userScrollEnabled = false
                    ) {
                        if (lyricLines.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No lyric",
                                        color = textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(lyricLines) { index, line ->
                                val isActive = isTimedLyrics && index == activePositionIndex
                                val targetColor = if (!isTimedLyrics) textPrimary.copy(alpha = 0.9f) else if (isActive) themeAccentColor else textPrimary.copy(alpha = 0.35f)
                                val targetScale = if (!isTimedLyrics) 1.0f else if (isActive) 1.1f else 0.92f
                                val animatedColor by animateColorAsState(targetValue = targetColor, label = "p_color_$index")
                                val animatedScale by animateFloatAsState(targetValue = targetScale, label = "p_scale_$index")

                                if (line.text.isBlank() && isActive) {
                                    val itemAlignment = Alignment.Center
                                    val subColumnAlignment = Alignment.CenterHorizontally
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = previewSpacingVal + 3.dp, horizontal = 12.dp),
                                        contentAlignment = itemAlignment
                                    ) {
                                        Column(
                                            horizontalAlignment = subColumnAlignment,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {

                                            Box(
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .height(3.dp)
                                                    .background(themeAccentColor.copy(alpha = 0.15f))
                                            ) {
                                                val lineDurationMs = if (index < lyricLines.size - 1) {
                                                    lyricLines[index + 1].timeMs - line.timeMs
                                                } else {
                                                    5000L
                                                }
                                                val elapsedMs = if (isActive) (positionMs - line.timeMs).coerceIn(0L, lineDurationMs) else 0L
                                                val fraction = if (isActive && lineDurationMs > 0) (elapsedMs.toFloat() / lineDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(fraction)
                                                        .background(themeAccentColor)
                                                )
                                            }
                                        }
                                    }
                                } else if (line.text.isNotBlank()) {
                                    KaraokeLyricLineView(
                                        line = line,
                                        currentPlaybackPosition = if (isActive) positionMs else 0L,
                                        isActive = isActive,
                                        baseColor = animatedColor,
                                        accentColor = themeAccentColor,
                                        alignment = previewAlignVal,
                                        fontSize = previewSizeVal,
                                        fontFamily = previewFont,
                                        modifier = Modifier
                                            .padding(vertical = previewSpacingVal + 3.dp, horizontal = 12.dp)
                                            .graphicsLayer(
                                                scaleX = animatedScale,
                                                scaleY = animatedScale,
                                                transformOrigin = previewScaleOrigin
                                            )
                                            .clickable(enabled = isTimedLyrics) { onSeek(line.timeMs) }
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic bottom weighted spacer to push controls down gracefully
                if (!settings.previewLyrics) {
                    Spacer(modifier = Modifier.weight(1.2f))
                } else {
                    Spacer(modifier = Modifier.weight(0.4f))
                }
            }
        }

        // DYNAMIC NESTED BOTTOM OPTION SHEET (Covers 82% height, requires swipe-up style scroll)
        AdvancedOptionsSheet(
            visible = isMoreOptionsSheetVisible,
            onDismissRequest = { isMoreOptionsSheetVisible = false },
            track = track,
            settings = settings,
            isPinned = isPinned,
            onTogglePinned = onTogglePinned,
            rawLyricsText = rawLyricsText,
            customLyricsMap = customLyricsMap,
            onUpdateLyrics = onUpdateLyrics,
            queue = queue,
            onPlayQueueTrack = onPlayQueueTrack,
            onDeleteTrack = onDeleteTrack,
            onDismiss = onDismiss,
            themeAccentColor = themeAccentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            isLight = isLight
        )

        if (false) {
            AnimatedVisibility(
                visible = isMoreOptionsSheetVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.fillMaxSize()
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                // Direct click outside overlay to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.18f)
                        .clickable { isMoreOptionsSheetVisible = false }
                        .align(Alignment.TopCenter)
                )

                // The options board sheet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.82f)
                        .border(1.dp, themeAccentColor, androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                        .align(Alignment.BottomCenter),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = if (settings.themeMode == "light") Color(0xFFECECEC) else if (settings.themeMode == "amoled") Color.Black else Color(0xFF101010))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        // Drag indicators bar
                        Box(
                            modifier = Modifier
                                .size(44.dp, 4.dp)
                                .background(textPrimary.copy(alpha = 0.25f), CircleShape)
                                .align(Alignment.CenterHorizontally)
                                .clickable { isMoreOptionsSheetVisible = false }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "MORE SETTINGS",
                                color = themeAccentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = getMetroFontFamily(settings.fontFamily)
                            )
                            IconButton(onClick = { isMoreOptionsSheetVisible = false }) {
                                Icon(Icons.Default.Close, contentDescription = "close", tint = textPrimary, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // Pin Track setting option item
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("PIN TRACK TO HUB HOME", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("manually pin this song to play it directly from the front-page tiles screen.", color = textSecondary, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Button(
                                        onClick = onTogglePinned,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, themeAccentColor),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPinned) themeAccentColor else Color.Transparent,
                                            contentColor = if (isPinned) (if (isLight) Color.White else Color.Black) else textPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (isPinned) "PINNED TO HUB HOME" else "PIN TO HUB HOME",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                Divider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(top = 12.dp))
                            }

                            // Synced Lyrics
                            item {
                                var editingLyrics by remember { mutableStateOf(false) }
                                var lyricsInputText by remember { mutableStateOf("") }
                                LaunchedEffect(rawLyricsText) {
                                    lyricsInputText = rawLyricsText
                                }

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("EDIT LYRICS", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        IconButton(onClick = { 
                                            coroutineScope.launch {
                                                val loadedText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    getLyricsForTrack(track, customLyricsMap)
                                                 }
                                                 lyricsInputText = loadedText
                                                 editingLyrics = true
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "edit lyrics",
                                                tint = themeAccentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (editingLyrics) {
                                        androidx.compose.ui.window.Dialog(onDismissRequest = { editingLyrics = false }) {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                                    .fillMaxHeight(0.75f),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                                border = androidx.compose.foundation.BorderStroke(2.dp, themeAccentColor),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isLight) Color.White else Color(0xFF141414)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(16.dp)
                                                ) {
                                                    Text(
                                                        text = "EDIT LYRICS FOR: ${track.title.uppercase()}",
                                                        color = textPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    
                                                    OutlinedTextField(
                                                        value = lyricsInputText,
                                                        onValueChange = { lyricsInputText = it },
                                                        label = { Text("XML TTML or LRC text strings...", color = textSecondary, fontSize = 11.sp) },
                                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                                        textStyle = androidx.compose.ui.text.TextStyle(
                                                            color = textPrimary, 
                                                            fontSize = 11.sp, 
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                        ),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = textPrimary,
                                                            unfocusedTextColor = textPrimary,
                                                            focusedBorderColor = themeAccentColor,
                                                            unfocusedBorderColor = textSecondary.copy(alpha = 0.4f),
                                                            focusedContainerColor = Color.Transparent,
                                                            unfocusedContainerColor = Color.Transparent
                                                        )
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        MetroButton(
                                                            text = "cancel",
                                                            accentHex = "#666666",
                                                            onClick = { editingLyrics = false }
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        MetroButton(
                                                            text = "apply",
                                                            accentHex = settings.accentColorHex,
                                                            onClick = {
                                                                onUpdateLyrics(track.id, lyricsInputText)
                                                                editingLyrics = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Scrollable live display block (always visible inside settings options item row)
                                    if (true) {
                                        // Scrollable live display block
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(130.dp)
                                                .background(textPrimary.copy(alpha = 0.03f))
                                                .border(1.dp, textPrimary.copy(alpha = 0.08f))
                                                .padding(10.dp)
                                         ) {
                                             if (lyricLines.isEmpty()) {
                                                Text("no synced LRC or Apple TTML formatting strings loaded.", color = textSecondary, fontSize = 12.sp)
                                            } else {
                                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                    items(lyricLines) { line ->
                                                        if (line.text.isNotBlank()) {
                                                            Text(
                                                                text = line.text,
                                                                color = textPrimary.copy(alpha = 0.7f),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Normal,
                                                                modifier = Modifier.padding(vertical = 3.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Divider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(top = 12.dp))
                            }

                            // Active Queue
                            item {
                                Column {
                                    Text("CURRENT AUDIENCE QUEUE", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("shows what tracks reside in active pipeline buffer.", color = textSecondary, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Column {
                                            queue.forEach { qTrack ->
                                                val isPlay = qTrack.id == track.id
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(if (isPlay) themeAccentColor.copy(alpha = 0.12f) else Color.Transparent)
                                                        .clickable { onPlayQueueTrack(qTrack) }
                                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlay) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = if (isPlay) themeAccentColor else textSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(qTrack.title, color = if (isPlay) themeAccentColor else textPrimary, fontSize = 13.sp)
                                                        Text(qTrack.artist.lowercase(), color = textSecondary, fontSize = 11.sp)
                                                    }
                                                }
                                                Divider(color = textPrimary.copy(alpha = 0.05f))
                                            }
                                        }
                                    }
                                }
                            }

                            // Infrastructure information cards
                            item {
                                Divider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(top = 8.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("SONG INFORMATION", color = themeAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = (-0.3).sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val fileFormat = remember(track) {
                                        if (track.isSynth) {
                                            "Procedural Synth"
                                        } else {
                                            val ext = track.path.substringAfterLast('.', "").uppercase()
                                            if (ext.isNotEmpty()) ext else "MP3"
                                        }
                                    }

                                    var fileSizeStr by remember(track) { mutableStateOf("Calculating...") }
                                    LaunchedEffect(track) {
                                        if (track.isSynth) {
                                            fileSizeStr = "N/A (RAM Generated)"
                                        } else {
                                            fileSizeStr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    val file = java.io.File(track.path)
                                                    if (file.exists() && file.isFile) {
                                                        val sizeBytes = file.length()
                                                        val sizeMb = sizeBytes.toDouble() / (1024 * 1024)
                                                        String.format("%.2f MB", sizeMb)
                                                    } else {
                                                        "Unknown Size"
                                                    }
                                                } catch (e: java.lang.Exception) {
                                                    "Unknown Size"
                                                }
                                            }
                                        }
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, textPrimary.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                        colors = CardDefaults.cardColors(containerColor = textPrimary.copy(alpha = 0.02f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Title", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(track.title, color = textPrimary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                            }
                                            Divider(color = textPrimary.copy(alpha = 0.04f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Artist", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(track.artist, color = textPrimary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                            }
                                            Divider(color = textPrimary.copy(alpha = 0.04f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Album", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(track.album.ifEmpty { "Unknown" }, color = textPrimary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                            }
                                            Divider(color = textPrimary.copy(alpha = 0.04f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Genre", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(track.genre.ifEmpty { "Unknown" }, color = textPrimary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                            }
                                            Divider(color = textPrimary.copy(alpha = 0.04f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Audio Format", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(fileFormat, color = themeAccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Divider(color = textPrimary.copy(alpha = 0.04f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Duration", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(formatDuration(track.durationMs), color = textPrimary, fontSize = 11.sp)
                                            }
                                            Divider(color = textPrimary.copy(alpha = 0.04f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("File Size", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(fileSizeStr, color = textPrimary, fontSize = 11.sp)
                                            }
                                            Divider(color = textPrimary.copy(alpha = 0.04f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("File Path", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = track.path,
                                                    color = textSecondary.copy(alpha = 0.8f),
                                                    fontSize = 10.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                    modifier = Modifier.weight(1f).padding(start = 16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Divider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(top = 12.dp))
                            }

                            // Delete Action
                            item {
                                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                    Text("DELETE SONG", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Button(
                                        onClick = {
                                            isMoreOptionsSheetVisible = false
                                            onDismiss()
                                            onDeleteTrack(track)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "delete local storage",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("DELETE SONG FROM DEVICE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        } // ending the if(false) block of old layout
    }
}

fun formatTrackDurationGlobal(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun AdvancedOptionsSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    track: Track,
    settings: com.example.data.database.UserSettingsEntity,
    isPinned: Boolean,
    onTogglePinned: () -> Unit,
    rawLyricsText: String,
    customLyricsMap: Map<String, String>,
    onUpdateLyrics: (String, String) -> Unit,
    queue: List<Track>,
    onPlayQueueTrack: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onDismiss: () -> Unit,
    themeAccentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    isLight: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val isTtml = rawLyricsText.contains("<tt", ignoreCase = true) || rawLyricsText.contains("<p", ignoreCase = true)
    val lyricLines = remember(rawLyricsText) { if (isTtml) parseTtml(rawLyricsText) else parseLrc(rawLyricsText) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // Direct click outside overlay to dismiss
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.18f)
                    .clickable { onDismissRequest() }
                    .align(Alignment.TopCenter)
            )

            // The options board sheet
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .border(1.dp, themeAccentColor, androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                    .align(Alignment.BottomCenter),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = if (settings.themeMode == "light") Color(0xFFECECEC) else if (settings.themeMode == "amoled") Color.Black else Color(0xFF101010))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    // Drag indicators bar
                    Box(
                        modifier = Modifier
                            .size(44.dp, 4.dp)
                            .background(textPrimary.copy(alpha = 0.25f), CircleShape)
                            .align(Alignment.CenterHorizontally)
                            .clickable { onDismissRequest() }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "MORE SETTINGS",
                            color = themeAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = getMetroFontFamily(settings.fontFamily)
                        )
                        IconButton(onClick = { onDismissRequest() }) {
                            Icon(Icons.Default.Close, contentDescription = "close", tint = textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Pin Track setting option item
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("PIN SONG TO HOME", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Pin this song to the Home screen for quick play.", color = textSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Button(
                                    onClick = onTogglePinned,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, themeAccentColor),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPinned) themeAccentColor else Color.Transparent,
                                        contentColor = if (isPinned) (if (isLight) Color.White else Color.Black) else textPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isPinned) "PINNED TO HOME" else "PIN TO HOME",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            Divider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(top = 12.dp))
                        }

                        // Synced Lyrics
                        item {
                            var editingLyrics by remember { mutableStateOf(false) }
                            var lyricsInputText by remember { mutableStateOf("") }
                            LaunchedEffect(rawLyricsText) {
                                lyricsInputText = rawLyricsText
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("EDIT LYRICS", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    IconButton(onClick = { 
                                        coroutineScope.launch {
                                            val loadedText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                getLyricsForTrack(track, customLyricsMap)
                                             }
                                             lyricsInputText = loadedText
                                             editingLyrics = true
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "edit lyrics",
                                            tint = themeAccentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (editingLyrics) {
                                    androidx.compose.ui.window.Dialog(onDismissRequest = { editingLyrics = false }) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                                .fillMaxHeight(0.75f),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                            border = androidx.compose.foundation.BorderStroke(2.dp, themeAccentColor),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isLight) Color.White else Color(0xFF141414)
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(16.dp)
                                            ) {
                                                Text(
                                                    text = "EDIT LYRICS FOR: ${track.title.uppercase()}",
                                                    color = textPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                OutlinedTextField(
                                                    value = lyricsInputText,
                                                    onValueChange = { lyricsInputText = it },
                                                    label = { Text("XML TTML or LRC text strings...", color = textSecondary, fontSize = 11.sp) },
                                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                                    textStyle = androidx.compose.ui.text.TextStyle(
                                                        color = textPrimary, 
                                                        fontSize = 11.sp, 
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                    ),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedTextColor = textPrimary,
                                                        unfocusedTextColor = textPrimary,
                                                        focusedBorderColor = themeAccentColor,
                                                        unfocusedBorderColor = textSecondary.copy(alpha = 0.4f),
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent
                                                    )
                                                )
                                                
                                                Spacer(modifier = Modifier.height(16.dp))
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    MetroButton(
                                                        text = "cancel",
                                                        accentHex = "#666666",
                                                        onClick = { editingLyrics = false }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    MetroButton(
                                                        text = "apply",
                                                        accentHex = settings.accentColorHex,
                                                        onClick = {
                                                            onUpdateLyrics(track.id, lyricsInputText)
                                                            editingLyrics = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Scrollable live display block (always visible inside settings options item row)
                                if (true) {
                                    // Scrollable live display block
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .background(textPrimary.copy(alpha = 0.03f))
                                            .border(1.dp, textPrimary.copy(alpha = 0.08f))
                                            .padding(10.dp)
                                     ) {
                                         if (lyricLines.isEmpty()) {
                                            Text("no synced LRC or Apple TTML formatting strings loaded.", color = textSecondary, fontSize = 12.sp)
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(lyricLines) { line ->
                                                    if (line.text.isNotBlank()) {
                                                        Text(
                                                            text = line.text,
                                                            color = textPrimary.copy(alpha = 0.7f),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Normal,
                                                            modifier = Modifier.padding(vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                     }
                                }
                            }
                            Divider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(top = 12.dp))
                        }

                        // Active Queue
                        item {
                            Column {
                                Text("PLAYING QUEUE", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("List of upcoming songs.", color = textSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Column {
                                        queue.forEach { qTrack ->
                                            val isPlay = qTrack.id == track.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(if (isPlay) themeAccentColor.copy(alpha = 0.12f) else Color.Transparent)
                                                    .clickable { onPlayQueueTrack(qTrack) }
                                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlay) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = if (isPlay) themeAccentColor else textSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(qTrack.title, color = if (isPlay) themeAccentColor else textPrimary, fontSize = 13.sp)
                                                    Text(qTrack.artist.lowercase(), color = textSecondary, fontSize = 11.sp)
                                                }
                                            }
                                            Divider(color = textPrimary.copy(alpha = 0.05f))
                                        }
                                    }
                                }
                            }
                        }

                        // Infrastructure information cards
                        item {
                            Divider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(top = 8.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("SONG INFORMATION", color = themeAccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = (-0.3).sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                val fileFormat = remember(track) {
                                    if (track.isSynth) {
                                        "Procedural Synth"
                                    } else {
                                        val ext = track.path.substringAfterLast('.', "").uppercase()
                                        if (ext.isNotEmpty()) ext else "MP3"
                                    }
                                }

                                var fileSizeStr by remember(track) { mutableStateOf("Calculating...") }
                                LaunchedEffect(track) {
                                    if (track.isSynth) {
                                        fileSizeStr = "N/A (RAM Generated)"
                                    } else {
                                        fileSizeStr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val file = java.io.File(track.path)
                                                if (file.exists() && file.isFile) {
                                                    val sizeBytes = file.length()
                                                    val sizeMb = sizeBytes.toDouble() / (1024 * 1024)
                                                    String.format("%.2f MB", sizeMb)
                                                } else {
                                                    "Unknown Size"
                                                }
                                            } catch (e: java.lang.Exception) {
                                                "Unknown Size"
                                            }
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, textPrimary.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                    colors = CardDefaults.cardColors(containerColor = textPrimary.copy(alpha = 0.02f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Title", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(track.title, color = textPrimary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                        }
                                        Divider(color = textPrimary.copy(alpha = 0.04f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Artist", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(track.artist, color = textPrimary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                        }
                                        Divider(color = textPrimary.copy(alpha = 0.04f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Album", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(track.album.ifEmpty { "Unknown" }, color = textPrimary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                        }
                                        Divider(color = textPrimary.copy(alpha = 0.04f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Genre", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(track.genre.ifEmpty { "Unknown" }, color = textPrimary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
                                        }
                                        Divider(color = textPrimary.copy(alpha = 0.04f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Audio Format", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(fileFormat, color = themeAccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Divider(color = textPrimary.copy(alpha = 0.04f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Duration", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(formatTrackDurationGlobal(track.durationMs), color = textPrimary, fontSize = 11.sp)
                                        }
                                        Divider(color = textPrimary.copy(alpha = 0.04f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("File Size", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(fileSizeStr, color = textPrimary, fontSize = 11.sp)
                                        }
                                        Divider(color = textPrimary.copy(alpha = 0.04f))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("File Path", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = track.path,
                                                color = textSecondary.copy(alpha = 0.8f),
                                                fontSize = 10.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                modifier = Modifier.weight(1f).padding(start = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Divider(color = textPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(top = 12.dp))
                        }

                        // Delete Action
                        item {
                            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                Text("DELETE SONG", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(6.dp))

                                Button(
                                    onClick = {
                                        onDismissRequest()
                                        onDismiss()
                                        onDeleteTrack(track)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "delete local storage",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("DELETE SONG FROM DEVICE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// LYRIC FORMAT PARSING HARNESS
data class LyricWord(val timeMs: Long, val text: String)
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun KaraokeLyricLineView(
    line: LyricLine,
    currentPlaybackPosition: Long,
    isActive: Boolean,
    baseColor: Color,
    accentColor: Color,
    alignment: TextAlign,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier
) {
    if (line.words.isEmpty() || !isActive) {
        Text(
            text = line.text,
            color = baseColor,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            textAlign = alignment,
            lineHeight = fontSize * 1.4f,
            modifier = modifier
        )
    } else {
        val horizontalArrangement = when (alignment) {
            TextAlign.Left -> Arrangement.Start
            TextAlign.Right -> Arrangement.End
            else -> Arrangement.Center
        }
        
        androidx.compose.foundation.layout.FlowRow(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.Center
        ) {
            line.words.forEach { word ->
                val isWordActive = currentPlaybackPosition >= word.timeMs
                val targetWordColor = if (isWordActive && isActive) {
                    accentColor
                } else if (isActive) {
                    baseColor.copy(alpha = 0.55f)
                } else {
                    baseColor.copy(alpha = 0.35f)
                }
                
                val wordColor by androidx.compose.animation.animateColorAsState(
                    targetValue = targetWordColor,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
                )
                val wordWeight = if (isWordActive && isActive) FontWeight.Bold else FontWeight.Medium
                
                Text(
                    text = word.text + " ",
                    color = wordColor,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    fontWeight = wordWeight,
                    lineHeight = fontSize * 1.4f
                )
            }
        }
    }
}

fun parseLrc(lyricsStr: String): List<LyricLine> {
    val results = mutableListOf<LyricLine>()
    val lines = lyricsStr.lines()
    val pattern = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")

    for (line in lines) {
        val trimmedLine = line.trim()
        val match = pattern.matchEntire(trimmedLine)
        if (match != null) {
            val min = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val sec = match.groups[2]?.value?.toLongOrNull() ?: 0L
            val msVal = match.groups[3]?.value ?: "00"
            val ms = when (msVal.length) {
                2 -> msVal.toLong() * 10
                3 -> msVal.toLong()
                else -> 0L
            }
            var content = match.groups[4]?.value?.trim() ?: ""
            if (content.startsWith("v1:")) content = content.removePrefix("v1:").trim()
            if (content.startsWith("v2:")) content = content.removePrefix("v2:").trim()
            if (content.startsWith("<v")) {
                content = content.replace(Regex("<v[^>]*>"), "").trim()
            }
            val computedTime = (min * 60 * 1000) + (sec * 1000) + ms
            
            // Check if there are inline/progressive timestamps like <00:05.50>
            val isEnhanced = content.contains("<") && content.contains(">")
            if (isEnhanced) {
                val words = mutableListOf<LyricWord>()
                val inlinePattern = Regex("<(?:(\\d{2}):)?(\\d{2}):(\\d{2})\\.(\\d{2,4})>")
                val tags = inlinePattern.findAll(content).toList()
                if (tags.isNotEmpty()) {
                    for (i in tags.indices) {
                        val currTag = tags[i]
                        val wordHr = currTag.groups[1]?.value?.toLongOrNull() ?: 0L
                        val wordMin = currTag.groups[2]?.value?.toLongOrNull() ?: 0L
                        val wordSec = currTag.groups[3]?.value?.toLongOrNull() ?: 0L
                        val wordMsVal = currTag.groups[4]?.value ?: "000"
                        val wordMs = when (wordMsVal.length) {
                            1 -> wordMsVal.toLong() * 100
                            2 -> wordMsVal.toLong() * 10
                            3 -> wordMsVal.toLong()
                            else -> wordMsVal.take(3).toLong()
                        }
                        val wordTime = (wordHr * 3600 * 1000) + (wordMin * 60 * 1000) + (wordSec * 1000) + wordMs
                        
                        val startIdx = currTag.range.last + 1
                        val endIdx = if (i + 1 < tags.size) tags[i + 1].range.first else content.length
                        val wordVal = content.substring(startIdx, endIdx).replace(Regex("<[^>]*>"), "").trim()
                        if (wordVal.isNotEmpty()) {
                            words.add(LyricWord(wordTime, wordVal))
                        }
                    }
                }
                
                val cleanText = content.replace(Regex("<[^>]*>"), "").trim()
                results.add(LyricLine(computedTime, cleanText, words))
            } else {
                results.add(LyricLine(computedTime, content))
            }
        } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("[")) {
            val clean = trimmedLine.replace(Regex("<[^>]*>"), "").trim()
            results.add(LyricLine(0L, clean))
        }
    }
    val sorted = results.sortedBy { it.timeMs }
    val isTimed = sorted.any { it.timeMs > 0 }
    if (isTimed && sorted.isNotEmpty() && sorted[0].timeMs > 3000L) {
        return listOf(LyricLine(0L, "")) + sorted
    }
    return sorted
}

fun parseTtml(lyricsStr: String): List<LyricLine> {
    val results = mutableListOf<LyricLine>()
    val lines = lyricsStr.lines()

    // 1. Regular expression for parsing XML elements like <p begin="00:05.100">Some text</p>
    val xmlPattern = Regex("<p\\s+begin=\"([^\"]+)\".*?>(.*?)</p>", RegexOption.IGNORE_CASE)

    // Helper to extract time from any string
    fun parseTimeToMs(timeStr: String): Long {
        val trimmed = timeStr.trim()
        if (trimmed.isEmpty()) return 0L
        if (trimmed.endsWith("ms", ignoreCase = true)) {
            return trimmed.removeSuffix("ms").trim().toDoubleOrNull()?.toLong() ?: 0L
        }
        if (trimmed.endsWith("s", ignoreCase = true)) {
            return (trimmed.removeSuffix("s").trim().toDoubleOrNull()?.let { it * 1000 }?.toLong()) ?: 0L
        }
        val doubleVal = trimmed.toDoubleOrNull()
        if (doubleVal != null) {
            return (doubleVal * 1000).toLong()
        }
        val parts = trimmed.split(":")
        return try {
            if (parts.size == 3) {
                val hr = parts[0].toLongOrNull() ?: 0L
                val min = parts[1].toLongOrNull() ?: 0L
                val secParts = parts[2].split(".")
                val sec = secParts[0].toLongOrNull() ?: 0L
                val msVal = if (secParts.size > 1) secParts[1] else "0"
                val ms = when (msVal.length) {
                    1 -> msVal.toLong() * 100
                    2 -> msVal.toLong() * 10
                    3 -> msVal.toLong()
                    else -> msVal.take(3).toLong()
                }
                (hr * 3600 * 1000) + (min * 60 * 1000) + (sec * 1000) + ms
            } else if (parts.size == 2) {
                val min = parts[0].toLongOrNull() ?: 0L
                val secParts = parts[1].split(".")
                val sec = secParts[0].toLongOrNull() ?: 0L
                val msVal = if (secParts.size > 1) secParts[1] else "0"
                val ms = when (msVal.length) {
                    1 -> msVal.toLong() * 100
                    2 -> msVal.toLong() * 10
                    3 -> msVal.toLong()
                    else -> msVal.take(3).toLong()
                }
                (min * 60 * 1000) + (sec * 1000) + ms
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    // 2. Regular expression for inline VTT/custom timestamps like <00:06.333> or <00:00:06.333>
    val inlinePattern = Regex("<(?:(\\d{2}):)?(\\d{2}):(\\d{2})\\.(\\d{2,4})>")

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        // Check XML pattern first
        val xmlMatch = xmlPattern.find(trimmed)
        if (xmlMatch != null) {
            val beginStr = xmlMatch.groups[1]?.value ?: "0"
            val computedTime = parseTimeToMs(beginStr)
            var pText = xmlMatch.groups[2]?.value ?: ""
            if (pText.startsWith("v1:")) pText = pText.removePrefix("v1:").trim()
            if (pText.startsWith("v2:")) pText = pText.removePrefix("v2:").trim()
            if (pText.startsWith("<v")) {
                pText = pText.replace(Regex("<v[^>]*>"), "").trim()
            }
            
            // Check if there are <span> elements with begin timestamps inside
            val spanPattern = Regex("<span\\s+begin=\"([^\"]+)\"[^>]*>(.*?)</span>", RegexOption.IGNORE_CASE)
            val spanMatches = spanPattern.findAll(pText).toList()
            if (spanMatches.isNotEmpty()) {
                val words = mutableListOf<LyricWord>()
                for (m in spanMatches) {
                    val wordBegin = m.groups[1]?.value ?: ""
                    val wordText = m.groups[2]?.value?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
                    if (wordBegin.isNotEmpty() && wordText.isNotEmpty()) {
                        words.add(LyricWord(parseTimeToMs(wordBegin), wordText))
                    }
                }
                val cleanText = pText.replace(Regex("<[^>]*>"), "").trim()
                results.add(LyricLine(computedTime, cleanText, words))
            } else {
                val cleanText = pText.replace(Regex("<[^>]*>"), "").trim()
                results.add(LyricLine(computedTime, cleanText))
            }
            continue
        }

        // Check inline timestamp pattern
        val inlineMatch = inlinePattern.find(trimmed)
        if (inlineMatch != null) {
            val hr = inlineMatch.groups[1]?.value?.toLongOrNull() ?: 0L
            val min = inlineMatch.groups[2]?.value?.toLongOrNull() ?: 0L
            val sec = inlineMatch.groups[3]?.value?.toLongOrNull() ?: 0L
            val msVal = inlineMatch.groups[4]?.value ?: "000"
            val ms = when (msVal.length) {
                1 -> msVal.toLong() * 100
                2 -> msVal.toLong() * 10
                3 -> msVal.toLong()
                else -> inlineMatch.groups[4]?.value?.take(3)?.toLongOrNull() ?: 0L
            }
            val computedTime = (hr * 3600 * 1000) + (min * 60 * 1000) + (sec * 1000) + ms
            
            var cleanText = trimmed.replace(inlineMatch.value, "").trim()
            if (cleanText.startsWith("v1:")) cleanText = cleanText.removePrefix("v1:").trim()
            if (cleanText.startsWith("v2:")) cleanText = cleanText.removePrefix("v2:").trim()
            if (cleanText.startsWith("<v")) cleanText = cleanText.substringAfter(">").trim()
            cleanText = cleanText.replace(Regex("<[^>]*>"), "").trim()
            results.add(LyricLine(computedTime, cleanText))
        }
    }

    if (results.isEmpty()) {
        val plainText = lyricsStr.replace(Regex("<[^>]*>"), "").trim()
        if (plainText.isNotEmpty()) {
            results.add(LyricLine(0L, plainText))
        }
    }
    val sorted = results.sortedBy { it.timeMs }
    val isTimed = sorted.any { it.timeMs > 0 }
    if (isTimed && sorted.isNotEmpty() && sorted[0].timeMs > 3000L) {
        return listOf(LyricLine(0L, "")) + sorted
    }
    return sorted
}

fun extractEmbeddedLyrics(filePath: String): String? {
    val file = java.io.File(filePath)
    if (!file.exists() || !file.isFile) return null
    try {
        val maxHeaderRead = 4 * 1024 * 1024 // 4MB is more than enough for any metadata headers!
        val bytes = java.io.FileInputStream(file).use { input ->
            val buf = ByteArray(minOf(file.length(), maxHeaderRead.toLong()).toInt())
            var bytesRead = 0
            while (bytesRead < buf.size) {
                val read = input.read(buf, bytesRead, buf.size - bytesRead)
                if (read == -1) break
                bytesRead += read
            }
            if (bytesRead == buf.size) buf else buf.copyOf(bytesRead)
        }
        if (bytes.size < 64) return null
        
        // 1. MP3 ID3 Tag structured parser
        if (bytes[0] == 'I'.toByte() && bytes[1] == 'D'.toByte() && bytes[2] == '3'.toByte()) {
            val major = bytes[3].toInt() and 0xFF
            val size = ((bytes[6].toInt() and 0x7F) shl 21) or
                       ((bytes[7].toInt() and 0x7F) shl 14) or
                       ((bytes[8].toInt() and 0x7F) shl 7) or
                       (bytes[9].toInt() and 0x7F)
            if (size > 0) {
                var offset = 10
                val limit = minOf(10 + size, bytes.size)
                while (offset + 10 < limit) {
                    val frameId: String
                    val frameSize: Int
                    val headerSize: Int
                    
                    if (major == 2) {
                        frameId = String(bytes, offset, 3, Charsets.US_ASCII)
                        frameSize = ((bytes[offset + 3].toInt() and 0xFF) shl 16) or
                                    ((bytes[offset + 4].toInt() and 0xFF) shl 8) or
                                    (bytes[offset + 5].toInt() and 0xFF)
                        headerSize = 6
                    } else {
                        frameId = String(bytes, offset, 4, Charsets.US_ASCII)
                        val b0 = bytes[offset + 4].toInt() and 0xFF
                        val b1 = bytes[offset + 5].toInt() and 0xFF
                        val b2 = bytes[offset + 6].toInt() and 0xFF
                        val b3 = bytes[offset + 7].toInt() and 0xFF
                        
                        frameSize = if (major == 4) {
                            ((b0 and 0x7F) shl 21) or ((b1 and 0x7F) shl 14) or ((b2 and 0x7F) shl 7) or (b3 and 0x7F)
                        } else {
                            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
                        }
                        headerSize = 10
                    }
                    
                    if (frameSize <= 0 || offset + headerSize + frameSize > limit) {
                        break
                    }
                    
                    if (frameId == "USLT" || frameId == "ULT" || frameId == "SYLT") {
                        val dataOffset = offset + headerSize
                        if (frameSize > 4) {
                            val encoding = bytes[dataOffset].toInt() and 0xFF
                            var descOffset = dataOffset + 4
                            val maxSearch = dataOffset + frameSize
                            if (encoding == 0 || encoding == 3) {
                                while (descOffset < maxSearch && bytes[descOffset] != 0.toByte()) {
                                    descOffset++
                                }
                                descOffset++
                            } else {
                                while (descOffset + 1 < maxSearch && 
                                       !(bytes[descOffset] == 0.toByte() && bytes[descOffset + 1] == 0.toByte())) {
                                    descOffset++
                                }
                                descOffset += 2
                            }
                            val lyricsLen = (dataOffset + frameSize) - descOffset
                            if (lyricsLen > 0) {
                                val charset = when (encoding) {
                                    0 -> Charsets.ISO_8859_1
                                    1 -> Charsets.UTF_16
                                    2 -> Charsets.UTF_16BE
                                    3 -> Charsets.UTF_8
                                    else -> Charsets.UTF_8
                                }
                                val l = String(bytes, descOffset, lyricsLen, charset).trim()
                                if (l.isNotBlank()) return l
                            }
                        }
                    } else if (frameId == "TXXX") {
                        val dataOffset = offset + headerSize
                        if (frameSize > 5) {
                            val encoding = bytes[dataOffset].toInt() and 0xFF
                            val charset = when (encoding) {
                                0 -> Charsets.ISO_8859_1
                                1 -> Charsets.UTF_16
                                2 -> Charsets.UTF_16BE
                                3 -> Charsets.UTF_8
                                else -> Charsets.UTF_8
                            }
                            var descOffset = dataOffset + 1
                            val maxSearch = dataOffset + frameSize
                            var desc = ""
                            if (encoding == 0 || encoding == 3) {
                                var descLen = 0
                                while (descOffset + descLen < maxSearch && bytes[descOffset + descLen] != 0.toByte()) {
                                    descLen++
                                }
                                desc = String(bytes, descOffset, descLen, charset)
                                descOffset += descLen + 1
                            } else {
                                var descLen = 0
                                while (descOffset + descLen + 1 < maxSearch && 
                                       !(bytes[descOffset + descLen] == 0.toByte() && bytes[descOffset + descLen + 1] == 0.toByte())) {
                                    descLen += 2
                                }
                                desc = String(bytes, descOffset, descLen, charset)
                                descOffset += descLen + 2
                            }
                            if (desc.trim().lowercase() in listOf("lyrics", "unsyncedlyrics", "lrc")) {
                                val valLen = maxSearch - descOffset
                                if (valLen > 0) {
                                    val l = String(bytes, descOffset, valLen, charset).trim()
                                    if (l.isNotBlank()) return l
                                }
                            }
                        }
                    }
                    offset += headerSize + frameSize
                }
            }
        }
        
        // 2. Fallback brute-force byte scanner for USLT
        val limit = bytes.size
        for (i in 0 until limit - 12) {
            if (bytes[i] == 'U'.toByte() && bytes[i+1] == 'S'.toByte() && bytes[i+2] == 'L'.toByte() && bytes[i+3] == 'T'.toByte()) {
                val b0 = bytes[i + 4].toInt() and 0xFF
                val b1 = bytes[i + 5].toInt() and 0xFF
                val b2 = bytes[i + 6].toInt() and 0xFF
                val b3 = bytes[i + 7].toInt() and 0xFF
                
                val sizeStd = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
                val sizeSafe = ((b0 and 0x7F) shl 21) or ((b1 and 0x7F) shl 14) or ((b2 and 0x7F) shl 7) or (b3 and 0x7F)
                for (frameSize in listOf(sizeStd, sizeSafe)) {
                    if (frameSize > 4 && i + 10 + frameSize <= bytes.size) {
                        val dataOffset = i + 10
                        val encoding = bytes[dataOffset].toInt() and 0xFF
                        if (encoding in 0..3) {
                            var descOffset = dataOffset + 4
                            val maxSearch = dataOffset + frameSize
                            if (encoding == 0 || encoding == 3) {
                                while (descOffset < maxSearch && bytes[descOffset] != 0.toByte()) {
                                    descOffset++
                                }
                                descOffset++
                            } else {
                                while (descOffset + 1 < maxSearch && 
                                       !(bytes[descOffset] == 0.toByte() && bytes[descOffset + 1] == 0.toByte())) {
                                    descOffset++
                                }
                                descOffset += 2
                            }
                            val lyricsLen = (dataOffset + frameSize) - descOffset
                            if (lyricsLen > 2) {
                                val charset = when (encoding) {
                                    0 -> Charsets.ISO_8859_1
                                    1 -> Charsets.UTF_16
                                    2 -> Charsets.UTF_16BE
                                    3 -> Charsets.UTF_8
                                    else -> Charsets.UTF_8
                                }
                                val l = String(bytes, descOffset, lyricsLen, charset).trim()
                                if (l.length > 5) return l
                            }
                        }
                    }
                }
            }
        }
        
        // 3. Fallback brute-force scan for MP4/M4A "©lyr" (0xA9 'l' 'y' 'r')
        for (i in 0 until limit - 24) {
            if (bytes[i] == 0xA9.toByte() && bytes[i+1] == 0x6C.toByte() && bytes[i+2] == 0x79.toByte() && bytes[i+3] == 0x72.toByte()) {
                for (j in i + 4 until minOf(i + 32, limit - 16)) {
                    if (bytes[j] == 0x64.toByte() && bytes[j+1] == 0x61.toByte() && bytes[j+2] == 0x74.toByte() && bytes[j+3] == 0x61.toByte()) {
                        val b0 = bytes[j - 4].toInt() and 0xFF
                        val b1 = bytes[j - 3].toInt() and 0xFF
                        val b2 = bytes[j - 2].toInt() and 0xFF
                        val b3 = bytes[j - 1].toInt() and 0xFF
                        val dataSize = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
                        
                        if (dataSize > 16 && j + dataSize - 4 <= bytes.size) {
                            val payloadOffset = j + 12
                            val payloadLen = dataSize - 16
                            if (payloadLen > 0) {
                                val l = String(bytes, payloadOffset, payloadLen, Charsets.UTF_8).trim()
                                if (l.isNotBlank()) return l
                            }
                        }
                    }
                }
            }
        }
        
        // 4. Fallback brute-force scan for Vorbis comments (FLAC, OGG)
        val signatures = listOf(
            "LYRICS=" to 7,
            "lyrics=" to 7,
            "UNSYNCEDLYRICS=" to 16,
            "unsyncedlyrics=" to 16,
            "LRC=" to 4,
            "lrc=" to 4
        )
        for ((sig, sigLen) in signatures) {
            val sigBytes = sig.toByteArray(Charsets.US_ASCII)
            for (i in 0 until limit - sigLen - 10) {
                var match = true
                for (k in 0 until sigLen) {
                    if (bytes[i + k] != sigBytes[k]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    if (i >= 4) {
                        val b0 = bytes[i - 4].toInt() and 0xFF
                        val b1 = bytes[i - 3].toInt() and 0xFF
                        val b2 = bytes[i - 2].toInt() and 0xFF
                        val b3 = bytes[i - 1].toInt() and 0xFF
                        val totalStrLen = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                        
                        val lyricLen = totalStrLen - sigLen
                        if (lyricLen > 5 && i + sigLen + lyricLen <= bytes.size) {
                            val l = String(bytes, i + sigLen, lyricLen, Charsets.UTF_8).trim()
                            if (l.isNotBlank()) return l
                        }
                    }
                }
            }
        }
        
        // 5. Dynamic text detector for LRC format embedded anywhere in the bytes!
        val fileContentString = String(bytes, Charsets.ISO_8859_1)
        val lrcIndex = fileContentString.indexOf("[00:")
        if (lrcIndex != -1) {
            val sub = fileContentString.substring(lrcIndex)
            val lrcLines = mutableListOf<String>()
            val lines = sub.lines()
            for (line in lines) {
                if (line.contains(Regex("\\[\\d{2}:\\d{2}[.:]\\d{2}"))) {
                    lrcLines.add(line)
                } else if (lrcLines.isNotEmpty() && line.isNotBlank() && !line.startsWith("[")) {
                     lrcLines.add(line)
                } else if (lrcLines.size > 5 && line.trim().isEmpty()) {
                    break
                } else if (lrcLines.size > 140) {
                    break
                }
            }
            if (lrcLines.size >= 3) {
                val candidate = lrcLines.joinToString("\n")
                if (candidate.isNotBlank()) return candidate
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

val lyricsCache = java.util.concurrent.ConcurrentHashMap<String, String>()

fun getLyricsForTrack(track: Track, customLyricsMap: Map<String, String>): String {
    val custom = customLyricsMap[track.id]
    if (custom != null) return custom

    val cached = lyricsCache[track.id]
    if (cached != null) return cached

    val rawResult = getPlainLyricsForTrack(track)
    if (rawResult.isNotBlank()) {
        lyricsCache[track.id] = rawResult
    }
    return rawResult
}

private fun getPlainLyricsForTrack(track: Track): String {
    if (!track.isSynth && track.path.isNotEmpty()) {
        try {
            val fileObj = java.io.File(track.path)
            if (fileObj.exists()) {
                // 1. Try common sidecar lyric files in the same directory (standard approach for local music players)
                val parentDir = fileObj.parentFile
                if (parentDir != null && parentDir.exists()) {
                    val baseName = fileObj.nameWithoutExtension
                    val extensions = listOf("lrc", "ttml", "txt")
                    for (ext in extensions) {
                        val sidecar = java.io.File(parentDir, "$baseName.$ext")
                        if (sidecar.exists() && sidecar.isFile) {
                            try {
                                val content = sidecar.readText()
                                if (content.isNotBlank()) {
                                    return content
                                }
                            } catch (readEx: Exception) {
                                // Safe fallback to next extension
                            }
                        }
                    }
                }

                // 2. Try reading embedded tags via MediaMetadataRetriever
                val retriever = android.media.MediaMetadataRetriever()
                var embeddedLyrics: String? = null
                try {
                    retriever.setDataSource(track.path)
                    embeddedLyrics = retriever.extractMetadata(28)
                } catch (ex: Exception) {
                    // Safe fallback
                } finally {
                    try { retriever.release() } catch (re: Exception) {}
                }
                if (!embeddedLyrics.isNullOrBlank()) {
                    return embeddedLyrics
                }

                // 3. Robust custom embedded tags scanner (scans ID3 USLT, MP4 lyr, Vorbis Comments)
                val robustLyrics = extractEmbeddedLyrics(track.path)
                if (!robustLyrics.isNullOrBlank()) {
                    return robustLyrics
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    return when {
        track.id.contains("mock_1_mp3") -> {
            """[00:00.00]Welcome to Metro Sound Explorer
[00:04.00]Living in high contrast, color tiles
[00:08.50]Sliding left and right through the panoramic view
[00:13.00]Offline tunes pumping right to your hand
[00:18.00]Azune Player music experience living on!
[00:23.00]Every chord synthesized in real-time
[00:28.00]No internet required, local play sublimed
[00:33.50]Metro Music, bringing design back with grace
[00:39.00][Instrumental Solo]
[00:52.00]Beautiful lyrics syncing right down to the millisecond
[01:02.00]Adjust the pitch, feel the wave expand..."""
        }
        track.id.contains("mock_2_flac") -> {
            """<tt xml:lang="en">
  <body>
    <div>
      <p begin="00:00.00">Floating through the Aero timeline</p>
      <p begin="00:05.100">Semi-translucent, frosty layouts on display</p>
      <p begin="00:10.200">Lossless music streams without relay</p>
      <p begin="00:15.500">Aero Transparency brings the light of day</p>
      <p begin="00:21.000">A beautiful chord synth generated procedurally</p>
      <p begin="00:28.000">[Dynamic Instrumental Solo - Enjoy the clean spectrum]</p>
    </div>
  </body>
</tt>"""
        }
        track.isSynth -> {
            """[00:00.00]Procedural Wave Harmonics Active
[00:05.100]Clock cycle frequency generation synchronized
[00:10.500]Rendering real-time low-pass soundwaves
[00:15.300]No physical file required on disks
[00:20.900]Enjoy the pure retro synthesized chords!"""
        }
        else -> {
            ""
        }
    }
}

@Composable
fun WelcomeScreen(
    settings: com.example.data.database.UserSettingsEntity,
    isScanning: Boolean,
    scanProgress: Float,
    scanStatusMessage: String,
    onStartScan: () -> Unit,
    onThemeChange: (String) -> Unit,
    onAccentChange: (String) -> Unit,
    onGridlinesChange: (String) -> Unit,
    onLaunch: () -> Unit,
    onUploadCustomBackground: (String) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val welcomeImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val localPath = copyUriToLocalFile(context, uri)
            if (localPath.isNotEmpty()) {
                onUploadCustomBackground(localPath)
                onGridlinesChange("upload")
            }
        }
    }

    var step by remember { mutableStateOf(0) }
    val isLight = when (settings.themeMode) {
        "light" -> true
        "dark" -> false
        "amoled" -> false
        else -> !androidx.compose.foundation.isSystemInDarkTheme()
    }
    val canvasBg = when (settings.themeMode) {
        "light" -> Color.White
        "amoled" -> Color.Black
        else -> Color(0xFF0C0D11)
    }
    val textPrimary = if (isLight) Color.Black else Color.White
    val textSecondary = if (isLight) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
    val accentColor = getThemeAccentColor(settings.accentColorHex)

    val isStoragePermissionGranted = hasPermission

    val onboardingContext = androidx.compose.ui.platform.LocalContext.current
    var isNotificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    onboardingContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isNotificationPermissionGranted = isGranted
    }

    var showPermissionRequestPopup by remember { mutableStateOf(false) }
    var scanStartedAtLeastOnce by remember { mutableStateOf(false) }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            scanStartedAtLeastOnce = true
        }
    }

    val scanFinished = scanStartedAtLeastOnce && !isScanning

    if (showPermissionRequestPopup) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPermissionRequestPopup = false },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRequestPopup = false
                        onRequestPermission()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                ) {
                    Text("GRANT ACCESS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showPermissionRequestPopup = false },
                    colors = ButtonDefaults.buttonColors(containerColor = textPrimary.copy(alpha = 0.1f), contentColor = textPrimary),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            title = {
                Text(
                    text = "Music Storage Permission",
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = getMetroFontFamily(settings.fontFamily)
                )
            },
            text = {
                Text(
                    text = "Azune Player requires permission to read your music library so that it can scan and play offline songs stored on this device.",
                    color = textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = canvasBg
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBg)
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .clickable(enabled = false) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(30.dp))

                // Global Top indicator of steps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(if (i <= step) accentColor else textPrimary.copy(alpha = 0.12f))
                        )
                    }
                }

                when (step) {
                    0 -> {
                        // STEP 0: GREETING & HELLO
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "WELCOME".uppercase(),
                                color = textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Azune Player",
                                color = accentColor,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-2).sp
                            )
                            Text(
                                text = "Clean, 100% Offline MP3 Music Player",
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Feature Highlights Outline
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(accentColor.copy(alpha = 0.06f))
                                    .border(1.dp, accentColor.copy(alpha = 0.2f), androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Palette, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("1. Personalize Aesthetics", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Themes, accents, and custom background gridlines", color = textSecondary, fontSize = 11.sp)
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("2. Permission and Music Sync", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Notification status bar controls & local music scan", color = textSecondary, fontSize = 11.sp)
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("3. Zero Data Collection", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("100% private, no trackers, no subscriptions, completely ad-free", color = textSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // STEP 1: CUSTOMIZATION OPTIONS
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "personalize".uppercase(),
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Set Your Aesthetic",
                                color = textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-0.5).sp
                            )
                            
                            // Theme mode selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "theme mode".uppercase(), color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf("dark" to "Dark mode", "amoled" to "Amoled dark", "light" to "Aero Light").forEach { (mode, label) ->
                                        val active = settings.themeMode == mode
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (active) accentColor else textPrimary.copy(alpha = 0.05f))
                                                .border(1.dp, if (active) Color.Transparent else textPrimary.copy(alpha = 0.15f))
                                                .clickable { onThemeChange(mode) }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label.lowercase(),
                                                color = if (active) Color.White else textPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Accent selection swatches
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "accent color highlight".uppercase(), color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                val swatches = listOf("#0078D7", "#107C41", "#5024B3", "#D83B01", "#E3008C")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    swatches.forEach { hex ->
                                        val active = settings.accentColorHex == hex
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(Color(android.graphics.Color.parseColor(hex)))
                                                .border(2.dp, if (active) textPrimary else Color.Transparent)
                                                .clickable { onAccentChange(hex) }
                                        )
                                    }
                                }
                            }

                            // App Background options
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "App Background".uppercase(), color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                val row1 = listOf(
                                    "solid" to "Solid",
                                    "grid" to "Tech lines",
                                    "retro-tiles" to "Blueprint"
                                )
                                val row2 = listOf(
                                    "constellation" to "Stars",
                                    "circuit" to "Circuit",
                                    "mesh" to "Mesh"
                                )
                                listOf(row1, row2).forEach { rowList ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        rowList.forEach { (style, label) ->
                                            val active = settings.backgroundStyle == style
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(if (active) accentColor else textPrimary.copy(alpha = 0.05f))
                                                    .border(1.dp, if (active) Color.Transparent else textPrimary.copy(alpha = 0.15f))
                                                    .clickable { onGridlinesChange(style) }
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label.lowercase(),
                                                    color = if (active) Color.White else textPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                    2 -> {
                        // STEP 2: PERMISSION AND MUSIC SYNC
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "PERMISSION AND MUSIC SYNC".uppercase(),
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Permissions & Music Scan",
                                color = textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Grant notification and storage permissions below to enable status bar controls and auto-catalog your offline tracks.",
                                color = textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Normal
                            )

                            // TOP BOX: Notification Permission Access Box
                            if (!isNotificationPermissionGranted) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(accentColor.copy(alpha = 0.08f))
                                        .border(1.dp, accentColor.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                                        .padding(12.dp)
                                        .clickable {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Notification Access (Click to allow)",
                                                color = accentColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = getMetroFontFamily(settings.fontFamily)
                                            )
                                        }
                                        Text(
                                            text = "Required to show status bar playback controls, album artwork, and song notification details.",
                                            color = textSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(accentColor.copy(alpha = 0.1f))
                                        .border(1.dp, accentColor)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor)
                                    Text(
                                        text = "Notification access active! Music controls will display in the status bar.",
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // BELOW BOX: Music Scan / Storage Access Box
                            if (!isStoragePermissionGranted) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(accentColor.copy(alpha = 0.08f))
                                        .border(1.dp, accentColor.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                                        .padding(12.dp)
                                        .clickable { showPermissionRequestPopup = true }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Access Music Folder (Click to allow)",
                                                color = accentColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = getMetroFontFamily(settings.fontFamily)
                                            )
                                        }
                                        Text(
                                            text = "Azune Player requires storage permissions in order to build your music library. Tap here to request context access.",
                                            color = textSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(accentColor.copy(alpha = 0.1f))
                                        .border(1.dp, accentColor)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor)
                                    Text(
                                        text = "Music folder access is active! Tap SCAN LOCAL MUSIC NOW below.",
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // SCAN BUTTON: Located below the music scan box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isStoragePermissionGranted) accentColor else accentColor.copy(alpha = 0.2f))
                                    .clickable(enabled = isStoragePermissionGranted) {
                                        onStartScan()
                                        scanStartedAtLeastOnce = true
                                    }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = if (isStoragePermissionGranted) Color.White else Color.White.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "SCAN LOCAL MUSIC NOW",
                                        color = if (isStoragePermissionGranted) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            if (isScanning) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = accentColor,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Scanning local directories for audio files...",
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (scanFinished && !isScanning) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(accentColor.copy(alpha = 0.1f))
                                        .border(1.dp, accentColor)
                                        .padding(12.dp)
                                        .padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor)
                                    Text(
                                        text = "Scan complete! Your offline library is loaded. Tap Next to continue.",
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    3 -> {
                        // STEP 3: WRAP UP / FINISH GREETINGS
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "done".uppercase(),
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "You're All Setup!",
                                color = accentColor,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "Everything is perfectly tuned and ready.",
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tap the launch button below to enter Azune Player. Slide panes left and right to navigate between your Music Hub, Playlists, and advanced settings.",
                                color = textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer controls containing Back / Next
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                if (step > 0) {
                    Button(
                        onClick = { step-- },
                        colors = ButtonDefaults.buttonColors(containerColor = textPrimary.copy(alpha = 0.05f), contentColor = textPrimary),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(text = "← Back", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Next / Launch Button
                val canProceed = when (step) {
                    2 -> isStoragePermissionGranted && scanFinished
                    else -> true
                }
                Button(
                    onClick = {
                        if (step < 3) {
                            step++
                        } else {
                            onLaunch()
                        }
                    },
                    enabled = canProceed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White,
                        disabledContainerColor = accentColor.copy(alpha = 0.2f),
                        disabledContentColor = textPrimary.copy(alpha = 0.4f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = if (step == 3) "LAUNCH APP →" else "Next →",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

