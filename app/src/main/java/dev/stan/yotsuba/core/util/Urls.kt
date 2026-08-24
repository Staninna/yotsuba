package dev.stan.yotsuba.core.util

object Urls {
    const val API_BASE = "https://a.4cdn.org/"
    private const val MEDIA_BASE = "https://i.4cdn.org"

    fun thumbnail(board: String, tim: Long) = "$MEDIA_BASE/$board/${tim}s.jpg"
    fun fullMedia(board: String, tim: Long, ext: String) = "$MEDIA_BASE/$board/$tim$ext"
    fun threadWebUrl(board: String, no: Long) = "https://boards.4chan.org/$board/thread/$no"

    /** Internal-link routing by URL shape (D26). */
    sealed interface InternalLink {
        data class Catalog(val board: String, val searchQuery: String? = null) : InternalLink
        data class Thread(val board: String, val threadNo: Long, val postNo: Long? = null) : InternalLink
    }

    private val INTERNAL_HOSTS = setOf("boards.4chan.org", "boards.4channel.org")
    private val BOARD_PATH = Regex("""/(\w+)/(?:catalog)?""")
    private val THREAD_PATH = Regex("""/(\w+)/thread/(\d+)(?:/[^#]*)?""")

    /** Returns the in-app destination for a 4chan board link, or null → external path. */
    fun parseInternal(url: String): InternalLink? {
        val normalized = if (url.startsWith("//")) "https:$url" else url
        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return null
        if (uri.host !in INTERNAL_HOSTS) return null
        val path = uri.path.orEmpty().trimEnd('/')
        THREAD_PATH.matchEntire(path)?.let { m ->
            val postNo = uri.fragment?.removePrefix("p")?.toLongOrNull()
            return InternalLink.Thread(m.groupValues[1], m.groupValues[2].toLong(), postNo)
        }
        if (path.endsWith("/catalog")) {
            val board = path.removeSuffix("/catalog").trim('/')
            val search = uri.fragment?.removePrefix("s=")?.replace('_', ' ')
            if (board.isNotEmpty()) return InternalLink.Catalog(board, search)
        }
        BOARD_PATH.matchEntire("$path/")?.let { m ->
            return InternalLink.Catalog(m.groupValues[1])
        }
        return null
    }

    fun domainOf(url: String): String? =
        runCatching { java.net.URI(if (url.startsWith("//")) "https:$url" else url).host }.getOrNull()
}
