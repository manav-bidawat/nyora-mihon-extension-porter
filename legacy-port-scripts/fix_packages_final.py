import os

def fix_packages(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    new_content = content.replace("package com.nyora.hasan72341", "package com.nyora.android")
    new_content = new_content.replace("import com.nyora.hasan72341", "import com.nyora.android")

    if new_content != content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"Fixed package in: {file_path}")

def main():
    root_dir = "Nyora/nyora-android/app/src/main/kotlin/com/nyora/android"
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                fix_packages(os.path.join(root, file))

if __name__ == "__main__":
    main()
