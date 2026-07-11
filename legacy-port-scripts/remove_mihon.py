import os

def replace_in_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Replace mihon package with parsers package
    content = content.replace("com.nyora.hasan72341.mihon", "com.nyora.hasan72341.parsers")
    
    # Replace in any other places like build.gradle or proguard
    if file_path.endswith(".gradle") or file_path.endswith(".pro"):
         content = content.replace("com.nyora.android.mihon", "com.nyora.android.parsers")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Updated: {file_path}")

def main():
    root_dirs = ["Nyora/nyora-android/app/src", "Nyora/nyora-android/app"]
    for root_dir in root_dirs:
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                if file.endswith((".kt", ".xml", ".gradle", ".pro")):
                    replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
