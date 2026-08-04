import re

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'r') as f:
    content = f.read()

old_box = '''                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradientBrush)
                        .alpha(settings.appBackgroundOpacity)
                )'''

new_box = '''                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = gradientBrush, alpha = settings.appBackgroundOpacity)
                )'''

content = content.replace(old_box, new_box)

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'w') as f:
    f.write(content)

