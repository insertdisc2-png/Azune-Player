import re

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# 1. MainTilesHub favoritesCount
old_fav_count = """    val favoritesCount = remember(allSongs, settings.favoriteTrackIds) {
        val favIds = settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
        allSongs.count { it.id in favIds }
    }"""
new_fav_count = """    val favoritesCount by androidx.compose.runtime.produceState(initialValue = 0, key1 = allSongs, key2 = settings.favoriteTrackIds) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val favIds = settings.favoriteTrackIds.split(",").filter { it.isNotEmpty() }.toSet()
            value = allSongs.count { it.id in favIds }
        }
    }"""
content = content.replace(old_fav_count, new_fav_count)

# 2. dynamicGenres
old_genres = """    val dynamicGenres = androidx.compose.runtime.remember(allSongs) {
        allSongs
            .map { it.genre.trim() }
            .filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Ambient Synth", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .sorted()
    }"""
new_genres = """    val dynamicGenres by androidx.compose.runtime.produceState(initialValue = emptyList<String>(), key1 = allSongs) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            value = allSongs
                .map { it.genre.trim() }
                .filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Ambient Synth", ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .sorted()
        }
    }"""
content = content.replace(old_genres, new_genres)

# 3. sortedTracks
old_sorted_tracks = """    val sortedTracks = androidx.compose.runtime.remember(allSongs, query, category, sortBy, isAscending, settings.favoriteTrackIds) {
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
        if (!isAscending) finalSorted.reversed() else finalSorted
    }"""
new_sorted_tracks = """    val sortedTracks by androidx.compose.runtime.produceState(initialValue = emptyList<Track>(), key1 = allSongs, key2 = query, key3 = category, key4 = sortBy, key5 = settings.favoriteTrackIds) {
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
content = content.replace(old_sorted_tracks, new_sorted_tracks)

# 4. groupedByArtist
old_artist = """    val groupedByArtist = remember(allSongs, settings.artistSeparators, query) {
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
new_artist = """    val groupedByArtist by androidx.compose.runtime.produceState(initialValue = emptyMap<String, List<Track>>(), key1 = allSongs, key2 = settings.artistSeparators, key3 = query) {
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
content = content.replace(old_artist, new_artist)

# 5. groupedByAlbum
old_album = """    val groupedByAlbum = remember(allSongs) {
        allSongs.groupBy { it.album.ifEmpty { "Unknown Album" } }.toSortedMap()
    }"""
new_album = """    val groupedByAlbum by androidx.compose.runtime.produceState(initialValue = emptyMap<String, List<Track>>(), key1 = allSongs) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            value = allSongs.groupBy { it.album.ifEmpty { "Unknown Album" } }.toSortedMap()
        }
    }"""
content = content.replace(old_album, new_album)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
