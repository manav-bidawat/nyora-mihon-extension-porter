package eu.kanade.tachiyomi.extension.all.nyoralocal

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

/**
 * Exposes every on-device kotatsu-parsers source as a Tachiyomi source. Two
 * flavors, split by content type (baked in via BuildConfig.NYORA_NSFW):
 *   - Nyora-Sources      → non-adult sources
 *   - Nyora-Sources 18+  → adult (HENTAI) sources
 */
class NyoraLocalSourceFactory : SourceFactory {
    override fun createSources(): List<Source> {
        val wantAdult = BuildConfig.NYORA_NSFW
        val kotatsu = MangaParserSource.entries
            .filter { it.name !in SourcePatches.DEAD_SOURCES }        // hide dead-domain sources
            .filter { !it.name.startsWith("MANGAFIRE") }             // native override (new JSON API)
            .filter { it.name != "TOONILY_ME" }                     // native override (ToonDex, new JSON API)
            .filter { (it.contentType == ContentType.HENTAI) == wantAdult }
            .mapNotNull { runCatching { NyoraLocalSource(it, it.locale.ifBlank { "all" }) }.getOrNull() }
        // Sources whose sites relaunched on new JSON APIs the kotatsu parsers
        // can't read — shipped as native ports instead:
        //   • MangaFire → per-language (non-adult flavor only)
        //   • ToonDex (ex-Toonily.me → toontop.io) → single source, both flavors
        return kotatsu + ToonDexSource() + if (wantAdult) emptyList() else MangaFireSource.all()
    }
}
