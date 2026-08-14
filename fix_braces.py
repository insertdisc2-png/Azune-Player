with open("app/src/main/java/com/example/ui/view/MetroPlayerApp.kt", "r") as f:
    content = f.read()

bad_end = """                            }
                            }
                        }
                    }
                }
                // End workspace content"""

# Wait, let's look at the actual file at that location to be precise.
