with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

old_items = """                items(items = playlistTracks, key = { it.id }) { track ->"""

new_items = """                items(items = playlistTracks) { track ->"""

content = content.replace(old_items, new_items)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
