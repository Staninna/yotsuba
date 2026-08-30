package dev.stan.yotsuba.core.text

private val ARCHIVE_QUOTELINK = Regex(""">>(\d+)""")
private val ESCAPED_QUOTELINK = Regex("""&gt;&gt;(\d+)""")

/**
 * FoolFuuka's plain comment, marked up the way 4chan would have served it, so
 * [PostHtmlParser] reads it like a live post. The `quotelink` and `quote` class names
 * here are the ones the parser matches on.
 */
fun archiveCommentToHtml(comment: String?): String? {
    if (comment.isNullOrEmpty()) return null
    return comment.split('\n').joinToString("<br>") { line ->
        val escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val linked = escaped.replace(ESCAPED_QUOTELINK) { m ->
            val no = m.groupValues[1]
            """<a href="#p$no" class="quotelink">&gt;&gt;$no</a>"""
        }
        if (line.startsWith(">") && !ARCHIVE_QUOTELINK.matchesAt(line, 0)) {
            """<span class="quote">$linked</span>"""
        } else {
            linked
        }
    }
}
