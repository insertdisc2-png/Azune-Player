def find_matching(file_path, start_line):
    with open(file_path, 'r') as f:
        lines = f.readlines()
    
    count = 0
    found_start = False
    for i in range(start_line - 1, len(lines)):
        line = lines[i]
        for char in line:
            if char == '{':
                count += 1
                found_start = True
            elif char == '}':
                count -= 1
                if found_start and count == 0:
                    print(f"Match found at line {i + 1}")
                    return

find_matching('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 323) # Scaffold
find_matching('app/src/main/java/com/example/ui/view/MetroPlayerApp.kt', 318) # MetroBackgroundContainer
