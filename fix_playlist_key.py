with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# Just remove the key entirely or use an index. The safest is to not provide a key and let Compose use position.
old_items = """                items(
                    items = playlistTracks,
                    key = { song -> "pt_${song.id}" }
                ) { song ->"""

new_items = """                items(
                    items = playlistTracks
                ) { song ->"""

content = content.replace(old_items, new_items)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
