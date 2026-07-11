import os

def replace_in_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Fix the redundant .parsers.parsers in imports
    content = content.replace("com.nyora.hasan72341.parsers.parsers", "com.nyora.hasan72341.parsers")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Fixed imports: {file_path}")

def main():
    root_dirs = ["Nyora/nyora-android/app/src"]
    for root_dir in root_dirs:
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                if file.endswith(".kt"):
                    replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
