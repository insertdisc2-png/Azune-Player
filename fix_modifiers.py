with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# Fix ArtistsPanel
old_artists = """        LazyColumn(
            modifier = Modifier.weight(1f),
            modifier = Modifier.fillMaxSize(),"""

new_artists = """        LazyColumn(
            modifier = Modifier.weight(1f),"""

content = content.replace(old_artists, new_artists, 1)

# Fix AlbumsPanel
old_albums = """        LazyColumn(
            modifier = Modifier.weight(1f),
            modifier = Modifier.fillMaxSize(),"""

new_albums = """        LazyColumn(
            modifier = Modifier.fillMaxSize(),"""

content = content.replace(old_albums, new_albums)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
