with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

old_end = """                item(key = "artist_div_$artistName", contentType = "divider") {
                    Divider(color = textPrimaryColor.copy(alpha = 0.08f), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

// PANE 3.5: Albums Panel"""

new_end = """                item(key = "artist_div_$artistName", contentType = "divider") {
                    Divider(color = textPrimaryColor.copy(alpha = 0.08f), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    }
}

// PANE 3.5: Albums Panel"""

content = content.replace(old_end, new_end)

with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "w") as f:
    f.write(content)

print("Done")
