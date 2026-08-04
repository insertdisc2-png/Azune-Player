import re

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'r') as f:
    content = f.read()

# Chain the backgrounds
content = content.replace(
    '.background(baseBgColor) // Dynamic deep theme',
    '.background(baseBgColor) // Dynamic deep theme\n            .background(Brush.radialGradient(colors = sweepColors, radius = 2000f))'
)

# Remove the inner sweep box
sweep_box_pattern = r'        // Draw elegant orbital background sweep to break solid flatness\s+Box\(\s+modifier = Modifier\s+\.fillMaxSize\(\)\s+\.background\(\s+Brush\.radialGradient\(\s+colors = sweepColors,\s+radius = 2000f\s+\)\s+\)\s+\)\n'

content = re.sub(sweep_box_pattern, '', content)

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'w') as f:
    f.write(content)

