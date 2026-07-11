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

    override val httpClient: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(LibApiHeadersInterceptor)
        .addInterceptor(Interceptor { chain -> boundParser?.intercept(chain) ?: chain.proceed(chain.request()) })
        .build()

    override fun getDefaultUserAgent(): String = NYORA_UA

    override fun getConfig(source: LibMangaSource): MangaSourceConfig = DefaultSourceConfig

    @Deprecated("Provide a base url")
    override suspend fun evaluateJs(script: String): String? = null

    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? = null

    override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response = response

    override fun createBitmap(width: Int, height: Int): Bitmap =
        throw UnsupportedOperationException("Bitmap descrambling is not supported in the local extension")

    private object DefaultSourceConfig : MangaSourceConfig {
        override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
    }

    private companion object {
        const val NYORA_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
