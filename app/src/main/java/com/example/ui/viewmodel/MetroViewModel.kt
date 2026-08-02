package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.database.MetroDatabase
import com.example.data.database.UserSettingsEntity
import com.example.data.model.Track
import com.example.data.player.MetroPlayerEngine
import com.example.data.repository.MetroRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MetroViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database: MetroDatabase by lazy {
        Room.databaseBuilder(
            application.applicationContext,
            MetroDatabase::class.java,
            "metro_music_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    private val repository: MetroRepository by lazy {
        MetroRepository(application.applicationContext, database)
    }

    private val playerEngine: MetroPlayerEngine by lazy {
        MetroPlayerEngine(application.applicationContext)
    }

    // Settings State
    val userSettings = repository.userSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettingsEntity()
    )

    // Track if database settings have been loaded at least once
    val isSettingsLoaded = repository.userSettingsFlow
        .map { true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    // Playlists List State
    val playlists = repository.playlistsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Music lists
    private val _localTracksList = MutableStateFlow<List<Track>>(emptyList())
    val localTracksList: StateFlow<List<Track>> = _localTracksList.asStateFlow()

    // Combined all available tracks (Local only)
    val allAvailableTracks = _localTracksList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current playlist selected tracks
    private val _currentPlaylistId = MutableStateFlow<Int?>(null)
    val currentPlaylistId: StateFlow<Int?> = _currentPlaylistId.asStateFlow()

    val currentPlaylistTracks = _currentPlaylistId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            repository.getPlaylistTracksFlow(id)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Player State Variables
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlayingState = MutableStateFlow(false)
    val isPlayingState: StateFlow<Boolean> = _isPlayingState.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    // Speed and Pitch control variables
    private val _playbackSpeedVal = MutableStateFlow(1.0f)
    val playbackSpeedVal: StateFlow<Float> = _playbackSpeedVal.asStateFlow()

    private val _playbackPitchVal = MutableStateFlow(1.0f)
    val playbackPitchVal: StateFlow<Float> = _playbackPitchVal.asStateFlow()

    // Custom inline edited lyrics state dictionary
    private val _customLyricsMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val customLyricsMap: StateFlow<Map<String, String>> = _customLyricsMap.asStateFlow()

    // Playlist/Track Queue
    private var playbackQueue = listOf<Track>()
    private val _playbackQueueState = MutableStateFlow<List<Track>>(emptyList())
    val playbackQueueState: StateFlow<List<Track>> = _playbackQueueState.asStateFlow()

    private var currentQueueIndex = -1
    private var isShuffle = false
    private var isRepeat = false

    private val _isShuffleState = MutableStateFlow(false)
    val isShuffleState: StateFlow<Boolean> = _isShuffleState.asStateFlow()

    private val _isRepeatState = MutableStateFlow(false)
    val isRepeatState: StateFlow<Boolean> = _isRepeatState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        // Observe and load on settings updates (only when music folder or placeholder settings actually change)
        viewModelScope.launch {
            repository.userSettingsFlow
                .map { Pair(it.targetMusicFolder, it.showPlaceholderSongs) }
                .distinctUntilChanged()
                .collect { (folder, _) ->
                    loadLocalLibraryWithFolder(folder)
                }
        }

        // Set Completion Listener to auto-play next tracks
        playerEngine.setOnCompletionListener {
            if (isRepeat) {
                _currentTrack.value?.let { playTrack(it, playbackQueue) }
            } else {
                playNextTrack()
            }
        }

        // Observe current track and playback state to update music notification on status bar
        viewModelScope.launch {
            combine(_currentTrack, _isPlayingState) { track, isPlaying ->
                Pair(track, isPlaying)
            }.collect { (track, isPlaying) ->
                com.example.service.MusicNotificationService.updateNotification(
                    application.applicationContext,
                    track,
                    isPlaying,
                    _playbackPosition.value
                )
            }
        }

        // Handle user control actions from MusicNotificationService
        com.example.service.MusicNotificationService.onNotificationAction = { action ->
            when (action) {
                com.example.service.MusicNotificationService.ACTION_PLAY,
                com.example.service.MusicNotificationService.ACTION_PAUSE -> togglePlayPause()
                com.example.service.MusicNotificationService.ACTION_NEXT -> playNextTrack()
                com.example.service.MusicNotificationService.ACTION_PREVIOUS -> playPreviousTrack()
                com.example.service.MusicNotificationService.ACTION_STOP -> stopPlayback()
            }
        }
    }

    fun loadLocalLibrary() {
        loadLocalLibraryWithFolder(userSettings.value.targetMusicFolder)
    }

    fun loadLocalLibraryWithFolder(folder: String) {
        viewModelScope.launch {
            val tracks = repository.queryLocalMusic(folder).toMutableList()
            if (userSettings.value.showPlaceholderSongs) {
                tracks.addAll(0, repository.defaultSynthTracks)
            }
            _localTracksList.value = tracks

            if (_currentTrack.value == null && userSettings.value.rememberLastPlayed && userSettings.value.lastPlayedTrackId.isNotEmpty()) {
                val restoredTrack = tracks.find { it.id == userSettings.value.lastPlayedTrackId }
                if (restoredTrack != null) {
                    _currentTrack.value = restoredTrack
                    _playbackDuration.value = restoredTrack.durationMs
                    _playbackPosition.value = userSettings.value.lastPlayedPositionMs
                    playerEngine.preload(restoredTrack)
                    playbackQueue = tracks
                    _playbackQueueState.value = playbackQueue
                    currentQueueIndex = playbackQueue.indexOfFirst { it.id == restoredTrack.id }
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(themeMode = mode)
            repository.saveSettings(updated)
        }
    }

    fun setTargetMusicFolder(folder: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(targetMusicFolder = folder)
            repository.saveSettings(updated)
        }
    }

    fun updatePlaybackParams(speed: Float, pitch: Float) {
        _playbackSpeedVal.value = speed
        _playbackPitchVal.value = pitch
        playerEngine.setPlaybackSpeedAndPitch(speed, pitch)
    }

    fun updateLyricsForTrack(trackId: String, newLyricsText: String) {
        val currentMap = _customLyricsMap.value.toMutableMap()
        currentMap[trackId] = newLyricsText
        _customLyricsMap.value = currentMap
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            if (track.isSynth) return@launch
            
            // Remove from state list instantly
            _localTracksList.value = _localTracksList.value.filter { it.id != track.id }
            playbackQueue = playbackQueue.filter { it.id != track.id }
            _playbackQueueState.value = playbackQueue
            
            if (_currentTrack.value?.id == track.id) {
                if (playbackQueue.isNotEmpty()) {
                    playNextTrack()
                } else {
                    _currentTrack.value = null
                    _isPlayingState.value = false
                    playerEngine.stop()
                }
            }

            try {
                val file = java.io.File(track.path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Playback controls
    fun playTrack(track: Track, customQueue: List<Track> = emptyList()) {
        _playbackPosition.value = 0L
        _playbackDuration.value = track.durationMs
        _isPlayingState.value = true
        _currentTrack.value = track
        
        // Define Queue
        if (customQueue.isNotEmpty()) {
            playbackQueue = customQueue
        } else {
            playbackQueue = allAvailableTracks.value
        }
        _playbackQueueState.value = playbackQueue
        currentQueueIndex = playbackQueue.indexOfFirst { it.id == track.id }
        playerEngine.play(track)

        // Reapply current speed and pitch if configured
        playerEngine.setPlaybackSpeedAndPitch(_playbackSpeedVal.value, _playbackPitchVal.value)

        startPositionTicker()

        saveLastPlayedTrack(track.id, 0L)
    }

    fun togglePlayPause() {
        val track = _currentTrack.value ?: return
        if (playerEngine.isPlaying()) {
            playerEngine.pause()
            _isPlayingState.value = false
        } else {
            if (!playerEngine.isPrepared()) {
                val lastPos = _playbackPosition.value
                playerEngine.play(track)
                if (lastPos > 0L) {
                    playerEngine.seekTo(lastPos)
                }
                _isPlayingState.value = true
                playerEngine.setPlaybackSpeedAndPitch(_playbackSpeedVal.value, _playbackPitchVal.value)
                startPositionTicker()
            } else {
                playerEngine.resume()
                _isPlayingState.value = true
            }
        }
    }

    fun stopPlayback() {
        _currentTrack.value = null
        _isPlayingState.value = false
        playerEngine.stop()
        com.example.service.MusicNotificationService.stopNotification(getApplication())
    }

    fun playNextTrack() {
        if (playbackQueue.isEmpty()) return
        
        if (isShuffle) {
            currentQueueIndex = (0 until playbackQueue.size).random()
        } else {
            currentQueueIndex = (currentQueueIndex + 1) % playbackQueue.size
        }

        val nextTrack = playbackQueue.getOrNull(currentQueueIndex) ?: return
        playTrack(nextTrack, playbackQueue)
    }

    fun playPreviousTrack() {
        if (playbackQueue.isEmpty()) return

        currentQueueIndex = if (currentQueueIndex - 1 < 0) {
            playbackQueue.size - 1
        } else {
            currentQueueIndex - 1
        }

        val prevTrack = playbackQueue.getOrNull(currentQueueIndex) ?: return
        playTrack(prevTrack, playbackQueue)
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        _isShuffleState.value = isShuffle
    }

    fun toggleRepeat() {
        isRepeat = !isRepeat
        _isRepeatState.value = isRepeat
    }

    fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
        _playbackPosition.value = positionMs
        _currentTrack.value?.let { track ->
            saveLastPlayedTrack(track.id, positionMs)
            com.example.service.MusicNotificationService.updateNotification(
                getApplication(),
                track,
                _isPlayingState.value,
                positionMs
            )
        }
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var saveCounter = 0
            while (true) {
                if (playerEngine.isPlaying()) {
                    val pos = playerEngine.getCurrentPosition()
                    _playbackPosition.value = pos
                    _playbackDuration.value = playerEngine.getDuration()

                    saveCounter++
                    if (saveCounter >= 40) { // every ~5 seconds (40 * 120 ms)
                        saveCounter = 0
                        _currentTrack.value?.let { track ->
                            saveLastPlayedTrack(track.id, pos)
                        }
                    }
                }
                delay(120)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        playerEngine.stop()
    }

    // Layout configuration customizers
    fun setAccentColor(hexColor: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(accentColorHex = hexColor)
            repository.saveSettings(updated)
        }
    }

    fun setFontFamily(fontFamily: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(fontFamily = fontFamily)
            repository.saveSettings(updated)
        }
    }

    fun setBackgroundTransparency(alpha: Float) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(backgroundTransparency = alpha)
            repository.saveSettings(updated)
        }
    }

    fun setBackgroundStyle(style: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(backgroundStyle = style)
            repository.saveSettings(updated)
        }
    }

    fun setWelcomeCompleted(completed: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(welcomeCompleted = completed)
            repository.saveSettings(updated)
        }
    }

    fun setCornerCoverArt(style: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(cornerCoverArt = style)
            repository.saveSettings(updated)
        }
    }

    fun setCoverBackgroundStyle(style: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(coverBackgroundStyle = style)
            repository.saveSettings(updated)
        }
    }

    fun setAppBackgroundSettings(image: String, blur: Float, opacity: Float) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(
                appBackgroundImage = image,
                appBackgroundBlur = blur,
                appBackgroundOpacity = opacity
            )
            repository.saveSettings(updated)
        }
    }

    // Playlists managers
    fun createPlaylist(name: String, desc: String, accentHex: String = "#0078D7") {
        viewModelScope.launch {
            repository.createPlaylist(name, desc, accentHex)
        }
    }

    fun deletePlaylist(id: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
            if (_currentPlaylistId.value == id) {
                _currentPlaylistId.value = null
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Int, track: Track) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Int, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun selectPlaylist(playlistId: Int?) {
        _currentPlaylistId.value = playlistId
    }

    // Toggle Favorite
    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            val currentFavs = userSettings.value.favoriteTrackIds
                .split(",")
                .filter { it.isNotEmpty() }
                .toMutableList()

            if (currentFavs.contains(trackId)) {
                currentFavs.remove(trackId)
            } else {
                currentFavs.add(trackId)
            }

            val updatedFavs = currentFavs.joinToString(",")
            val updatedSettings = userSettings.value.copy(favoriteTrackIds = updatedFavs)
            repository.saveSettings(updatedSettings)
        }
    }

    fun isTrackFavorite(trackId: String): Boolean {
        val list = userSettings.value.favoriteTrackIds.split(",").filter { it.isNotEmpty() }
        return list.contains(trackId)
    }

    // Scanning state variables
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _scanStatusMessage = MutableStateFlow("")
    val scanStatusMessage: StateFlow<String> = _scanStatusMessage.asStateFlow()

    private val _scanCompletedEvent = MutableSharedFlow<Boolean>()
    val scanCompletedEvent: SharedFlow<Boolean> = _scanCompletedEvent.asSharedFlow()

    fun startLibraryScan(folder: String) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = 0.05f
            _scanStatusMessage.value = "initiate scan engine..."
            delay(400)
            
            _scanProgress.value = 0.2f
            _scanStatusMessage.value = "accessing target media folder: $folder..."
            delay(500)
            
            _scanProgress.value = 0.5f
            _scanStatusMessage.value = "indexing local audio files..."
            delay(600)
            
            val tracks = repository.queryLocalMusic(folder).toMutableList()
            if (userSettings.value.showPlaceholderSongs) {
                tracks.addAll(0, repository.defaultSynthTracks)
            }
            _scanProgress.value = 0.8f
            _scanStatusMessage.value = "found ${tracks.size} tracks, caching index database..."
            delay(500)
            
            _localTracksList.value = tracks
            _scanProgress.value = 1.0f
            _scanStatusMessage.value = "scan completed successfully! indexed ${tracks.size} tracks."
            delay(400)
            
            _isScanning.value = false
            _scanCompletedEvent.emit(true)
        }
    }

    fun setShowPlaceholderSongs(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(showPlaceholderSongs = enabled)
            repository.saveSettings(updated)
            loadLocalLibraryWithFolder(updated.targetMusicFolder)
        }
    }

    // User settings modifications for lyrics
    fun setPreviewLyrics(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(previewLyrics = enabled)
            repository.saveSettings(updated)
        }
    }

    fun setLyricsFontFamily(font: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(lyricsFontFamily = font)
            repository.saveSettings(updated)
        }
    }

    fun setLyricsSpacing(spacing: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(lyricsSpacing = spacing)
            repository.saveSettings(updated)
        }
    }

    fun setLyricsAlignment(align: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(lyricsAlignment = align)
            repository.saveSettings(updated)
        }
    }

    fun setLyricsFontSize(size: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(lyricsFontSize = size)
            repository.saveSettings(updated)
        }
    }

    fun setPlayerBackgroundStyle(style: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(playerBackgroundStyle = style)
            repository.saveSettings(updated)
        }
    }

    fun setPlayerBackgroundIntensity(intensity: Float) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(playerBackgroundIntensity = intensity)
            repository.saveSettings(updated)
        }
    }

    fun togglePinnedTrack(trackId: String) {
        viewModelScope.launch {
            val currentPinned = userSettings.value.pinnedTrackIds.split(",").filter { it.isNotEmpty() }.toMutableList()
            if (currentPinned.contains(trackId)) {
                currentPinned.remove(trackId)
            } else {
                currentPinned.add(trackId)
            }
            val updated = userSettings.value.copy(pinnedTrackIds = currentPinned.joinToString(","))
            repository.saveSettings(updated)
        }
    }

    fun isTrackPinned(trackId: String): Boolean {
        return userSettings.value.pinnedTrackIds.split(",").filter { it.isNotEmpty() }.contains(trackId)
    }

    fun setEnableCoverArt(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(enableCoverArt = enabled)
            repository.saveSettings(updated)
        }
    }

    fun setCoverArtResolution(resolution: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(coverArtResolution = resolution)
            repository.saveSettings(updated)
        }
    }

    fun setEnableListCoverArt(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(enableListCoverArt = enabled)
            repository.saveSettings(updated)
        }
    }

    fun setNowPlayingTileStyle(style: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(nowPlayingTileStyle = style)
            repository.saveSettings(updated)
        }
    }

    fun setRememberLastPlayed(enabled: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(rememberLastPlayed = enabled)
            repository.saveSettings(updated)
        }
    }

    fun saveLastPlayedTrack(trackId: String, positionMs: Long) {
        viewModelScope.launch {
            if (userSettings.value.rememberLastPlayed) {
                val updated = userSettings.value.copy(
                    lastPlayedTrackId = trackId,
                    lastPlayedPositionMs = positionMs
                )
                repository.saveSettings(updated)
            }
        }
    }

    fun saveTileOrder(order: List<String>) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(tileOrder = order.joinToString(","))
            repository.saveSettings(updated)
        }
    }

    fun saveTileSpans(spans: Map<String, Int>) {
        viewModelScope.launch {
            val spansStr = spans.map { "${it.key}:${it.value}" }.joinToString(",")
            val updated = userSettings.value.copy(tileSpans = spansStr)
            repository.saveSettings(updated)
        }
    }

    fun saveMusicSort(sortBy: String, ascending: Boolean) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(musicSortBy = sortBy, musicSortAscending = ascending)
            repository.saveSettings(updated)

            if (playbackQueue.isNotEmpty()) {
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
                var sorted = playbackQueue.sortedWith(comparator)
                if (!ascending) {
                    sorted = sorted.reversed()
                }
                playbackQueue = sorted
                _playbackQueueState.value = sorted

                _currentTrack.value?.let { current ->
                    currentQueueIndex = playbackQueue.indexOfFirst { it.id == current.id }
                }
            }
        }
    }

    fun setVisibleTiles(value: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(visibleTiles = value)
            repository.saveSettings(updated)
        }
    }

    fun setCoverArtBorderThickness(value: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(coverArtBorderThickness = value)
            repository.saveSettings(updated)
        }
    }

    fun setArtistSeparators(value: String) {
        viewModelScope.launch {
            val updated = userSettings.value.copy(artistSeparators = value)
            repository.saveSettings(updated)
        }
    }
}
