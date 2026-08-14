with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# I can just remove named parameters and just pass them positionally since vararg or overloaded functions handle it.
old_key_fav = "androidx.compose.runtime.produceState(initialValue = 0, key1 = allSongs, key2 = settings.favoriteTrackIds)"
new_key_fav = "androidx.compose.runtime.produceState(initialValue = 0, allSongs, settings.favoriteTrackIds)"
content = content.replace(old_key_fav, new_key_fav)

old_key_genres = "androidx.compose.runtime.produceState(initialValue = emptyList<String>(), key1 = allSongs)"
new_key_genres = "androidx.compose.runtime.produceState(initialValue = emptyList<String>(), allSongs)"
content = content.replace(old_key_genres, new_key_genres)

old_key_tracks = "androidx.compose.runtime.produceState(initialValue = emptyList<Track>(), key1 = allSongs, key2 = query, key3 = category, key4 = sortBy, key5 = settings.favoriteTrackIds, key6 = isAscending)"
new_key_tracks = "androidx.compose.runtime.produceState(initialValue = emptyList<Track>(), allSongs, query, category, sortBy, settings.favoriteTrackIds, isAscending)"
content = content.replace(old_key_tracks, new_key_tracks)

old_key_artist = "androidx.compose.runtime.produceState(initialValue = emptyMap<String, List<Track>>(), key1 = allSongs, key2 = settings.artistSeparators, key3 = query)"
new_key_artist = "androidx.compose.runtime.produceState(initialValue = emptyMap<String, List<Track>>(), allSongs, settings.artistSeparators, query)"
content = content.replace(old_key_artist, new_key_artist)

old_key_album = "androidx.compose.runtime.produceState(initialValue = emptyMap<String, List<Track>>(), key1 = allSongs)"
new_key_album = "androidx.compose.runtime.produceState(initialValue = emptyMap<String, List<Track>>(), allSongs)"
content = content.replace(old_key_album, new_key_album)


with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
