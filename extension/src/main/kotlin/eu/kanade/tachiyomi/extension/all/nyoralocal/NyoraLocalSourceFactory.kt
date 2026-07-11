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
        return MangaParserSource.entries
            .filter { it != MangaParserSource.DUMMY }
            .filter { (it.contentType == ContentType.HENTAI) == wantAdult }
            .map { NyoraLocalSource(it, it.locale.ifBlank { "all" }) }
    }
}
