with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if 'WELCOME OVERLAY SCREEN' in line:
        idx = i
        break

lines.pop(idx - 1)
lines.pop(idx - 2)

# Insert the bracket for `if (!isPlayerFullyVisible)` after Scaffold.
# In the file right now, line 603 (index 602) is `        }\n`
# We will insert it at index 603.
lines.insert(603, '            }\n')

with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'w') as f:
    f.writelines(lines)
