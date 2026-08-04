import re

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '.background(if (isCurrent) activeTrackBg else Color.Transparent)',
    '.then(if (isCurrent) Modifier.background(activeTrackBg) else Modifier)'
)

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'w') as f:
    f.write(content)

