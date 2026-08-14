with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# Add searchArtistQuery
content = content.replace("var searchMusicQuery by remember { mutableStateOf(\"\") }", "var searchMusicQuery by remember { mutableStateOf(\"\") }\n    var searchArtistQuery by remember { mutableStateOf(\"\") }")

# Add query to ArtistsPanel call
old_call = """                            3 -> {
                                // Pivot ARTISTS (Alphabetical artist grouping list)
                                ArtistsPanel(
                                    allSongs = allSongs,
                                    settings = settings,
                                    onTrackPlay = { track, customQueue ->
                                        viewModel.playTrack(track, customQueue)
                                    }
                                )
                            }"""

new_call = """                            3 -> {
                                // Pivot ARTISTS (Alphabetical artist grouping list)
                                ArtistsPanel(
                                    allSongs = allSongs,
                                    settings = settings,
                                    query = searchArtistQuery,
                                    onQueryChange = { searchArtistQuery = it },
                                    onTrackPlay = { track, customQueue ->
                                        viewModel.playTrack(track, customQueue)
                                    }
                                )
                            }"""

content = content.replace(old_call, new_call)

# Now update ArtistsPanel signature and implementation
old_sig = """fun ArtistsPanel(
    allSongs: List<Track>,
    settings: com.example.data.database.UserSettingsEntity,
    onTrackPlay: (Track, List<Track>) -> Unit
) {"""

new_sig = """fun ArtistsPanel(
    allSongs: List<Track>,
    settings: com.example.data.database.UserSettingsEntity,
    query: String,
    onQueryChange: (String) -> Unit,
    onTrackPlay: (Track, List<Track>) -> Unit
) {"""

content = content.replace(old_sig, new_sig)

old_grouped = """    val groupedByArtist = remember(allSongs, settings.artistSeparators) {"""

new_grouped = """    val groupedByArtist = remember(allSongs, settings.artistSeparators, query) {"""

content = content.replace(old_grouped, new_grouped)

# Add filter to map
old_map = """        }
        map
    }"""

new_map = """        }
        
        if (query.isNotBlank()) {
            val filteredMap = java.util.TreeMap<String, MutableList<Track>>(String.CASE_INSENSITIVE_ORDER)
            map.forEach { (artist, tracks) ->
                if (artist.contains(query, ignoreCase = true)) {
                    filteredMap[artist] = tracks
                } else {
                    // Filter tracks whose title matches
                    val matchingTracks = tracks.filter { it.title.contains(query, ignoreCase = true) }
                    if (matchingTracks.isNotEmpty()) {
                        filteredMap[artist] = matchingTracks.toMutableList()
                    }
                }
            }
            filteredMap
        } else {
            map
        }
    }"""

content = content.replace(old_map, new_map)

# Add search bar to UI
old_ui = """    if (groupedByArtist.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {"""

new_ui = """    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { 
                Text(
                    text = "search artists...", 
                    fontFamily = getMetroFontFamily(settings.fontFamily),
                    color = textSecondaryColor 
                ) 
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = themeAccentColor,
                unfocusedIndicatorColor = textSecondaryColor.copy(alpha = 0.5f),
                cursorColor = themeAccentColor
            ),
            singleLine = true
        )
        
        if (groupedByArtist.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {"""

content = content.replace(old_ui, new_ui)

# Fix the else block wrapping
old_else = """            )
        }
    } else {
        LazyColumn("""

new_else = """            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f),"""

content = content.replace(old_else, new_else)

# Need to close Column
old_lazy_col_end = """                            .background(themeAccentColor)
                        )
                    }
                }
            }
        }
    }
}

// =========================================="""

new_lazy_col_end = """                            .background(themeAccentColor)
                        )
                    }
                }
            }
        }
    }
    }
}

// =========================================="""

content = content.replace(old_lazy_col_end, new_lazy_col_end)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
