import re

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'r') as f:
    content = f.read()

# Replace Canvas alpha modifier
old_canvas = 'Canvas(modifier = Modifier.fillMaxSize().alpha(if (resolvedTheme == "light") 0.04f else 0.08f)) {'
new_canvas = '''val gridAlpha = if (resolvedTheme == "light") 0.04f else 0.08f
            val gridColor = lineAccent.copy(alpha = gridAlpha)
            Canvas(modifier = Modifier.fillMaxSize()) {'''

content = content.replace(old_canvas, new_canvas)

# Replace lineAccent in drawLine inside the Canvas block
content = content.replace('color = lineAccent,', 'color = gridColor,')

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'w') as f:
    f.write(content)

