with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# Fix isActive
content = content.replace("val isActive = activePivotIndex == index && selectedPlaylistIdDetail == null", "val isTabActive = activePivotIndex == index && selectedPlaylistIdDetail == null")
content = content.replace("selected = isActive", "selected = isTabActive")
content = content.replace("if (isActive)", "if (isTabActive)")

# Fix isDetail
old_anim = """                    ) { isDetail ->
                        if (isDetail) {"""
new_anim = """                    ) { targetIsDetail ->
                        if (targetIsDetail) {"""
content = content.replace(old_anim, new_anim)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
