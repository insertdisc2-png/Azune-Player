import re

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'r') as f:
    content = f.read()

old_tile = '''    Card(
        modifier = modifier
            .padding(4.dp)
            .then(
                when (sizeType) {
                    2 -> Modifier.aspectRatio(2f)
                    3 -> Modifier.aspectRatio(0.48f)
                    else -> Modifier.aspectRatio(1f)
                }
            )
            .clickable(onClick = onClick)
            .border(2.dp, tileBorderColor),
        colors = CardDefaults.cardColors(containerColor = tileBgColor),
        shape = androidx.compose.ui.graphics.RectangleShape // Metro standard 90-degree vector box
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {'''

new_tile = '''    Box(
        modifier = modifier
            .padding(4.dp)
            .then(
                when (sizeType) {
                    2 -> Modifier.aspectRatio(2f)
                    3 -> Modifier.aspectRatio(0.48f)
                    else -> Modifier.aspectRatio(1f)
                }
            )
            .background(tileBgColor)
            .border(2.dp, tileBorderColor)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {'''

content = content.replace(old_tile, new_tile)

with open('app/src/main/java/com/example/ui/components/MetroComponents.kt', 'w') as f:
    f.write(content)

