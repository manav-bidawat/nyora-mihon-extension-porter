# nyora-mihon-extension-porter

Build pipeline that **creates** the fully-local Nyora extension for
[Mihon](https://mihon.app) / Tachiyomi — it bundles the native
[kotatsu-parsers](https://github.com/kotatsu-redo/kotatsu-parsers-redo) engine and
parses **on-device** (no server). This is the **creator**; the built artifacts are
published to the store repo [`Nyora-Manga/nyora-mihon`](https://github.com/Nyora-Manga/nyora-mihon),
which Mihon points at.

It produces two extensions:

| Extension | package | contents |
|---|---|---|
| **Nyora-Sources** | `…extension.all.nyoralocal` | SFW sources |
| **Nyora-Sources 18+** | `…extension.all.nyoralocal18` | adult sources |

## How it works

This is an extension **creator, not a Mihon fork**. `scripts/build.sh`:

1. **Clones `Nyora-Manga/nyora-shared`** and vendors the shared glue into the module:
   the `*Lib` header fix (`LibApiHeaders.kt`) and `SourcePatches.kt`
   (`DOMAIN_OVERRIDES` / `TITLE_OVERRIDES` / `DEAD_SOURCES`). So domain moves,
   rebrands and dead-source pruning flow in automatically on every build.
2. **Clones upstream Mihon** (`v0.20.1`, throwaway) — only for its public Source API
   + `mihonx` build plugins. Builds **only** `:nyora-local-extension`, never the app.
3. **Assembles + signs** both flavors with the stable key (`signing/nyora.keystore`),
   then runs `scripts/generate-repo.py` to emit `repo/`.

**Engine:** `com.github.clquwu:kotatsu-parsers-redo` — the ref is read from
`nyora-shared` at build time, so the extension's sources always match the web/helper.

**Sources:** every `MangaParserSource` **minus** `SourcePatches.DEAD_SOURCES`
(health-probed — DNS-dead/parked hidden, Cloudflare-protected kept), split by content
type into the two flavors, **plus** native ports for sites that moved to a JSON API
(MangaFire, ToonDex).

**Cloudflare:** requests run through Mihon's own `CloudflareInterceptor` (its in-app
WebView solver). Classic challenges are solved there; managed/Turnstile challenges
that bind clearance to the browser fingerprint are a known limitation of an
OkHttp-based extension.

## Build locally

```sh
scripts/build.sh
# overrides:
NYORA_SHARED_REF=<sha> MIHON_REF=<tag> scripts/build.sh
```

Requires the Android SDK (`ANDROID_HOME`) and `signing/nyora.keystore` (gitignored).
Outputs `repo/{apk,icon,index.min.json,repo.json}`.

## Publish (CI/CD)

Push to the **`deploy`** branch → GitHub Actions
([`.github/workflows/deploy.yml`](.github/workflows/deploy.yml)) builds + signs both
flavors, **verifies the signing fingerprint** matches the trusted key (so Mihon's
repo auto-trust can't silently break), and publishes `repo/` to `Nyora-Manga/nyora-mihon`.

```sh
git commit -am "…"       # on master
git branch -f deploy master && git push origin deploy   # → build → sign → publish
```

Repo secrets:

| Secret | Purpose |
|---|---|
| `SIGNING_KEYSTORE_B64` | base64 of the stable signing key (cert SHA-256 `8321e917…306d0f1` → Mihon repo auto-trust) |
| `PUBLISHER_TOKEN` | Contents:write on `Nyora-Manga/nyora-mihon` |

Bump `versionCode`/`versionName` in `extension/build.gradle.kts` so Mihon offers the update
(`scripts/generate-repo.py` reads both from the built APKs). **Contract:** `versionName` must be
`"<tachiyomix.extensionLib>.<patch>"` — Mihon derives the repo libVersion as
`versionName.substringBeforeLast('.')` and flags a *permanent* update if it exceeds the APK's
manifest `extensionLib` (the generator fails the build on that drift).

## Layout

```
extension/                the Tachiyomi extension module (kotatsu-parsers bridge)
scripts/build.sh          the build pipeline
scripts/generate-repo.py  emits index.min.json + repo.json (fingerprint auto-detected)
signing/                  stable signing key (gitignored)
repo/                     build output, published to nyora-mihon
legacy-port-scripts/      early one-off scripts — reference only, do not run
```
