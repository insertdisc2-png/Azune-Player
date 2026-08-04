import re

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'r') as f:
    content = f.read()

# Replace multiple backgrounds with just baseBgColor
content = content.replace(
    '.background(baseBgColor) // Dynamic deep theme\n            .background(Brush.radialGradient(colors = sweepColors, radius = 2000f))',
    '.background(baseBgColor) // Dynamic deep theme'
)
content = content.replace(
    '.background(baseBgColor) // Dynamic deep theme\n            .background(androidx.compose.ui.graphics.Brush.radialGradient(colors = sweepColors, radius = 2000f))',
    '.background(baseBgColor) // Dynamic deep theme'
)

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'w') as f:
    f.write(content)

