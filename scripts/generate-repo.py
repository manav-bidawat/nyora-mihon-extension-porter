#!/usr/bin/env python3
"""Emit the Mihon extension-repo index (index.min.json + repo.json) from the
built APKs in <repo>/apk. Mirrors the format Mihon's "Add extension repo" reads."""
import json
import os
import sys

VERSION = "1.6.0"
EXTS = [
    {"name": "Nyora-Sources",     "pkg": "eu.kanade.tachiyomi.extension.all.nyoralocal",   "nsfw": 0},
    {"name": "Nyora-Sources 18+", "pkg": "eu.kanade.tachiyomi.extension.all.nyoralocal18", "nsfw": 1},
]


def main(repo):
    index = []
    for e in EXTS:
        apk = f"{e['pkg']}.apk"
        if not os.path.exists(os.path.join(repo, "apk", apk)):
            print(f"  ! missing apk {apk} — skipped", file=sys.stderr)
            continue
        index.append({
            "name": e["name"], "pkg": e["pkg"], "apk": apk,
            "lang": "all", "code": 1, "version": VERSION,
            "nsfw": e["nsfw"], "sources": None,
        })
    with open(os.path.join(repo, "index.min.json"), "w") as f:
        json.dump(index, f, ensure_ascii=False)
    with open(os.path.join(repo, "repo.json"), "w") as f:
        json.dump({"index_v2": None, "extensions": index}, f, ensure_ascii=False, indent=2)
    print(f"  wrote index for {len(index)} extension(s)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "repo")
