# nyora-mihon-local

Build pipeline for the **fully-local** Nyora Mihon/Tachiyomi extension — the one
that bundles the kotatsu-parsers engine and parses **on-device** (no server),
published to [`Nyora-Manga/nyora-mihon`](https://github.com/Nyora-Manga/nyora-mihon)
as two extensions:

| Extension | pkg | nsfw | contents |
|---|---|---|---|
| **Nyora-Sources** | `…nyoralocal`   | 0 | SFW sources only |
| **Nyora-Sources 18+** | `…nyoralocal18` | 1 | all sources incl. adult |

## Why this repo exists

The original build lived only in an ephemeral scratchpad and was lost; only the
one-off porting scripts survived (kept in [`legacy-port-scripts/`](./legacy-port-scripts)
for reference — do **not** run them, they were trial-and-error).

This is the **usable, reproducible** replacement — an extension **creator**, not
a Mihon fork:

- It borrows the public Source API + `mihonx` build plugins from **official
  upstream Mihon** at build time (a throwaway `git clone`; nothing forked or
  maintained), and builds **only** `:nyora-local-extension` — never the app.
- It **links the engine + fixes to `nyora-shared`** (the same stack the hosted
  helper is built from), so the extension automatically inherits every fix —
  MangaLib headers, MangaEclipse URL absolutization, the parser-Interceptor
  binding, etc. — with no manual porting.

## Build inputs (all pinned / fetched)

- **Engine:** `com.github.clquwu:kotatsu-parsers-redo:59c033ecfd` (Gradle dep — same ref as helper + android)
- **Glue + fixes:** cloned from `Nyora-Manga/nyora-shared` at build time (`NYORA_SHARED_REF`, default `main`):
  `net/LibApiHeaders.kt`, `net/BrowserHeaders.kt`, `net/HelperNetworkSettings.kt`,
  `extension/KotatsuLoaderContext.kt`, `extension/KotatsuParserExtensionService.kt`, `extension/NativeParserCatalog.kt`
- **Android bridge:** an on-device `MangaLoaderContext` (OkHttp + Jsoup; `evaluateJs`/Bitmap stubbed — Cloudflare/JS sources need a WebView, not yet wired) modelled on `nyora-android`.
- **Source list:** the helper catalog (`GET https://api.hasanraza.tech/sources/catalog`) split by `isNsfw` for the two flavors.

## Build

```sh
scripts/build.sh                 # both flavors, default NYORA_SHARED_REF=main
NYORA_SHARED_REF=<sha> scripts/build.sh
```

Outputs `repo/{apk,icon,index.min.json,repo.json}`, ready to push to `nyora-mihon`.

> **Status:** pipeline + glue wiring is in place; the Android extension module
> (`extension/`) is a standard Tachiyomi-extension Gradle build and needs the
> usual build-iteration on a machine with the Android SDK. See `scripts/build.sh`.
