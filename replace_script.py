import re

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# Replace Tab onClick logic
old_tab = """                            val isActive = activePivotIndex == index && selectedPlaylistIdDetail == null
                            Tab(
                                selected = isActive,
                                onClick = {
                                    selectedPlaylistIdDetail = null
                                    activePivotIndex = index
                                },"""

new_tab = """                            val isActive = activePivotIndex == index && selectedPlaylistIdDetail == null
                            Tab(
                                selected = isActive,
                                onClick = {
                                    selectedPlaylistIdDetail = null
                                    activePivotIndex = index
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },"""

content = content.replace(old_tab, new_tab)

# Replace AnimatedContent block
old_anim_start = """                Box(modifier = Modifier.weight(1f)) {
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
                        when (index) {"""

new_anim_start = """                Box(modifier = Modifier.weight(1f)) {
                    // Slide transition animation for panels
                    AnimatedContent(
                        targetState = selectedPlaylistIdDetail != null,
                        transitionSpec = {
                            if (targetState) {
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
                    ) { isDetail ->
                        if (isDetail) {
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
                                    onAddToPlaylistTrigger = { track ->
                                        showAddTrackToPlaylistDialog = track
                                    }
                                )
                            }
                        } else {
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.Top
                            ) { page ->
                                when (page) {"""

content = content.replace(old_anim_start, new_anim_start)

# Replace the 99 -> block and closing
old_anim_end = """                            99 -> {
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
                                        onAddToPlaylistTrigger = { track ->
                                            showAddTrackToPlaylistDialog = track
                                        }
                                    )
                                }
                            }
                        }
                    }
                }"""

new_anim_end = """                            }
                            }
                        }
                    }
                }"""

content = content.replace(old_anim_end, new_anim_end)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
