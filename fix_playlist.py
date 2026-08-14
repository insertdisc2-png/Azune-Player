with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

bad_text = """                                    onToggleFavorite = { trackId ->
                                        viewModel.toggleFavorite(trackId)
                                    },
                                    isFavorite = { trackId ->
                                        favTrackIdsSet.contains(trackId)
                                    },
                                    onAddToPlaylistTrigger = { track ->
                                        showAddTrackToPlaylistDialog = track
                                    }
                                )"""

good_text = """                                    onToggleFavorite = { trackId ->
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
                                )"""

content = content.replace(bad_text, good_text)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
