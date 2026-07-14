package eu.kanade.tachiyomi.extension.all.nyoralocal

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.time.Instant

/**
 * Native ToonDex source (ex-Toonily.me → toondex.io → toontop.io). ToonDex
 * relaunched on the MangaBuddy reader-network codebase backed by a clean JSON
 * API at `api.toontop.io`; the old kotatsu Madtheme parser (scraping the
 * WordPress site) no longer matches, so `TOONILY_ME` returns nothing. This ports
 * nyora-shared's ToonDexExtensionService to a Tachiyomi HttpSource and replaces
 * the kotatsu TOONILY_ME source.
 *
 * Endpoints (all on api.toontop.io):
 *   GET /titles/search?sort=popular|latest&page=N   and   ?q=<query>&page=N   → browse/search
 *   GET /titles/{id}                                                          → details
 *   GET /titles/{id}/chapters?page=N                                          → chapter list
 * Page images live in the site's Next.js data route (buildId rotates per deploy).
 */
class ToonDexSource : HttpSource() {

    override val name = "ToonDex"
    override val baseUrl = "https://toontop.io"
    override val lang = "all"
    override val supportsLatest = true

    private val apiUrl = "https://api.toontop.io"
    private val json = Json { ignoreUnknownKeys = true }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json")

    // -- browse ------------------------------------------------------------
    private suspend fun browse(params: List<Pair<String, String>>): MangasPage {
        val url = "$apiUrl/titles/search".toHttpUrl().newBuilder()
            .apply { params.forEach { addQueryParameter(it.first, it.second) } }
            .build()
        val data = json.decodeFromString<Envelope<SearchData>>(
            client.newCall(GET(url, headers)).await().body.string(),
        ).data
        return MangasPage(data.items.map { it.toSManga() }, data.pagination?.hasNext ?: false)
    }

    override suspend fun getPopularManga(page: Int) =
        browse(listOf("sort" to "popular", "page" to "$page"))

    override suspend fun getLatestUpdates(page: Int) =
        browse(listOf("sort" to "latest", "page" to "$page"))

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) =
        if (query.isBlank()) {
            browse(listOf("sort" to "popular", "page" to "$page"))
        } else {
            browse(listOf("q" to query, "page" to "$page"))
        }

    // -- details + chapters ------------------------------------------------
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.trim('/')
        val details = json.decodeFromString<Envelope<TitleData>>(
            client.newCall(GET("$apiUrl/titles/$id", headers)).await().body.string(),
        ).data.title.toSManga()

        val chapterList = mutableListOf<SChapter>()
        var page = 1
        var hasNext: Boolean
        do {
            val url = "$apiUrl/titles/$id/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("page", "$page")
                .build()
            val data = json.decodeFromString<Envelope<ChaptersData>>(
                client.newCall(GET(url, headers)).await().body.string(),
            ).data
            data.chapters.forEach { chapterList.add(it.toSChapter(id)) }
            hasNext = data.pagination?.hasNext ?: false
            page++
        } while (hasNext && page <= 200)

        // API returns chapters newest-first — Tachiyomi's default sort wants index 0 = newest, so keep as-is.
        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    // -- pages -------------------------------------------------------------
    // The public /images API returns only preview images to anonymous callers; the
    // FULL page list is in the Next.js data route (pageProps.initialChapter.images).
    // chapter.url is the "<mangaSlug>/<chapterSlug>" path. buildId rotates per deploy,
    // so a 404 → refresh it once.
    @Volatile
    private var cachedBuildId: String? = null

    private suspend fun buildId(force: Boolean = false): String {
        cachedBuildId?.takeUnless { force }?.let { return it }
        val html = client.newCall(GET("$baseUrl/", headers)).await().body.string()
        val id = Regex("\"buildId\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: error("ToonDex buildId not found")
        cachedBuildId = id
        return id
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = chapter.url.trim('/')
        suspend fun fetch(bid: String): List<String> =
            json.decodeFromString<NextData>(
                client.newCall(GET("$baseUrl/_next/data/$bid/$path.json", headers)).await().body.string(),
            ).pageProps.initialChapter.images
        val images = runCatching { fetch(buildId()) }.getOrElse { fetch(buildId(force = true)) }
        return images.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override suspend fun getImageUrl(page: Page): String = page.imageUrl ?: ""

    override fun getMangaUrl(manga: SManga): String = baseUrl
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url.trim('/')}"

    override val id: Long by lazy {
        var h = 1125899906842597L
        for (ch in "nyora-toondex") h = 31 * h + ch.code
        h and Long.MAX_VALUE
    }

    // model mappers -------------------------------------------------------
    private fun TitleItem.toSManga() = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = cover
        initialized = false
    }

    private fun TitleFull.toSManga() = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = cover
        author = authors?.joinToString { it.name }
        description = summary?.let { Jsoup.parseBodyFragment(it).text() }
        genre = buildList {
            genres?.forEach { add(it.name) }
            themes?.forEach { add(it.name) }
        }.distinct().joinToString()
        status = when (this@toSManga.status?.lowercase()) {
            "ongoing", "releasing" -> SManga.ONGOING
            "completed", "finished" -> SManga.COMPLETED
            "hiatus", "on hold" -> SManga.ON_HIATUS
            "cancelled", "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    private fun ChapterItem.toSChapter(mangaId: String): SChapter = SChapter.create().apply {
        url = (this@toSChapter.url ?: "$mangaId/${this@toSChapter.id}").trim('/')
        chapter_number = number ?: -1f
        name = this@toSChapter.name
            ?: number?.let { "Chapter ${it.toString().removeSuffix(".0")}" }
            ?: "Chapter"
        date_upload = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
    }

    // HttpSource requires these; everything goes through the JSON API above.
    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

// -- API DTOs (ported from nyora-shared ToonDexExtensionService) -------------
@Serializable
private class Envelope<T>(val data: T)

@Serializable
private class Pagination(@SerialName("has_next") val hasNext: Boolean = false)

@Serializable
private class SearchData(val items: List<TitleItem> = emptyList(), val pagination: Pagination? = null)

@Serializable
private class TitleItem(
    val id: String,
    val url: String,
    val name: String,
    val cover: String? = null,
)

@Serializable
private class TitleData(val title: TitleFull)

@Serializable
private class TitleFull(
    val id: String,
    val name: String,
    val cover: String? = null,
    val summary: String? = null,
    val status: String? = null,
    val authors: List<NamedRef>? = null,
    val genres: List<NamedRef>? = null,
    val themes: List<NamedRef>? = null,
)

@Serializable
private class NamedRef(val name: String)

@Serializable
private class ChaptersData(val chapters: List<ChapterItem> = emptyList(), val pagination: Pagination? = null)

@Serializable
private class ChapterItem(
    val id: String,
    val url: String? = null,
    val name: String? = null,
    val number: Float? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private class NextData(val pageProps: NextPageProps)

@Serializable
private class NextPageProps(val initialChapter: NextChapter)

@Serializable
private class NextChapter(val images: List<String> = emptyList())
