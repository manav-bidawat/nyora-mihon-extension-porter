import os

def fix_mess(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    new_lines = []
    package_line = -1
    for i, line in enumerate(lines):
        if line.startswith("package "):
            package_line = i
            break
    
    if package_line > 0:
        # Move package line to the top
        pkg = lines[package_line]
        new_lines.append(pkg)
        for i, line in enumerate(lines):
            if i != package_line:
                new_lines.append(line)
        
        with open(file_path, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
        print(f"Fixed mess in: {file_path}")

def main():
    root_dir = "Nyora/nyora-android/app/src/main/kotlin/com/nyora/hasan72341/core/exceptions/resolve"
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                fix_mess(os.path.join(root, file))

if __name__ == "__main__":
    main()
