with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

bad_text = """                            99 -> {
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
                }"""

good_text = """                        }
                    }
                }
                }
                }"""

content = content.replace(bad_text, good_text)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
