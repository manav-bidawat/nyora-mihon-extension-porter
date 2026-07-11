#!/usr/bin/env bash
#
# Build the fully-local Nyora Mihon extension (SFW + 18+) from the latest
# nyora-shared. The extension bundles kotatsu-parsers-redo and parses on-device.
#
# It builds inside a Mihon fork checkout (which provides :source-api, :core:common
# and the `mihonx` build plugins); our module is copied in as :nyora-local-extension.
#
# Usage:  scripts/build.sh
#         NYORA_SHARED_REF=<sha> MIHON_REF=<ref> scripts/build.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NYORA_SHARED_REF="${NYORA_SHARED_REF:-main}"
MIHON_REF="${MIHON_REF:-main}"
PARSERS_REF="59c033ecfd"
WORK="$ROOT/.work"
PKG_DIR="eu/kanade/tachiyomi/extension/all/nyoralocal"

echo "▶ nyora-mihon-local  (nyora-shared@$NYORA_SHARED_REF, mihon@$MIHON_REF, parsers@$PARSERS_REF)"
rm -rf "$WORK"; mkdir -p "$WORK" "$ROOT/repo/apk" "$ROOT/repo/icon"

# 1) Fetch nyora-shared and vendor the *Lib fix (LibApiHeaders) into the module,
#    rewriting its package. This is the "fetch from nyora-shared" step: the *Lib
#    (MangaLib/HentaiLib) header+summary fix stays in sync automatically. The
#    parser-Interceptor binding + MangaEclipse absolutize are inlined in the
#    bridge (NyoraContext / NyoraLocalSource), mirroring nyora-shared.
echo "── vendor LibApiHeaders from nyora-shared"
git clone --quiet https://github.com/Hasan72341/nyora-shared.git "$WORK/nyora-shared"
( cd "$WORK/nyora-shared" && git checkout --quiet "$NYORA_SHARED_REF" 2>/dev/null || true )
LIB="$WORK/nyora-shared/src/jvmMain/kotlin/com/nyora/hasan72341/shared/net/LibApiHeaders.kt"
sed 's/^package .*/package eu.kanade.tachiyomi.extension.all.nyoralocal/' "$LIB" \
  > "$ROOT/extension/src/main/kotlin/$PKG_DIR/LibApiHeaders.kt"
( cd "$WORK/nyora-shared" && git rev-parse HEAD ) > "$ROOT/extension/nyora-shared.ref"

# 2) Fetch the Mihon fork (provides source-api + mihonx plugins).
echo "── fetch Mihon fork"
git clone --quiet --depth 1 --branch "$MIHON_REF" https://github.com/Hasan72341/mihon.git "$WORK/mihon"

# 3) Wire our module into the fork.
echo "── wire :nyora-local-extension into the fork"
cp -R "$ROOT/extension" "$WORK/mihon/nyora-local-extension"
printf '\ninclude(":nyora-local-extension")\n' >> "$WORK/mihon/settings.gradle.kts"

# 4) Build both flavors.
build_flavor() {  # <pkgSuffix> <name> <nsfw>
    local suffix="$1" name="$2" nsfw="$3"
    echo "── assemble $name  (nsfw=$nsfw)"
    ( cd "$WORK/mihon" && ./gradlew --quiet :nyora-local-extension:assembleRelease \
        -PnyoraPkgSuffix="$suffix" -PnyoraName="$name" -PnyoraNsfw="$nsfw" -PparsersRef="$PARSERS_REF" )
    cp "$WORK/mihon/nyora-local-extension/build/outputs/apk/release/nyora-local-extension-release.apk" \
       "$ROOT/repo/apk/eu.kanade.tachiyomi.extension.all.nyora${suffix}.apk"
}
build_flavor "local"   "Nyora-Sources"     0
build_flavor "local18" "Nyora-Sources 18+" 1

# 5) Emit the Mihon repo index.
python3 "$ROOT/scripts/generate-repo.py" "$ROOT/repo"
echo "✔ done → $ROOT/repo  (push to Hasan72341/nyora-mihon)"
