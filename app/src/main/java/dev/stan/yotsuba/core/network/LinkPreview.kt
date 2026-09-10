package dev.stan.yotsuba.core.network

import dev.stan.yotsuba.core.text.PostHtmlParser
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The Open Graph title, site name and description of a page, for the external-link dialog.
 * Reads at most the first [MAX_BYTES] of the page, never an image, and remembers every
 * answer (including "nothing found") for the life of the process.
 */
@Singleton
class LinkPreview @Inject constructor(client: OkHttpClient) {

    data class Preview(val title: String?, val siteName: String?, val description: String?) {
        val isEmpty: Boolean get() = title == null && siteName == null && description == null
    }

    private val client = client.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, Preview>()

    suspend fun fetch(url: String): Preview = cache[url] ?: withContext(Dispatchers.IO) {
        try {
            load(url)
        } catch (e: IOException) {
            EMPTY
        }
    }.also { cache[url] = it }

    private fun load(url: String): Preview {
        val httpUrl = url.toHttpUrlOrNull() ?: return EMPTY
        val request = Request.Builder().url(httpUrl).header("Accept", "text/html").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return EMPTY
            val source = response.body.source()
            source.request(MAX_BYTES)
            // ponytail: assumes UTF-8; a page in another charset shows mangled accents at worst.
            return parse(source.buffer.readUtf8(minOf(source.buffer.size, MAX_BYTES)))
        }
    }

    companion object {
        private const val MAX_BYTES = 64L * 1024
        private const val TIMEOUT_SECONDS = 5L
        private val EMPTY = Preview(null, null, null)

        private val META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTR = Regex("""(property|name|content)\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        private val TITLE = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        /** `og:*` meta tags in either attribute order, then `<title>` when there is no `og:title`. */
        fun parse(html: String): Preview {
            val meta = HashMap<String, String>()
            for (tag in META.findAll(html)) {
                val attrs = ATTR.findAll(tag.value).associate { m ->
                    m.groupValues[1].lowercase() to m.groupValues[2].ifEmpty { m.groupValues[3] }
                }
                val key = attrs["property"] ?: attrs["name"] ?: continue
                val content = attrs["content"] ?: continue
                meta.putIfAbsent(key.lowercase(), content)
            }
            return Preview(
                title = text(meta["og:title"]) ?: text(TITLE.find(html)?.groupValues?.get(1)),
                siteName = text(meta["og:site_name"]),
                description = text(meta["og:description"] ?: meta["description"]),
            )
        }

        /** Entities decoded and tags dropped by the post parser, which already knows both. */
        private fun text(raw: String?): String? =
            raw?.let { PostHtmlParser.parse(it).plainText.trim() }?.takeIf { it.isNotEmpty() }
    }
}
