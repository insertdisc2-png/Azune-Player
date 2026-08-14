import re

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

# Restore all isTabActive back to isActive
content = content.replace("if (isTabActive)", "if (isActive)")

# But keep the specific Tab ones correctly
# First, revert the declaration
content = content.replace("val isTabActive = activePivotIndex == index && selectedPlaylistIdDetail == null", "val isTabActive = activePivotIndex == index && selectedPlaylistIdDetail == null") # Keep it as isTabActive
# We just need to fix the Tab
tab_bad = """                            Tab(
                                selected = isTabActive,"""
# It's already isTabActive in the Tab declaration.
# But inside Tab content:
text_bad = """                                Text(
                                    text = label,
                                    color = if (isActive) (if (isLight) Color.Black else Color.White) else (if (isLight) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f)),
                                    fontFamily = getMetroFontFamily(settings.fontFamily),
                                    fontWeight = if (isActive) FontWeight.Light else FontWeight.ExtraLight,
                                    fontSize = if (isActive) 34.sp else 24.sp,"""

text_good = """                                Text(
                                    text = label,
                                    color = if (isTabActive) (if (isLight) Color.Black else Color.White) else (if (isLight) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f)),
                                    fontFamily = getMetroFontFamily(settings.fontFamily),
                                    fontWeight = if (isTabActive) FontWeight.Light else FontWeight.ExtraLight,
                                    fontSize = if (isTabActive) 34.sp else 24.sp,"""

content = content.replace(text_bad, text_good)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
