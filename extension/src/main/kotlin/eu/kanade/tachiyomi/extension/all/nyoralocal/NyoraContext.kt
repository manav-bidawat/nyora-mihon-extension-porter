package eu.kanade.tachiyomi.extension.all.nyoralocal

import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource as LibMangaSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Learns each source's real host from the redirects it actually follows, then
 * rewrites every later request to that host — so a source whose baked-in domain
 * has moved (its old host 3xx-redirects to a new one) keeps working without any
 * hardcoded domain table. Robust because it maps whatever the server points to,
 * at runtime, rather than a static guess.
 *
 * One application interceptor does both halves:
 *  - rewrite:  before proceeding, if this host was seen redirecting elsewhere,
 *              swap in the learned live host (skips the dead hop, and gives the
 *              *Lib / parser interceptors downstream the correct host for their
 *              Referer/Origin/Site-Id headers).
 *  - learn:    OkHttp follows redirects internally, so after proceeding the
 *              final response's host is the live one; if it differs from what we
 *              sent, remember old→live for next time.
 *
 * Process-wide so a host learned by one source is reused everywhere. Image/page
 * fetches go through this same client, so relative page URLs that resolved
 * against the stale domain get rewritten to the live host on the way out too.
 */
object RedirectDomainMapper : Interceptor {
    private val liveHost = ConcurrentHashMap<String, String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val requestedHost = request.url.host
        liveHost[requestedHost]?.takeIf { it != requestedHost }?.let { mapped ->
            request = request.newBuilder()
                .url(request.url.newBuilder().host(mapped).build())
                .build()
        }

        val response = chain.proceed(request)

        val sentHost = request.url.host
        val finalHost = response.request.url.host   // after any followed redirects
        if (finalHost.isNotEmpty() && finalHost != sentHost) {
            liveHost[sentHost] = finalHost
            liveHost[requestedHost] = finalHost
        }
        return response
    }
}

/**
 * On-device host for the native kotatsu-parsers engine, mirroring nyora-shared's
 * KotatsuLoaderContext but for a Mihon extension:
 *
 *  - `httpClient` = the host OkHttp client + [LibApiHeadersInterceptor] (the *Lib
 *    fix, vendored from nyora-shared at build time) + a delegating interceptor
 *    that runs the *bound parser's* own Interceptor (its Site-Id/auth headers) —
 *    kotatsu-parsers' AbstractMangaParser implements Interceptor but its usual
 *    wiring drops it, so we splice it here.
 *  - `evaluateJs`/Bitmap are unsupported (no WebView) — Cloudflare/JS sources fail
 *    gracefully, everything HTTP/Jsoup works.
 *
 * The bound parser is set per-source in [NyoraLocalSource].
 */
class NyoraContext(baseClient: OkHttpClient) : MangaLoaderContext() {

    @Volatile
    private var boundParser: Interceptor? = null

    fun bindParser(instance: Any?) {
        // newParserInstance returns a wrapper whose intercept() ignores the
        // delegate's; unwrap to the real parser so its Site-Id/auth headers run.
        boundParser = unwrapDelegate(instance) as? Interceptor
    }

    private fun unwrapDelegate(instance: Any?): Any? {
        if (instance == null) return null
        return try {
            val f = instance.javaClass.getDeclaredField("delegate")
            f.isAccessible = true
            f.get(instance) ?: instance
        } catch (_: Throwable) {
            instance
        }
    }

    override val cookieJar: CookieJar = baseClient.cookieJar

    // followRedirects/followSslRedirects are forced on so sources that 3xx-redirect
    // (moved/rebranded hosts, http→https, canonical-slug redirects — e.g. Asura)
    // resolve instead of failing on the first hop. Mihon's base client already
    // enables them, but we set them explicitly since this client backs the parser.
    override val httpClient: OkHttpClient = baseClient.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        // First: learn/apply moved-domain redirects, so the header interceptors
        // below and the parser see the live host.
        .addInterceptor(RedirectDomainMapper)
        .addInterceptor(LibApiHeadersInterceptor)
        .addInterceptor(Interceptor { chain -> boundParser?.intercept(chain) ?: chain.proceed(chain.request()) })
        .build()

    override fun getDefaultUserAgent(): String = NYORA_UA

    override fun getConfig(source: LibMangaSource): MangaSourceConfig = OverrideSourceConfig(source)

    @Deprecated("Provide a base url")
    override suspend fun evaluateJs(script: String): String? = null

    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? = null

    override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response = response

    override fun createBitmap(width: Int, height: Int): Bitmap =
        throw UnsupportedOperationException("Bitmap descrambling is not supported in the local extension")

    // Returns each ConfigKey's compiled-in default, EXCEPT ConfigKey.Domain: for a
    // source whose baked-in domain is dead and now redirects to a new host,
    // SourcePatches.DOMAIN_OVERRIDES supplies the current live domain (keyed by the
    // upstream MangaParserSource.name). This is what makes relocated/redirecting
    // sources work — the parser then requests, and absolutizes page URLs against,
    // the live domain. Mirrors nyora-shared / nyora-android SourcePatches wiring.
    private class OverrideSourceConfig(private val source: LibMangaSource) : MangaSourceConfig {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: ConfigKey<T>): T = when (key) {
            is ConfigKey.Domain -> (SourcePatches.DOMAIN_OVERRIDES[source.name] ?: key.defaultValue) as T
            else -> key.defaultValue
        }
    }

    private companion object {
        const val NYORA_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
