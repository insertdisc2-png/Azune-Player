with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'r') as f:
    content = f.read()
content = content.replace('Text(\n                        text = "Version: 1.0\\nEngine: Jetpack Compose / Kotlin\\nDeveloper: Azune Team\\n\\nA clean, beautiful offline MP3 music player featuring high-fidelity local playback, custom adaptive visual themes, and lyrics scrolling support.",', 'Text(\n                        text = "Version: ${com.example.BuildConfig.VERSION_NAME}\\nEngine: Jetpack Compose / Kotlin\\nDeveloper: Azune Team\\n\\nA clean, beautiful offline MP3 music player featuring high-fidelity local playback, custom adaptive visual themes, and lyrics scrolling support.",')
with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'w') as f:
    f.write(content)
