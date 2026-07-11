import os

def fix_subclasses(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Fix inheritance without generics
    content = content.replace("MangaListFragment()", "MangaListFragment<com.nyora.hasan72341.databinding.FragmentMangaListBinding>()")
    # Fix some common ones that might not have parens
    content = content.replace("MangaListFragment {", "MangaListFragment<com.nyora.hasan72341.databinding.FragmentMangaListBinding> {")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Fixed subclass: {file_path}")

def main():
    root_dir = "Nyora/nyora-android/app/src/main/kotlin/com/nyora/hasan72341"
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                fix_subclasses(os.path.join(root, file))

if __name__ == "__main__":
    main()
