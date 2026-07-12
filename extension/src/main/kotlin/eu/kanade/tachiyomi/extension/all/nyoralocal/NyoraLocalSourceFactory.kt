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
            .filter { (it.contentType == ContentType.HENTAI) == wantAdult }
            .mapNotNull { runCatching { NyoraLocalSource(it, it.locale.ifBlank { "all" }) }.getOrNull() }
        // MangaFire moved to a new JSON-API site the kotatsu parser can't read;
        // ship a native source per language (non-adult flavor only).
        return kotatsu + if (wantAdult) emptyList() else MangaFireSource.all()
    }
}
