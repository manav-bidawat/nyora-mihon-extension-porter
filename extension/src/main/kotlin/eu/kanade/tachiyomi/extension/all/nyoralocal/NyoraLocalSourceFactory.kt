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

        // FULLY resilient: Mihon aborts the ENTIRE extension (0 sources) if
        // createSources throws, so EVERY per-source access — name/contentType/
        // locale/constructor AND the native ports — is guarded. One bad parser
        // must never take down the whole list.
        val kotatsu = runCatching {
            MangaParserSource.entries.mapNotNull { entry ->
                runCatching {
                    val name = entry.name
                    when {
                        name in SourcePatches.DEAD_SOURCES -> null            // dead-domain
                        name.startsWith("MANGAFIRE") -> null                  // native override
                        name == "TOONILY_ME" -> null                          // native override (ToonDex)
                        (entry.contentType == ContentType.HENTAI) != wantAdult -> null
                        else -> NyoraLocalSource(entry, entry.locale.ifBlank { "all" })
                    }
                }.getOrNull()
            }
        }.getOrDefault(emptyList())

        // Native ports for sites that relaunched on new JSON APIs the kotatsu
        // parsers can't read (also guarded so a construction failure can't abort).
        val native = buildList {
            runCatching { add(ToonDexSource()) }                              // ex-Toonily.me → toontop.io
            if (!wantAdult) runCatching { addAll(MangaFireSource.all()) }     // per-language
        }

        return kotatsu + native
    }
}
