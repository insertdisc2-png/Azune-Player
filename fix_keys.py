with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# For multiple arguments passing correctly without vararg mapping issues:
# vararg syntax in produceState is `produceState(initialValue, key1, key2)` etc.
# Wait, produceState only has 3 keys. For more, it's `produceState(initialValue, *arrayOf(keys))`
# But actually, `val x by remember(keys)` with `LaunchedEffect(keys)` is way safer and doesn't suffer from produceState overloaded signature madness.

# Let's revert produceState to `var x by remember { mutableStateOf(...) }` + `LaunchedEffect(...)`

def replace_with_effect(content, old_produce, var_name, init_val, keys, calc_block):
    # This is a bit tricky, let's just do it directly.
    return content

# 1. MainTilesHub
old_fav = """    val favoritesCount by androidx.compose.runtime.produceState(initialValue = 0, allSongs, settings.favoriteTrackIds) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val favIds = settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
            value = allSongs.count { it.id in favIds }
        }
    }"""
new_fav = """    var favoritesCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(allSongs, settings.favoriteTrackIds) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val favIds = settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
            favoritesCount = allSongs.count { it.id in favIds }
        }
    }"""
content = content.replace(old_fav, new_fav)

# 2. dynamicGenres
old_genres = """    val dynamicGenres by androidx.compose.runtime.produceState(initialValue = emptyList<String>(), allSongs) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            value = allSongs
                .map { it.genre.trim() }
                .filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Ambient Synth", ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .sorted()
        }
    }"""
new_genres = """    var dynamicGenres by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList<String>()) }
    androidx.compose.runtime.LaunchedEffect(allSongs) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            dynamicGenres = allSongs
                .map { it.genre.trim() }
                .filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Ambient Synth", ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .sorted()
        }
    }"""
content = content.replace(old_genres, new_genres)

# 3. sortedTracks
old_sorted_tracks = """    val sortedTracks by androidx.compose.runtime.produceState(initialValue = emptyList<Track>(), allSongs, query, category, sortBy, settings.favoriteTrackIds, isAscending) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val favIds = settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
            val cleanQuery = query.trim()
            val hasQuery = cleanQuery.isNotEmpty()
            val filtered = if (!hasQuery && category == "all") {
                allSongs
            } else {
                allSongs.filter { track ->
                    val matchesQuery = !hasQuery ||
                            track.title.contains(cleanQuery, ignoreCase = true) ||
                            track.artist.contains(cleanQuery, ignoreCase = true)
                    val matchesCat = when {
                        category == "favorites" -> track.id in favIds
                        category == "all" -> true
                        category.startsWith("genre_") -> {
                            val filterGenre = category.removePrefix("genre_")
                            track.genre.trim().equals(filterGenre, ignoreCase = true)
                        }
                        else -> true
                    }
                    matchesQuery && matchesCat
                }
            }

            val comparator: Comparator<Track> = when (sortBy) {
                "A-Z" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                "Z-A" -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
                "Artists" -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                "Album" -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album.ifEmpty { "unknown album" } }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                "Date Added" -> compareBy { it.id }
                else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }

            val finalSorted = filtered.sortedWith(comparator)
            value = if (!isAscending) finalSorted.reversed() else finalSorted
        }
    }"""
new_sorted_tracks = """    var sortedTracks by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList<Track>()) }
    androidx.compose.runtime.LaunchedEffect(allSongs, query, category, sortBy, settings.favoriteTrackIds, isAscending) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val favIds = settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
            val cleanQuery = query.trim()
            val hasQuery = cleanQuery.isNotEmpty()
            val filtered = if (!hasQuery && category == "all") {
                allSongs
            } else {
                allSongs.filter { track ->
                    val matchesQuery = !hasQuery ||
                            track.title.contains(cleanQuery, ignoreCase = true) ||
                            track.artist.contains(cleanQuery, ignoreCase = true)
                    val matchesCat = when {
                        category == "favorites" -> track.id in favIds
                        category == "all" -> true
                        category.startsWith("genre_") -> {
                            val filterGenre = category.removePrefix("genre_")
                            track.genre.trim().equals(filterGenre, ignoreCase = true)
                        }
                        else -> true
                    }
                    matchesQuery && matchesCat
                }
            }

            val comparator: Comparator<Track> = when (sortBy) {
                "A-Z" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                "Z-A" -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
                "Artists" -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                "Album" -> compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album.ifEmpty { "unknown album" } }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                "Date Added" -> compareBy { it.id }
                else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            }

            val finalSorted = filtered.sortedWith(comparator)
            sortedTracks = if (!isAscending) finalSorted.reversed() else finalSorted
        }
    }"""
content = content.replace(old_sorted_tracks, new_sorted_tracks)

# 4. groupedByArtist
old_artist = """    val groupedByArtist by androidx.compose.runtime.produceState(initialValue = emptyMap<String, List<Track>>(), allSongs, settings.artistSeparators, query) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val separators = settings.artistSeparators.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("/", ",", "feat.", "ft.", "&", "and") }

            val regexPattern = Regex("(?i)(" + separators.joinToString("|") { Regex.escape(it) } + ")")
            val map = java.util.TreeMap<String, MutableList<Track>>(String.CASE_INSENSITIVE_ORDER)
            
            allSongs.forEach { song ->
                val individualArtists = song.artist.split(regexPattern)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val finalArtists = if (individualArtists.isEmpty()) listOf(song.artist) else individualArtists
                
                finalArtists.forEach { artist ->
                    if (artist.isNotEmpty()) {
                        map.getOrPut(artist) { mutableListOf() }.add(song)
                    }
                }
            }
            
            value = if (query.isNotBlank()) {
                val filteredMap = java.util.TreeMap<String, MutableList<Track>>(String.CASE_INSENSITIVE_ORDER)
                map.forEach { (artist, tracks) ->
                    if (artist.contains(query, ignoreCase = true)) {
                        filteredMap[artist] = tracks
                    } else {
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
        }
    }"""
new_artist = """    var groupedByArtist by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyMap<String, List<Track>>()) }
    androidx.compose.runtime.LaunchedEffect(allSongs, settings.artistSeparators, query) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val separators = settings.artistSeparators.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("/", ",", "feat.", "ft.", "&", "and") }

            val regexPattern = Regex("(?i)(" + separators.joinToString("|") { Regex.escape(it) } + ")")
            val map = java.util.TreeMap<String, MutableList<Track>>(String.CASE_INSENSITIVE_ORDER)
            
            allSongs.forEach { song ->
                val individualArtists = song.artist.split(regexPattern)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val finalArtists = if (individualArtists.isEmpty()) listOf(song.artist) else individualArtists
                
                finalArtists.forEach { artist ->
                    if (artist.isNotEmpty()) {
                        map.getOrPut(artist) { mutableListOf() }.add(song)
                    }
                }
            }
            
            groupedByArtist = if (query.isNotBlank()) {
                val filteredMap = java.util.TreeMap<String, MutableList<Track>>(String.CASE_INSENSITIVE_ORDER)
                map.forEach { (artist, tracks) ->
                    if (artist.contains(query, ignoreCase = true)) {
                        filteredMap[artist] = tracks
                    } else {
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
        }
    }"""
content = content.replace(old_artist, new_artist)

# 5. groupedByAlbum
old_album = """    val groupedByAlbum by androidx.compose.runtime.produceState(initialValue = emptyMap<String, List<Track>>(), allSongs) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            value = allSongs.groupBy { it.album.ifEmpty { "Unknown Album" } }.toSortedMap()
        }
    }"""
new_album = """    var groupedByAlbum by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyMap<String, List<Track>>()) }
    androidx.compose.runtime.LaunchedEffect(allSongs) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            groupedByAlbum = allSongs.groupBy { it.album.ifEmpty { "Unknown Album" } }.toSortedMap()
        }
    }"""
content = content.replace(old_album, new_album)


with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
