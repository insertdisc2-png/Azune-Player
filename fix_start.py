import re

with open('app/src/main/java/com/example/service/MusicNotificationService.kt', 'r') as f:
    content = f.read()

# Change startForegroundService to startService
content = re.sub(
    r'                if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.O\) \{\n                    context\.startForegroundService\(intent\)\n                \} else \{\n                    context\.startService\(intent\)\n                \}',
    '                context.startService(intent)',
    content
)

with open('app/src/main/java/com/example/service/MusicNotificationService.kt', 'w') as f:
    f.write(content)
