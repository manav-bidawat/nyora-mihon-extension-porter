import os

def replace_in_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Replace class names and references
    content = content.replace("MihonExtensionManager", "ExternalExtensionManager")
    content = content.replace("MihonExtensionLoader", "ExternalExtensionLoader")
    content = content.replace("MihonMangaRepository", "ExternalMangaRepository")
    content = content.replace("MihonFilterMapper", "ExternalFilterMapper")
    content = content.replace("MihonModule", "ParserModule")
    content = content.replace("GetMihonSourcesUseCase", "GetExternalSourcesUseCase")
    content = content.replace("MihonLoadResult", "ExternalLoadResult")
    content = content.replace("MihonMangaSource", "ExternalMangaSource")
    content = content.replace("MihonDataConverters", "ExternalDataConverters")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Updated symbols: {file_path}")

def main():
    root_dirs = ["Nyora/nyora-android/app/src/main/kotlin/com/nyora/hasan72341"]
    for root_dir in root_dirs:
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                if file.endswith(".kt"):
                    replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
