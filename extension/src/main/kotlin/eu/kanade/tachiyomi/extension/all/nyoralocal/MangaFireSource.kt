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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

/**
 * Native MangaFire source. MangaFire relaunched on a new site backed by a clean
 * JSON API (`/api/titles`) — no vrf, no Cloudflare challenge, plain CDN images —
 * which the kotatsu-parsers MangaFire (old `/filter` HTML + vrf + scrambling)
 * no longer matches, so it returns nothing. This is a direct port of the fix in
 * keiyoushi/extensions "Fix MangaFire (#17375)" to the new API. One source per
 * MangaFire language; replaces the kotatsu MANGAFIRE_* sources.
 */
class MangaFireSource(override val lang: String) : HttpSource() {

    override val name = "MangaFire"
    override val baseUrl = "https://mangafire.to"
    override val supportsLatest = true

    // MangaFire's own language code differs from the Tachiyomi lang tag.
    private val langCode: String
        get() = when (lang) {
            "es-419" -> "es-la"
            "pt-BR" -> "pt-br"
            else -> lang
        }

    private val json = Json { ignoreUnknownKeys = true }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json")

    // -- browse ------------------------------------------------------------
    private suspend fun titles(params: List<Pair<String, String>>): MangasPage {
        val url = "$baseUrl/api/titles".toHttpUrl().newBuilder()
            .apply { params.forEach { addQueryParameter(it.first, it.second) } }
            .build()
        val body = client.newCall(GET(url, headers)).await().body.string()
        val data = json.decodeFromString<ApiResponse<MangaDto>>(body)
        return MangasPage(data.items.map { it.toSManga() }, data.meta?.hasNext ?: false)
    }

    override suspend fun getPopularManga(page: Int) = titles(
        listOf("order[views_30d]" to "desc", "page" to "$page", "limit" to "50"),
    )

    override suspend fun getLatestUpdates(page: Int) = titles(
        listOf("order[chapter_updated_at]" to "desc", "page" to "$page", "limit" to "50"),
    )

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = titles(
        buildList {
            if (query.isNotBlank()) add("keyword" to query)
            add("page" to "$page")
            add("limit" to "50")
        },
    )

    // -- details + chapters ------------------------------------------------
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hid = getHid(manga.url)
        val details = json.decodeFromString<MangaDetailsResponse>(
            client.newCall(GET("$baseUrl/api/titles/$hid", headers)).await().body.string(),
        ).data.toSManga()

        val chapterList = mutableListOf<SChapter>()
        var page = 1
        var lastPage: Int
        do {
            val url = "$baseUrl/api/titles/$hid/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("language", langCode)
                .addQueryParameter("sort", "number")
                .addQueryParameter("order", "desc")
                .addQueryParameter("page", "$page")
                .addQueryParameter("limit", "200")
                .build()
            val data = json.decodeFromString<ApiResponse<ChapterDto>>(
                client.newCall(GET(url, headers)).await().body.string(),
            )
            data.items.forEach { chapterList.add(it.toSChapter(manga.url, langCode)) }
            lastPage = data.meta?.lastPage ?: 1
            page++
        } while (page <= lastPage)

        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    // -- pages -------------------------------------------------------------
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = (baseUrl + chapter.url).toHttpUrl().pathSegments
        val last = segments.last()
        val url = if (segments.contains("volume")) {
            "$baseUrl/api/volumes/$last"
        } else {
            "$baseUrl/api/chapters/${last.substringBefore("-")}"
        }
        val data = json.decodeFromString<PagesResponse>(
            client.newCall(GET(url, headers)).await().body.string(),
        )
        return data.data.pages.mapIndexed { i, p -> Page(i, imageUrl = p.url) }
    }

    override suspend fun getImageUrl(page: Page): String = page.imageUrl ?: ""

    // MangaFire manga urls look like /title/<hid>-<slug>.
    private fun getHid(url: String): String {
        val lastPart = url.removeSuffix("/").substringAfterLast("/")
        return when {
            lastPart.contains(".") -> lastPart.substringAfterLast(".")
            lastPart.contains("-") -> lastPart.substringBefore("-")
            else -> lastPart
        }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override val id: Long by lazy {
        var h = 1125899906842597L
        for (ch in "nyora-mangafire/$lang") h = 31 * h + ch.code
        h and Long.MAX_VALUE
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

    companion object {
        // The Tachiyomi lang tags MangaFire ships (keiyoushi build.gradle.kts).
        private val LANGS = listOf("en", "es", "es-419", "fr", "ja", "pt", "pt-BR")
        fun all(): List<MangaFireSource> = LANGS.map { MangaFireSource(it) }
    }
}

// -- API DTOs (ported from keiyoushi/extensions mangafire Dto.kt) -----------
@Serializable
private class ApiResponse<T>(val items: List<T> = emptyList(), val meta: ApiMeta? = null)

@Serializable
private class ApiMeta(val lastPage: Int = 1, val hasNext: Boolean = false)

@Serializable
private class MangaDto(
    private val hid: String,
    private val slug: String? = null,
    private val title: String,
    private val poster: PosterDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$hid${slug?.let { "-$it" } ?: ""}"
        title = this@MangaDto.title
        thumbnail_url = poster?.large ?: poster?.medium ?: poster?.small
        initialized = false
    }
}

@Serializable
private class MangaDetailsResponse(val data: MangaDetailsDto)

@Serializable
private class MangaDetailsDto(
    private val hid: String,
    private val slug: String? = null,
    private val title: String,
    private val type: String? = null,
    private val status: String? = null,
    private val poster: PosterDto? = null,
    private val synopsisHtml: String? = null,
    private val authors: List<EntityDto>? = null,
    private val artists: List<EntityDto>? = null,
    private val genres: List<EntityDto>? = null,
    private val themes: List<EntityDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$hid${slug?.let { "-$it" } ?: ""}"
        title = this@MangaDetailsDto.title
        thumbnail_url = poster?.large ?: poster?.medium ?: poster?.small
        author = authors?.joinToString { it.title }
        artist = artists?.joinToString { it.title }
        description = synopsisHtml?.let { Jsoup.parseBodyFragment(it).text() }
        genre = buildList {
            type?.replaceFirstChar { it.uppercase() }?.let { add(it) }
            genres?.forEach { add(it.title) }
            themes?.forEach { add(it.title) }
        }.joinToString()
        status = when (this@MangaDetailsDto.status?.lowercase()) {
            "releasing" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            "on_hiatus" -> SManga.ON_HIATUS
            "discontinued" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
private class PosterDto(val small: String? = null, val medium: String? = null, val large: String? = null)

@Serializable
private class EntityDto(val title: String)

@Serializable
private class ChapterDto(
    private val id: Int,
    private val number: Float,
    private val name: String? = null,
    private val createdAt: Long? = null,
    val type: String? = null,
) {
    fun toSChapter(mangaUrl: String, langCode: String): SChapter = SChapter.create().apply {
        url = "$mangaUrl/$id-chapter-${number.toString().removeSuffix(".0")}-$langCode"
        chapter_number = number
        name = buildString {
            append("Ch. ")
            append(number.toString().removeSuffix(".0"))
            if (!this@ChapterDto.name.isNullOrBlank()) {
                append(" - ")
                append(this@ChapterDto.name)
            }
        }
        scanlator = type ?: "Unknown"
        date_upload = createdAt?.times(1000L) ?: 0L
    }
}

@Serializable
private class PagesResponse(val data: ChapterDataDto)

@Serializable
private class ChapterDataDto(val pages: List<PageDto>)

@Serializable
private class PageDto(val url: String)
