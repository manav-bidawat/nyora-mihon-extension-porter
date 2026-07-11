import os

def replace_in_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Redirect tachiyomi network await to our new local util
    content = content.replace("com.nyora.hasan72341.tachiyomi.network.await", "com.nyora.hasan72341.core.parsers.util.await")
    content = content.replace("eu.kanade.tachiyomi.network.await", "com.nyora.hasan72341.core.parsers.util.await")
    
    # Remove other broken tachiyomi imports
    lines = content.splitlines()
    new_lines = []
    for line in lines:
        if "import com.nyora.hasan72341.tachiyomi" in line: continue
        if "import eu.kanade.tachiyomi" in line: continue
        if "import org.koitharu.kotatsu.parsers" in line: continue
        new_lines.append(line)
    content = "\n".join(new_lines)

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Cleaned imports: {file_path}")

def main():
    root_dirs = ["Nyora/nyora-android/app/src/main/kotlin/com/nyora/hasan72341"]
    for root_dir in root_dirs:
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                if file.endswith(".kt"):
                    replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
