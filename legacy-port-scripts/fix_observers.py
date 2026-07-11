import os

def fix_imports(file_path):
    # Skip the shims themselves to avoid circular dependencies
    if "core/exceptions/resolve" in file_path:
        return

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    def add_import(c, imp):
        # Only add if the symbol is used but the import is missing
        symbol = imp.split(".")[-1]
        if symbol not in c:
            return c
        if f"import {imp}" in c:
            return c
            
        lines = c.splitlines()
        new_lines = []
        added = False
        for line in lines:
            new_lines.append(line)
            if not added and line.startswith("package "):
                new_lines.append(f"import {imp}")
                added = True
        return "\n".join(new_lines)

    content = add_import(content, "com.nyora.hasan72341.core.exceptions.resolve.ExceptionResolver")
    content = add_import(content, "com.nyora.hasan72341.core.exceptions.resolve.SnackbarErrorObserver")
    content = add_import(content, "com.nyora.hasan72341.core.exceptions.resolve.DialogErrorObserver")
    content = add_import(content, "com.nyora.hasan72341.core.exceptions.resolve.ToastErrorObserver")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Fixed observers in: {file_path}")

def main():
    root_dirs = ["Nyora/nyora-android/app/src/main/kotlin"]
    for root_dir in root_dirs:
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                if file.endswith(".kt"):
                    fix_imports(os.path.join(root, file))

if __name__ == "__main__":
    main()
