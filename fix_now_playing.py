import re

with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'r') as f:
    content = f.read()

old_card = '''                            Card(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth()
                                    .then(
                                        when (sizeType) {
                                            2 -> Modifier.aspectRatio(2f)
                                            3 -> Modifier.aspectRatio(0.48f)
                                            else -> Modifier.aspectRatio(1f)
                                        }
                                    )
                                    .clickable { if (!isEditMode) onConsoleTrigger() }
                                    .dragToSwap("now_playing")
                                    .border(2.dp, gridTileBorderColor),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                                colors = CardDefaults.cardColors(containerColor = tileBg)
                            ) {'''

new_box = '''                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth()
                                    .then(
                                        when (sizeType) {
                                            2 -> Modifier.aspectRatio(2f)
                                            3 -> Modifier.aspectRatio(0.48f)
                                            else -> Modifier.aspectRatio(1f)
                                        }
                                    )
                                    .background(tileBg)
                                    .border(2.dp, gridTileBorderColor)
                                    .dragToSwap("now_playing")
                                    .clickable { if (!isEditMode) onConsoleTrigger() }
                            ) {'''

content = content.replace(old_card, new_box)

with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'w') as f:
    f.write(content)

