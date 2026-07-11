import os

def fix_packages(root_dir, old_base, new_base):
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                path = os.path.join(root, file)
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                # Update package declaration
                content = content.replace(f"package {old_base}", f"package {new_base}")
                # Update internal imports
                content = content.replace(f"import {old_base}", f"import {new_base}")
                
                with open(path, "w", encoding="utf-8") as f:
                    f.write(content)
                print(f"Fixed package: {path}")

def main():
    base = "Nyora/nyora-android/app/src/main/kotlin/com/nyora/hasan72341/core/parsers"
    # Note: mihon was already renamed to parsers in my previous steps, 
    # but the files were moved from ...parsers/... to ...core/parsers/...
    fix_packages(base, "com.nyora.hasan72341.parsers", "com.nyora.hasan72341.core.parsers")

if __name__ == "__main__":
    main()
