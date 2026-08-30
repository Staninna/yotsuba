package dev.stan.yotsuba.core.text

import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostStyle
import dev.stan.yotsuba.domain.model.PostText

/**
 * Hand-written tokenizer for 4chan post HTML (D10).
 *
 * Contract: any tag the tokenizer does not recognise is dropped and its text content kept.
 * Raw markup never reaches the UI.
 */
object PostHtmlParser {

    /** One open element; unknown-class spans and bare anchors carry null style/annotation. */
    private class Frame(val name: String, val style: PostStyle?, val annotation: PostAnnotation?)

    fun parse(html: String?): PostText {
        if (html.isNullOrEmpty()) return PostText.Empty
        val segments = mutableListOf<PostSegment>()
        val stack = ArrayDeque<Frame>()
        var spoilerCounter = 0
        val text = StringBuilder()

        fun flush() {
            if (text.isNotEmpty()) {
                val annotation = stack.lastOrNull { it.annotation != null }?.annotation
                segments += PostSegment(
                    text.toString(),
                    stack.mapNotNull { it.style }.toSet(),
                    if (annotation is PostAnnotation.Deadlink) deadlinkFor(text) else annotation,
                    stack.firstNotNullOfOrNull { (it.annotation as? PostAnnotation.Spoiler)?.id },
                )
                text.clear()
            }
        }

        var i = 0
        val n = html.length
        while (i < n) {
            val c = html[i]
            when (c) {
                '<' -> {
                    val end = html.indexOf('>', i + 1)
                    if (end == -1) { text.append(c); i++; continue }
                    val rawTag = html.substring(i + 1, end)
                    i = end + 1
                    val closing = rawTag.startsWith("/")
                    val body = if (closing) rawTag.substring(1) else rawTag
                    val name = body.takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
                    // b/strong and i/em open and close interchangeably.
                    val canonical = when (name) {
                        "strong" -> "b"
                        "em" -> "i"
                        else -> name
                    }
                    if (!closing) {
                        when (canonical) {
                            "br" -> text.append('\n')
                            "wbr" -> text.append('​')
                            "s" -> {
                                flush()
                                stack.addLast(Frame("s", PostStyle.SPOILER, PostAnnotation.Spoiler(spoilerCounter++)))
                            }
                            "b" -> { flush(); stack.addLast(Frame("b", PostStyle.BOLD, null)) }
                            "i" -> { flush(); stack.addLast(Frame("i", PostStyle.ITALIC, null)) }
                            "u" -> { flush(); stack.addLast(Frame("u", PostStyle.UNDERLINE, null)) }
                            "pre" -> { flush(); stack.addLast(Frame("pre", PostStyle.CODE, null)) }
                            "span" -> {
                                flush()
                                val classes = classTokens(body)
                                stack.addLast(
                                    when {
                                        "quote" in classes -> Frame("span", PostStyle.GREENTEXT, null)
                                        "deadlink" in classes -> Frame("span", PostStyle.DEADLINK, PostAnnotation.Deadlink())
                                        "sjis" in classes -> Frame("span", PostStyle.SJIS, null)
                                        "math" in classes -> Frame("span", PostStyle.MATH, null)
                                        // Unknown span class: content kept, no style.
                                        else -> Frame("span", null, null)
                                    },
                                )
                            }
                            "a" -> {
                                flush()
                                val href = HREF_ATTR.find(body)?.groupValues?.get(1)
                                val annotation = when {
                                    href == null -> null
                                    "quotelink" in classTokens(body) -> parseQuotelink(href)
                                    else -> PostAnnotation.Link(Urls.absolute(href))
                                }
                                stack.addLast(Frame("a", null, annotation))
                            }
                            // Unknown tag: dropped, content kept.
                            else -> {}
                        }
                    } else if (canonical in FRAMED_TAGS) {
                        flush()
                        // Pop the innermost matching frame; unmatched closes are no-ops.
                        val idx = stack.indexOfLast { it.name == canonical }
                        if (idx >= 0) stack.removeAt(idx)
                    }
                }
                '&' -> {
                    val semi = html.indexOf(';', i + 1)
                    if (semi != -1 && semi - i <= 10) {
                        val entity = html.substring(i + 1, semi)
                        val decoded = decodeEntity(entity)
                        if (decoded != null) {
                            text.append(decoded)
                            i = semi + 1
                            continue
                        }
                    }
                    text.append(c); i++
                }
                else -> { text.append(c); i++ }
            }
        }
        flush()
        return PostText(segments)
    }

    private fun parseQuotelink(href: String): PostAnnotation {
        // "#p109582912" is same thread; "/g/thread/109593884#p109593884" is cross-thread.
        if (href.startsWith("#p")) {
            val no = href.removePrefix("#p").toLongOrNull()
            if (no != null) return PostAnnotation.QuotelinkSameThread(no)
        }
        val m = CROSS_THREAD.matchEntire(href)
        if (m != null) {
            return PostAnnotation.QuotelinkCrossThread(
                board = m.groupValues[1],
                threadNo = m.groupValues[2].toLong(),
                postNo = m.groupValues[3].takeIf { it.isNotEmpty() }?.toLong(),
            )
        }
        return PostAnnotation.Link(Urls.absolute(href))
    }

    /** The span's text is what says which post died: 4chan writes `>>123`, nothing else. */
    private fun deadlinkFor(text: CharSequence): PostAnnotation.Deadlink =
        PostAnnotation.Deadlink(DEAD_QUOTE.matchEntire(text.trim())?.groupValues?.get(1)?.toLongOrNull())

    /** Whitespace-separated class names; attribute order is not assumed (D10). */
    private fun classTokens(tagBody: String): Set<String> =
        CLASS_ATTR.find(tagBody)?.groupValues?.get(1)?.split(' ')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    private fun decodeEntity(entity: String): String? = when {
        entity == "gt" -> ">"
        entity == "lt" -> "<"
        entity == "amp" -> "&"
        entity == "quot" -> "\""
        entity == "apos" -> "'"
        entity == "nbsp" -> " "
        entity.startsWith("#x") || entity.startsWith("#X") -> codePoint(entity.drop(2).toIntOrNull(16))
        entity.startsWith("#") -> codePoint(entity.drop(1).toIntOrNull())
        else -> null
    }

    /** Null for anything outside Unicode: `Character.toChars` throws on those, and a numeric entity must never take the parser down. */
    private fun codePoint(value: Int?): String? =
        value?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }

    private val FRAMED_TAGS = setOf("s", "b", "i", "u", "pre", "span", "a")
    private val HREF_ATTR = Regex("""href\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val CLASS_ATTR = Regex("""class\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val DEAD_QUOTE = Regex(""">>(\d+)""")
    private val CROSS_THREAD = Regex("""(?:https?:)?(?://boards\.4chan(?:nel)?\.org)?/(\w+)/thread/(\d+)(?:#p(\d+))?""")
}
