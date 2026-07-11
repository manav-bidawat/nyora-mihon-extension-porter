import os
import re

def fix_strings(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Check for memory_usage_pattern with multiple %s or %d without positions
    pattern = r'<string name="memory_usage_pattern".*?>(.*?)<\/string>'
    matches = re.findall(pattern, content)
    
    changed = False
    for match in matches:
        # If it has more than one % but no %1$ or %2$
        if match.count('%') > 1 and '%1$' not in match:
            # Replace with a safe fixed version
            new_val = "%1$s - %2$s"
            content = content.replace(f">{match}</string>", f">{new_val}</string>")
            changed = True
            print(f"Fixed {file_path}: {match} -> {new_val}")

    if changed:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)

def main():
    res_dir = "Nyora/nyora-android/app/src/main/res"
    for root, dirs, files in os.walk(res_dir):
        if "strings.xml" in files:
            fix_strings(os.path.join(root, "strings.xml"))

if __name__ == "__main__":
    main()
