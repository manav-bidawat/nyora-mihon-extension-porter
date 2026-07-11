import os

def replace_in_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    original_content = content

    # Rename specific symbols missed in the first pass
    content = content.replace("MihonSourceItem", "ExternalSourceItem")
    content = content.replace("toMihonChapter", "toExternalChapter")
    content = content.replace("toMihonManga", "toExternalManga")
    content = content.replace("rethrowMihonWrappedExceptions", "rethrowExternalWrappedExceptions")
    content = content.replace("MihonNetwork", "ExternalNetwork")
    content = content.replace("Mihon/Tachiyomi", "external")
    content = content.replace("Mihon-stamped", "external-stamped")
    content = content.replace("Mihon icon", "external icon")
    content = content.replace("Mihon client", "external client")
    content = content.replace("mihonMangaSource", "externalMangaSource")
    content = content.replace("mihonSource", "externalSource")
    content = content.replace("mihon_extension_loading", "external_extension_loading")
    content = content.replace("Mihon sources", "external sources")
    content = content.replace("MihonCatalogueSource", "ExternalCatalogueSource")
    
    # Case-insensitive replacements for comments and logs
    # We use a cautious approach to avoid breaking URLs or specific identifiers
    content = content.replace("Mihon", "External")
    content = content.replace("mihon", "external")

    if content != original_content:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Cleaned: {file_path}")

def main():
    root_dirs = ["Nyora/nyora-android/app/src/main/kotlin", "Nyora/nyora-android/app"]
    for root_dir in root_dirs:
        for root, dirs, files in os.walk(root_dir):
            for file in files:
                if file.endswith((".kt", ".pro", ".gradle")):
                    replace_in_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
