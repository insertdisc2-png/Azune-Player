with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

old_key = "produceState(initialValue = emptyList<Track>(), key1 = allSongs, key2 = query, key3 = category, key4 = sortBy, key5 = settings.favoriteTrackIds)"
new_key = "produceState(initialValue = emptyList<Track>(), key1 = allSongs, key2 = query, key3 = category, key4 = sortBy, key5 = settings.favoriteTrackIds, key6 = isAscending)"

content = content.replace(old_key, new_key)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
