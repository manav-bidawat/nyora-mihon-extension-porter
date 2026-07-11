package eu.kanade.tachiyomi.extension.all.nyoralocal

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import uy.kohesive.injekt.injectLazy
import org.koitharu.kotatsu.parsers.model.Manga as LibManga
import org.koitharu.kotatsu.parsers.model.MangaChapter as LibChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter as LibFilter
import org.koitharu.kotatsu.parsers.model.MangaPage as LibPage

/**
 * One Tachiyomi source backed by an on-device kotatsu-parsers parser. Browsing,
 * details, chapters and pages all run through the native parser; page image URLs
 * are absolutized against the source domain (the MangaEclipse fix). All heavy
 * network/HTML work is the parser's; this class just maps kotatsu <-> Tachiyomi.
 */
class NyoraLocalSource(
    private val parserSource: MangaParserSource,
    override val lang: String,
) : HttpSource() {

    override val name: String = parserSource.title.ifBlank { parserSource.name }
    override val supportsLatest = true

    private val network: NetworkHelper by injectLazy()
    private val context = NyoraContext(network.client)
    private val parser = context.newParserInstance(parserSource).also { context.bindParser(it) }

    override val baseUrl: String = runCatching { "https://" + parser.domain }.getOrDefault("")
    override val client get() = context.httpClient

    // -- browse ------------------------------------------------------------
    private suspend fun list(page: Int, order: SortOrder, query: String?): MangasPage {
        val filter = if (query.isNullOrBlank()) LibFilter.EMPTY else LibFilter(query = query)
        val entries = parser.getListPage(page, order, filter)
        return MangasPage(entries.map { it.toSManga() }, hasNextPage = entries.isNotEmpty())
    }

    override suspend fun getPopularManga(page: Int): MangasPage = list(page, SortOrder.POPULARITY, null)
    override suspend fun getLatestUpdates(page: Int): MangasPage = list(page, SortOrder.UPDATED, null)
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        list(page, SortOrder.RELEVANCE, query)

    // -- details / chapters ------------------------------------------------
    override suspend fun getMangaDetails(manga: SManga): SManga {
        val full = parser.getDetails(manga.toLib())
        return full.toSManga()
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val full = parser.getDetails(manga.toLib())
        return (full.chapters ?: emptyList()).mapIndexed { i, c -> c.toSChapter(i) }
    }

    // -- pages -------------------------------------------------------------
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val lib = LibChapter(
            id = chapter.url.hashCode().toLong(),
            title = chapter.name, number = chapter.chapter_number, volume = 0,
            url = chapter.url, scanlator = chapter.scanlator, uploadDate = 0, branch = null,
            source = parserSource,
        )
        return parser.getPages(lib).mapIndexed { i, p ->
            val raw = p.url.ifBlank { parser.getPageUrl(p) }
            Page(i, "", raw.toAbsoluteUrl(parser.domain))   // absolutize (MangaEclipse fix)
        }
    }

    override fun imageUrlParse(response: okhttp3.Response): String = throw UnsupportedOperationException()
    override suspend fun getImageUrl(page: Page): String = page.imageUrl ?: ""

    // -- kotatsu <-> tachiyomi mapping ------------------------------------
    private fun LibManga.toSManga() = SManga.create().also { s ->
        s.url = url
        s.title = title
        s.thumbnail_url = largeCoverUrl ?: coverUrl
        s.author = authors.joinToString().ifBlank { null }
        s.description = description
        s.genre = tags.joinToString { it.title }
        s.status = when (state) {
            MangaState.ONGOING -> SManga.ONGOING
            MangaState.FINISHED -> SManga.COMPLETED
            MangaState.ABANDONED -> SManga.CANCELLED
            MangaState.PAUSED -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun SManga.toLib() = LibManga(
        id = url.hashCode().toLong(), title = title, altTitles = emptySet(), url = url,
        publicUrl = url, rating = -1f, contentRating = null, coverUrl = thumbnail_url,
        tags = emptySet(), state = null, authors = emptySet(), source = parserSource,
    )

    private fun LibChapter.toSChapter(index: Int) = SChapter.create().also { c ->
        c.url = url
        c.name = title ?: "Chapter ${number.takeIf { it > 0 } ?: (index + 1)}"
        c.chapter_number = number
        c.scanlator = scanlator
        c.date_upload = uploadDate
    }

    // Tachiyomi HttpSource requires these; browsing goes through the parser above.
    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: okhttp3.Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: okhttp3.Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: okhttp3.Response): MangasPage = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: okhttp3.Response): SManga = throw UnsupportedOperationException()
    override fun chapterListParse(response: okhttp3.Response): List<SChapter> = throw UnsupportedOperationException()
    override fun pageListParse(response: okhttp3.Response): List<Page> = throw UnsupportedOperationException()

    override val id: Long by lazy {
        // stable per parser+lang, namespaced so it never collides with other sources
        val key = "nyoralocal/${parserSource.name}/$lang"
        var h = 1125899906842597L
        for (ch in key) h = 31 * h + ch.code
        h and Long.MAX_VALUE
    }
}
