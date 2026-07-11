import os

def fix_subclasses(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Fix inheritance with generics to non-generic
    content = content.replace("MangaListFragment<com.nyora.hasan72341.databinding.FragmentMangaListBinding>", "MangaListFragment")
    content = content.replace("MangaListFragment<FragmentMangaListBinding>", "MangaListFragment")
    
    # Fix onViewBindingCreated parameter
    content = content.replace("override fun onViewBindingCreated(binding: FragmentMangaListBinding, savedInstanceState: Bundle?)", "override fun onViewBindingCreated(binding: com.nyora.hasan72341.databinding.FragmentMangaListBinding, savedInstanceState: Bundle?)")

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
