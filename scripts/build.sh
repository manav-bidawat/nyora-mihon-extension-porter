#!/usr/bin/env bash
#
# Build the fully-local Nyora Mihon extension (SFW + 18+) from the latest
# nyora-shared. Reproducible: pins the parser engine, fetches the glue + fixes
# from nyora-shared, splits the catalog by NSFW, builds both flavors, and
# emits a ready-to-publish repo/ .
#
# Usage:  scripts/build.sh            (NYORA_SHARED_REF=main)
#         NYORA_SHARED_REF=<sha> scripts/build.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NYORA_SHARED_REF="${NYORA_SHARED_REF:-main}"
PARSERS_REF="59c033ecfd"                          # same ref as helper + android
CATALOG_URL="https://api.hasanraza.tech/sources/catalog"
WORK="$ROOT/.work"
GLUE_PKG="com/nyora/hasan72341/shared"
GLUE_DST="$ROOT/extension/src/main/kotlin/$GLUE_PKG"

echo "▶ nyora-mihon-local build  (nyora-shared@$NYORA_SHARED_REF, parsers@$PARSERS_REF)"
rm -rf "$WORK"; mkdir -p "$WORK"

# 1) Fetch nyora-shared (the parser glue + all fixes: LibApiHeaders, MangaEclipse
#    absolutize, parser-Interceptor binding, KotatsuLoaderContext, catalog).
echo "── fetch nyora-shared"
git clone --quiet --depth 1 --branch "$NYORA_SHARED_REF" \
  https://github.com/Hasan72341/nyora-shared.git "$WORK/nyora-shared" 2>/dev/null \
  || git clone --quiet "https://github.com/Hasan72341/nyora-shared.git" "$WORK/nyora-shared"
( cd "$WORK/nyora-shared" && git checkout --quiet "$NYORA_SHARED_REF" 2>/dev/null || true )

# 2) Copy ONLY the parser-relevant glue into the extension (not the REST server,
#    sync, DB — those are helper-only). The extension's own MangaLoaderContext
#    (Android, OkHttp) + Source bridge live under extension/src.
echo "── vendor parser glue from nyora-shared"
SRC="$WORK/nyora-shared/src/jvmMain/kotlin/$GLUE_PKG"
mkdir -p "$GLUE_DST/net" "$GLUE_DST/extension"
for f in net/LibApiHeaders.kt net/BrowserHeaders.kt net/HelperNetworkSettings.kt \
         extension/KotatsuParserExtensionService.kt extension/NativeParserCatalog.kt; do
  if [ -f "$SRC/$f" ]; then cp "$SRC/$f" "$GLUE_DST/$f"; echo "   + $f"; else echo "   ! missing $f (skipped)"; fi
done
# record provenance
( cd "$WORK/nyora-shared" && git rev-parse HEAD ) > "$ROOT/extension/src/main/assets/nyora-shared.ref" 2>/dev/null || true

# 3) Catalog → SFW / 18+ source lists baked into the APK assets.
echo "── fetch + split catalog"
mkdir -p "$ROOT/extension/src/main/assets"
python3 - "$CATALOG_URL" "$ROOT/extension/src/main/assets" <<'PY'
import sys, json, urllib.request
url, out = sys.argv[1], sys.argv[2]
data = json.load(urllib.request.urlopen(url, timeout=30))
entries = data.get("entries") or data.get("sources") or []
norm = [{"id": e["id"], "name": e.get("name") or e["id"], "lang": e.get("lang") or "all",
         "nsfw": bool(e.get("isNsfw") or e.get("nsfw"))} for e in entries]
sfw = [e for e in norm if not e["nsfw"]]
alls = norm
json.dump(sfw,  open(f"{out}/sources-sfw.json", "w"), ensure_ascii=False)
json.dump(alls, open(f"{out}/sources-all.json", "w"), ensure_ascii=False)
print(f"   catalog: {len(norm)} total, {len(sfw)} SFW, {len(norm)-len(sfw)} adult")
PY

# 4) Build both flavors. Each flavor bakes its identity + source-list via -P props
#    (mirrors nyora-mihon-parser's generator), consuming the vendored glue +
#    kotatsu-parsers-redo:$PARSERS_REF (see extension/build.gradle.kts).
build_flavor() {  # <pkgSuffix> <name> <nsfw> <sources-asset>
  local suffix="$1" name="$2" nsfw="$3" list="$4"
  echo "── assemble $name  (nsfw=$nsfw)"
  ( cd "$ROOT/extension" && ./gradlew --quiet assembleRelease \
      -PnyoraPkgSuffix="$suffix" -PnyoraName="$name" -PnyoraNsfw="$nsfw" -PnyoraList="$list" \
      -PparsersRef="$PARSERS_REF" )
  cp "$ROOT/extension/build/outputs/apk/release/extension-release.apk" \
     "$ROOT/repo/apk/eu.kanade.tachiyomi.extension.all.nyora${suffix}.apk"
}
mkdir -p "$ROOT/repo/apk" "$ROOT/repo/icon"
build_flavor "local"   "Nyora-Sources"     0 "sources-sfw.json"
build_flavor "local18" "Nyora-Sources 18+" 1 "sources-all.json"

# 5) Emit the repo index consumed by Mihon.
echo "── generate repo index"
python3 "$ROOT/scripts/generate-repo.py" "$ROOT/repo"
echo "✔ done → $ROOT/repo  (push to Hasan72341/nyora-mihon)"
