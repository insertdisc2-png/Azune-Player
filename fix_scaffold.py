import re

with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'r') as f:
    content = f.read()

# Replace the `if (!isPlayerFullyVisible) {` with just graphicsLayer modifier
old_scaffold_start = '''            val isPlayerFullyVisible = playerTransition.currentState && playerTransition.targetState
            if (!isPlayerFullyVisible) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),'''

new_scaffold_start = '''            val isPlayerFullyVisible = playerTransition.currentState && playerTransition.targetState
            Scaffold(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (isPlayerFullyVisible) 0f else 1f },'''

content = content.replace(old_scaffold_start, new_scaffold_start)

# We also need to remove the matching `}` we inserted at line 603/604.
# Let's use a regex to find the `}` right before `// WELCOME OVERLAY SCREEN`
pattern = r'            \}\n    // WELCOME OVERLAY SCREEN'
content = re.sub(pattern, '    // WELCOME OVERLAY SCREEN', content)

with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'w') as f:
    f.write(content)

