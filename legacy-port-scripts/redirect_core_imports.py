import os

def replace_in_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Redirect all parser-related imports to the new core structure
    content = content.replace("com.nyora.hasan72341.mihon.parsers", "com.nyora.hasan72341.core.parsers")
    content = content.replace("com.nyora.hasan72341.parsers.model", "com.nyora.hasan72341.core.parsers.model")
    content = content.replace("com.nyora.hasan72341.parsers.util", "com.nyora.hasan72341.core.parsers.util")
    content = content.replace("com.nyora.hasan72341.parsers.exception", "com.nyora.hasan72341.core.parsers.exception")
    content = content.replace("com.nyora.hasan72341.parsers.core", "com.nyora.hasan72341.core.parsers.core")
    content = content.replace("com.nyora.hasan72341.parsers.config", "com.nyora.hasan72341.core.parsers.config")
    
    # Root package (if any remains)
    content = content.replace("com.nyora.hasan72341.parsers.InternalParsersApi", "com.nyora.hasan72341.core.parsers.InternalParsersApi")
    content = content.replace("com.nyora.hasan72341.parsers.ErrorMessages", "com.nyora.hasan72341.core.parsers.ErrorMessages")
    content = content.replace("com.nyora.hasan72341.parsers.MangaLoaderContext", "com.nyora.hasan72341.core.parsers.MangaLoaderContext")
    content = content.replace("com.nyora.hasan72341.parsers.MangaParser", "com.nyora.hasan72341.core.parsers.MangaParser")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Updated imports: {file_path}")

def main():
    root_dirs = ["Nyora/nyora-android/app/src/main/kotlin/com/nyora/hasan72341", "Nyora/nyora-android/app/src/androidTest/kotlin/com/nyora/hasan72341", "Nyora/nyora-android/app/src/debug/kotlin/com/nyora/hasan72341"]
    for root_dir in root_dirs:
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                if file.endswith(".kt"):
                    replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
