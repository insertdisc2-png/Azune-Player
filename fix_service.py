import re

with open('app/src/main/java/com/example/service/MusicNotificationService.kt', 'r') as f:
    content = f.read()

# We want to remove album art loading from updateMediaSession and buildNotification,
# and instead pass it in, or just not load it for now to avoid the ANR/Crash.
# Let's just remove album art loading completely from the service for now to guarantee stability.

content = re.sub(
    r'        var albumArtBitmap = try \{.*?\n        \} catch \(_: Exception\) \{\n            null\n        \}',
    '        val albumArtBitmap: android.graphics.Bitmap? = null',
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/com/example/service/MusicNotificationService.kt', 'w') as f:
    f.write(content)
