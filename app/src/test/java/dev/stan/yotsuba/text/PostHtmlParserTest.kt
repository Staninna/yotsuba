package dev.stan.yotsuba.text

import dev.stan.yotsuba.core.text.PostAnnotation
import dev.stan.yotsuba.core.text.PostHtmlParser
import dev.stan.yotsuba.core.text.PostStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHtmlParserTest {
    private val parser = PostHtmlParser

    @Test fun `empty and null com`() {
        assertTrue(parser.parse(null).segments.isEmpty())
        assertTrue(parser.parse("").segments.isEmpty())
    }

    @Test fun `plain text with entities`() {
        val t = parser.parse("Tom &amp; Jerry &gt; others &#039;quoted&#039; &#x41;")
        assertEquals("Tom & Jerry > others 'quoted' A", t.plainText)
    }

    @Test fun `br becomes newline`() {
        assertEquals("a\nb", parser.parse("a<br>b").plainText)
    }

    @Test fun `wbr becomes zero-width break`() {
        assertEquals("long​word", parser.parse("long<wbr>word").plainText)
    }

    @Test fun greentext() {
        val t = parser.parse("<span class=\"quote\">&gt;implying</span>")
        assertEquals(">implying", t.plainText)
        assertTrue(t.segments.single().styles.contains(PostStyle.GREENTEXT))
    }

    @Test fun `same-thread quotelink`() {
        val t = parser.parse("<a href=\"#p109582912\" class=\"quotelink\">&gt;&gt;109582912</a>")
        val seg = t.segments.single()
        assertEquals(">>109582912", seg.text)
        assertEquals(PostAnnotation.QuotelinkSameThread(109582912L), seg.annotation)
    }

    @Test fun `cross-thread quotelink`() {
        val t = parser.parse("<a href=\"/g/thread/109593884#p109593885\" class=\"quotelink\">&gt;&gt;109593885</a>")
        assertEquals(
            PostAnnotation.QuotelinkCrossThread("g", 109593884L, 109593885L),
            t.segments.single().annotation,
        )
    }

    @Test fun `cross-board quotelink without post anchor`() {
        val t = parser.parse("<a href=\"/wsr/thread/123456\" class=\"quotelink\">&gt;&gt;&gt;/wsr/123456</a>")
        assertEquals(
            PostAnnotation.QuotelinkCrossThread("wsr", 123456L, null),
            t.segments.single().annotation,
        )
    }

    @Test fun `quotelink with rel attribute and varying attribute order`() {
        val a = parser.parse("<a class=\"quotelink\" rel=\"nofollow ugc\" href=\"#p42\">&gt;&gt;42</a>")
        assertEquals(PostAnnotation.QuotelinkSameThread(42L), a.segments.single().annotation)
        val b = parser.parse("<a rel=\"nofollow ugc\" href=\"#p42\" class=\"quotelink\">&gt;&gt;42</a>")
        assertEquals(PostAnnotation.QuotelinkSameThread(42L), b.segments.single().annotation)
    }

    @Test fun `deadlink is inert styled text`() {
        val t = parser.parse("<span class=\"deadlink\">&gt;&gt;109581000</span>")
        val seg = t.segments.single()
        assertTrue(seg.styles.contains(PostStyle.DEADLINK))
        assertEquals(PostAnnotation.Deadlink, seg.annotation)
    }

    @Test fun `spoilers get distinct ids`() {
        val t = parser.parse("<s>one</s> and <s>two</s>")
        val spoilers = t.segments.filter { it.styles.contains(PostStyle.SPOILER) }
        assertEquals(2, spoilers.size)
        assertEquals(PostAnnotation.Spoiler(0), spoilers[0].annotation)
        assertEquals(PostAnnotation.Spoiler(1), spoilers[1].annotation)
        val middle = t.segments.first { it.text == " and " }
        assertNull(middle.annotation)
        assertFalse(middle.styles.contains(PostStyle.SPOILER))
    }

    @Test fun `code block preserves whitespace and br newlines`() {
        val t = parser.parse("<pre class=\"prettyprint\">def f():<br>    return 1</pre>")
        assertEquals("def f():\n    return 1", t.plainText)
        assertTrue(t.segments.all { it.styles.contains(PostStyle.CODE) })
    }

    @Test fun `sjis and math are monospace styles`() {
        assertTrue(parser.parse("<span class=\"sjis\">art</span>").segments.single().styles.contains(PostStyle.SJIS))
        assertTrue(parser.parse("<span class=\"math\">x^2</span>").segments.single().styles.contains(PostStyle.MATH))
    }

    @Test fun `basic emphasis`() {
        assertTrue(parser.parse("<b>x</b>").segments.single().styles.contains(PostStyle.BOLD))
        assertTrue(parser.parse("<i>x</i>").segments.single().styles.contains(PostStyle.ITALIC))
        assertTrue(parser.parse("<u>x</u>").segments.single().styles.contains(PostStyle.UNDERLINE))
    }

    @Test fun `unknown tag dropped and text kept, no raw markup survives`() {
        val t = parser.parse("before <blink foo=\"bar\">kept</blink> after <table><tr><td>cell</td></tr></table>")
        assertEquals("before kept after cell", t.plainText)
        assertFalse(t.plainText.contains("<"))
        assertFalse(t.plainText.contains(">") && t.plainText.contains("<"))
    }

    @Test fun `unknown span class keeps content without style and pops symmetrically`() {
        val t = parser.parse("<span class=\"fortune\">lucky</span><span class=\"quote\">&gt;gt</span>")
        assertEquals("lucky>gt", t.plainText)
        assertTrue(t.segments.first { it.text == "lucky" }.styles.isEmpty())
        assertTrue(t.segments.first { it.text == ">gt" }.styles.contains(PostStyle.GREENTEXT))
    }

    @Test fun `protocol-relative bare link resolved to https`() {
        val t = parser.parse("<a href=\"//boards.4chan.org/wsr/\">//boards.4chan.org/wsr/</a>")
        assertEquals(PostAnnotation.Link("https://boards.4chan.org/wsr/"), t.segments.single().annotation)
    }

    @Test fun `malformed html does not crash and keeps text`() {
        assertEquals("unclosed ", parser.parse("unclosed <b>bold").plainText.let { it.substring(0, 9) })
        assertEquals("a<b", parser.parse("a<b").plainText)
        assertEquals("stray & amp", parser.parse("stray & amp").plainText)
    }

    @Test fun `link survives a nested closed spoiler`() {
        val t = parser.parse("<a href=\"//x.com\"><s>hidden</s> rest</a>")
        val hidden = t.segments.first { it.text == "hidden" }
        assertTrue(hidden.styles.contains(PostStyle.SPOILER))
        assertEquals(PostAnnotation.Spoiler(0), hidden.annotation)
        val rest = t.segments.first { it.text == " rest" }
        assertTrue(rest.styles.isEmpty())
        assertEquals(PostAnnotation.Link("https://x.com"), rest.annotation)
    }

    @Test fun `spoiler survives a nested closed quotelink`() {
        val t = parser.parse("<s><a href=\"#p7\" class=\"quotelink\">&gt;&gt;7</a>tail</s>")
        val quote = t.segments.first { it.text == ">>7" }
        assertTrue(quote.styles.contains(PostStyle.SPOILER))
        assertEquals(PostAnnotation.QuotelinkSameThread(7L), quote.annotation)
        val tail = t.segments.first { it.text == "tail" }
        assertTrue(tail.styles.contains(PostStyle.SPOILER))
        assertEquals(PostAnnotation.Spoiler(0), tail.annotation)
    }

    @Test fun `nested known spans unwind innermost first`() {
        val t = parser.parse("<span class=\"quote\"><span class=\"sjis\">art</span>green</span>")
        val art = t.segments.first { it.text == "art" }
        assertTrue(art.styles.containsAll(listOf(PostStyle.GREENTEXT, PostStyle.SJIS)))
        val green = t.segments.first { it.text == "green" }
        assertTrue(green.styles.contains(PostStyle.GREENTEXT))
        assertFalse(green.styles.contains(PostStyle.SJIS))
    }

    @Test fun `greentext with nested quotelink`() {
        val t = parser.parse("<a href=\"#p1\" class=\"quotelink\">&gt;&gt;1</a><br><span class=\"quote\">&gt;green</span>")
        assertEquals(">>1\n>green", t.plainText)
        assertEquals(PostAnnotation.QuotelinkSameThread(1L), t.segments.first().annotation)
        assertTrue(t.segments.last().styles.contains(PostStyle.GREENTEXT))
    }
}
