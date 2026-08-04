with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if 'WELCOME OVERLAY SCREEN' in line:
        idx = i
        break

lines.pop(idx - 1)

with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'w') as f:
    f.writelines(lines)
