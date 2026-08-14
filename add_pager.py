with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

pager_code = """    var isTileEditMode by remember { mutableStateOf(false) }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { 6 }
    )
    LaunchedEffect(pagerState.currentPage) {
        if (selectedPlaylistIdDetail == null) {
            activePivotIndex = pagerState.currentPage
        }
    }
"""

content = content.replace("    var isTileEditMode by remember { mutableStateOf(false) }\n", pager_code)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
