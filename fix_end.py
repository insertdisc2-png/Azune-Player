with open('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 'r') as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    if 'WELCOME OVERLAY SCREEN' in line:
        idx = i
        break

for i in range(idx - 6, idx):
    print(f"{i}: {repr(lines[i])}")

# Let's count brackets manually to find where the imbalance is.
count = 0
for i, line in enumerate(lines):
    for char in line:
        if char == '{': count += 1
        elif char == '}': count -= 1

print(f"Total bracket count: {count}")
