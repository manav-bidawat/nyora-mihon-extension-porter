import os
import re

def replace_in_file(file_path):
    # Skip the shims themselves to avoid circular dependencies
    if "mihon/parsers" in file_path:
        return

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Replace library package with local shim package
    # We use a generic regex for the model package first
    content = content.replace("org.koitharu.kotatsu.parsers.model", "com.nyora.hasan72341.mihon.parsers.model")
    
    # Core infra shims
    content = content.replace("org.koitharu.kotatsu.parsers.MangaParserAuthProvider", "com.nyora.hasan72341.mihon.parsers.MangaParserAuthProvider")
    content = content.replace("org.koitharu.kotatsu.parsers.MangaLoaderContext", "com.nyora.hasan72341.mihon.parsers.MangaLoaderContext")
    content = content.replace("org.koitharu.kotatsu.parsers.MangaParser", "com.nyora.hasan72341.mihon.parsers.MangaParser")
    content = content.replace("org.koitharu.kotatsu.parsers.InternalParsersApi", "com.nyora.hasan72341.mihon.parsers.InternalParsersApi")
    
    # Bitmaps and Config
    content = content.replace("org.koitharu.kotatsu.parsers.bitmap.Bitmap", "com.nyora.hasan72341.mihon.parsers.Bitmap")
    content = content.replace("org.koitharu.kotatsu.parsers.bitmap.Rect", "com.nyora.hasan72341.mihon.parsers.Rect")
    content = content.replace("org.koitharu.kotatsu.parsers.config.ConfigKey", "com.nyora.hasan72341.mihon.parsers.ConfigKey")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Updated: {file_path}")

def main():
    root_dir = "Nyora/nyora-android/app/src/main/kotlin"
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
