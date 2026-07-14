#!/usr/bin/env python3
"""Emit the Mihon extension-store index from the built APKs in <repo>/apk:
  - index.min.json : the extension list (NetworkLegacyExtension[])
  - repo.json      : the store metadata (NetworkLegacyExtensionRepo) — Mihon
                     fetches <base>/repo.json first and REQUIRES `meta`, incl.
                     `signingKeyFingerprint` (the APK signing cert SHA-256, so
                     the extensions install as trusted).

Usage: generate-repo.py <repo-dir> [signingKeyFingerprint]
       (fingerprint auto-detected from the APK via apksigner if omitted)
"""
import glob
import json
import os
import re
import subprocess
import sys

VERSION = "1.7.3"
VERSION_CODE = 9
WEBSITE = "https://github.com/Hasan72341/nyora-mihon"
EXTS = [
    {"name": "Nyora-Sources",     "pkg": "eu.kanade.tachiyomi.extension.all.nyoralocal",   "nsfw": 0},
    {"name": "Nyora-Sources 18+", "pkg": "eu.kanade.tachiyomi.extension.all.nyoralocal18", "nsfw": 1},
]


def cert_fingerprint(apk):
    """SHA-256 of the APK signing cert, lowercase hex (Mihon's fingerprint form)."""
    apksigner = sorted(glob.glob(os.path.expanduser(
        "~/Library/Android/sdk/build-tools/*/apksigner")))[-1]
    out = subprocess.run([apksigner, "verify", "--print-certs", apk],
                         capture_output=True, text=True).stdout
    m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", out)
    return m.group(1).lower() if m else ""


def main(repo, fingerprint=None):
    index = []
    for e in EXTS:
        apk = f"{e['pkg']}.apk"
        if not os.path.exists(os.path.join(repo, "apk", apk)):
            print(f"  ! missing apk {apk} — skipped", file=sys.stderr)
            continue
        if not fingerprint:
            fingerprint = cert_fingerprint(os.path.join(repo, "apk", apk))
        index.append({
            "name": e["name"], "pkg": e["pkg"], "apk": apk,
            "lang": "all", "code": VERSION_CODE, "version": VERSION,
            "nsfw": e["nsfw"], "sources": None,
        })
    with open(os.path.join(repo, "index.min.json"), "w") as f:
        json.dump(index, f, ensure_ascii=False)
    with open(os.path.join(repo, "repo.json"), "w") as f:
        json.dump({
            "index_v2": None,
            "meta": {
                "name": "Nyora",
                "shortName": "Nyora",
                "website": WEBSITE,
                "signingKeyFingerprint": fingerprint,
            },
        }, f, ensure_ascii=False, indent=2)
    print(f"  wrote index for {len(index)} extension(s); fingerprint={fingerprint[:16]}...")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "repo",
         sys.argv[2] if len(sys.argv) > 2 else None)
