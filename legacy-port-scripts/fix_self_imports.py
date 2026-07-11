import os

def fix_self_imports(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    package = ""
    for line in lines:
        if line.startswith("package "):
            package = line.split()[1]
            break
    
    if not package: return

    new_lines = []
    changed = False
    for line in lines:
        if line.startswith("import " + package):
            changed = True
            continue
        new_lines.append(line)
    
    if changed:
        with open(file_path, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
        print(f"Fixed self-import in: {file_path}")

def main():
    root_dir = "Nyora/nyora-android/app/src/main/kotlin/com/nyora/hasan72341/core/exceptions/resolve"
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                fix_self_imports(os.path.join(root, file))

if __name__ == "__main__":
    main()
