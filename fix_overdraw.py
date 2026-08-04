import re

with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'r') as f:
    content = f.read()

# Add transition
content = content.replace(
    'var isFullscreenConsoleVisible by remember { mutableStateOf(false) }',
    'var isFullscreenConsoleVisible by remember { mutableStateOf(false) }\n    val playerTransition = androidx.compose.animation.core.updateTransition(targetState = isFullscreenConsoleVisible, label = "player")'
)

# Use transition for player
content = content.replace(
    'AnimatedVisibility(\n        visible = isFullscreenConsoleVisible,',
    'playerTransition.AnimatedVisibility(\n        visible = { it },'
)

# Hide Scaffold when player is fully visible
scaffold_code = '''            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent
            ) { innerPadding ->'''

replacement = '''            val isPlayerFullyVisible = playerTransition.currentState && playerTransition.targetState
            if (!isPlayerFullyVisible) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent
            ) { innerPadding ->'''

content = content.replace(scaffold_code, replacement)

# Add closing bracket for if (!isPlayerFullyVisible) before WELCOME OVERLAY SCREEN
welcome_code = '    // WELCOME OVERLAY SCREEN'
content = content.replace(welcome_code, '            }\n    // WELCOME OVERLAY SCREEN')


with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'w') as f:
    f.write(content)

