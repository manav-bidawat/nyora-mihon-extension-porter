#!/usr/bin/env python3
"""Emit the Mihon extension-store index from the built APKs in <repo>/apk:
  - index.min.json : the extension list (NetworkLegacyExtension[])
  - repo.json      : the store metadata (NetworkLegacyExtensionRepo) — Mihon
                     fetches <base>/repo.json first and REQUIRES `meta`, incl.
                     `signingKeyFingerprint` (the APK signing cert SHA-256, so
                     the extensions install as trusted).

versionCode/versionName are read from each APK (aapt dump badging), so the index
can never advertise an update the APKs don't actually contain — Mihon shows an
update iff repo.code > installed.code OR repo libVersion > installed libVersion,
where repo libVersion = version.substringBeforeLast('.'). This script also FAILS
if that major.minor doesn't equal the APK's `tachiyomix.extensionLib` manifest
float (the exact drift that made the update badge stick forever — see mihon
ExtensionManager.updateExists).

Usage: generate-repo.py <repo-dir> [signingKeyFingerprint]
       (fingerprint auto-detected from the APK via apksigner if omitted)
"""
import glob
import json
import os
import re
import shutil
import struct
import subprocess
import sys

# Fallback only, when aapt is unavailable. versionName MUST stay
# "<tachiyomix.extensionLib>.<patch>" (see build.gradle.kts CONTRACT note).
VERSION = "1.6.19"
VERSION_CODE = 19
WEBSITE = "https://github.com/Nyora-Manga/nyora-mihon"
EXTS = [
    {"name": "Nyora-Sources",     "pkg": "eu.kanade.tachiyomi.extension.all.nyoralocal",   "nsfw": 0},
    {"name": "Nyora-Sources 18+", "pkg": "eu.kanade.tachiyomi.extension.all.nyoralocal18", "nsfw": 1},
]

_SDK_ROOTS = [
    os.environ.get("ANDROID_HOME"),
    os.environ.get("ANDROID_SDK_ROOT"),
    os.path.expanduser("~/Library/Android/sdk"),  # macOS local dev
    os.path.expanduser("~/Android/Sdk"),
]


def _find_tool(name):
    """Locate an SDK build-tools binary across SDK layouts (CI + macOS + PATH)."""
    for root in filter(None, _SDK_ROOTS):
        cands = sorted(glob.glob(os.path.join(root, "build-tools", "*", name)))
        if cands:
            return cands[-1]
    onpath = shutil.which(name)
    if onpath:
        return onpath
    raise FileNotFoundError(f"{name} not found (set ANDROID_HOME or install build-tools)")


def cert_fingerprint(apk):
    """SHA-256 of the APK signing cert, lowercase hex (Mihon's fingerprint form)."""
    out = subprocess.run([_find_tool("apksigner"), "verify", "--print-certs", apk],
                         capture_output=True, text=True).stdout
    m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", out)
    return m.group(1).lower() if m else ""


def apk_version(apk):
    """(versionCode, versionName) from the APK itself — the single source of truth."""
    out = subprocess.run([_find_tool("aapt"), "dump", "badging", apk],
                         capture_output=True, text=True).stdout
    m = re.search(r"versionCode='(\d+)' versionName='([^']+)'", out)
    if not m:
        raise RuntimeError(f"could not read versionCode/versionName from {apk}")
    return int(m.group(1)), m.group(2)


def apk_extension_lib(apk):
    """The `tachiyomix.extensionLib` manifest float (what Mihon reads as libVersion)."""
    out = subprocess.run([_find_tool("aapt"), "dump", "xmltree", apk, "AndroidManifest.xml"],
                         capture_output=True, text=True).stdout
    m = re.search(r"tachiyomix\.extensionLib.*?\n.*?=\(type 0x4\)0x([0-9a-fA-F]{8})", out)
    if not m:
        raise RuntimeError(f"tachiyomix.extensionLib not found in {apk}")
    return struct.unpack(">f", struct.pack(">I", int(m.group(1), 16)))[0]


def main(repo, fingerprint=None):
    index = []
    for e in EXTS:
        apk = f"{e['pkg']}.apk"
        path = os.path.join(repo, "apk", apk)
        if not os.path.exists(path):
            print(f"  ! missing apk {apk} — skipped", file=sys.stderr)
            continue
        if not fingerprint:
            fingerprint = cert_fingerprint(path)
        try:
            code, version = apk_version(path)
            lib = apk_extension_lib(path)
            major_minor = version.rsplit(".", 1)[0]
            if abs(float(major_minor) - lib) > 1e-6:
                sys.exit(
                    f"::error::{apk} versionName '{version}' implies libVersion {major_minor} "
                    f"but manifest extensionLib is {lib} — Mihon would flag a PERMANENT update. "
                    f"Use versionName '<extensionLib>.<patch>'."
                )
        except FileNotFoundError:
            print("  ! aapt/apksigner not found — falling back to hardcoded "
                  f"{VERSION} ({VERSION_CODE})", file=sys.stderr)
            code, version = VERSION_CODE, VERSION
        index.append({
            "name": e["name"], "pkg": e["pkg"], "apk": apk,
            "lang": "all", "code": code, "version": version,
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
