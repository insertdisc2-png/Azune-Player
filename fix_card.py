import re

def replace_cards(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # In MetroTile
    # Card( modifier = ..., colors = CardDefaults.cardColors(containerColor = tileBgColor), shape = RectangleShape ) { Box {
    
    # We can just replace Card with Box and move the background modifier.
    pass

