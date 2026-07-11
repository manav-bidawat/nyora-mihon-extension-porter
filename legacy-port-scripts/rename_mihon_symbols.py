import os

def replace_in_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Rename more symbols
    content = content.replace("MihonExtensionInfo", "ExternalExtensionInfo")
    content = content.replace("MihonInjektBridge", "ExternalInjektBridge")
    content = content.replace("MihonNetworkHelper", "ExternalNetworkHelper")
    content = content.replace("MihonFilter", "ExternalFilter")
    content = content.replace("ExternalExtensionType.MIHON", "ExternalExtensionType.EXTERNAL")
    content = content.replace("MIHON_", "EXTERNAL_")
    
    # Update comments and strings
    content = content.replace("Mihon extension", "external extension")
    content = content.replace("Mihon compatibility", "external compatibility")
    content = content.replace("Mihon-compatible", "external-compatible")
    content = content.replace("Mihon-stamped", "external-stamped")
    content = content.replace("Mihon repository", "external repository")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Updated: {file_path}")

def main():
    root_dirs = ["Nyora/nyora-android/app/src/main/kotlin", "Nyora/nyora-android/app"]
    for root_dir in root_dirs:
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                if file.endswith((".kt", ".pro", ".gradle")):
                    replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
