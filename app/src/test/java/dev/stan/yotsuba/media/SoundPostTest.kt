package dev.stan.yotsuba.media

import dev.stan.yotsuba.core.media.SoundPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundPostTest {

    @Test fun `a catbox tag without a scheme becomes an https url and leaves the name`() {
        val parsed = SoundPost.parse("dance[sound=files.catbox.moe%2Fabcd12.mp3]")
        assertEquals("dance", parsed.name)
        assertEquals("https://files.catbox.moe/abcd12.mp3", parsed.url)

        val explicit = SoundPost.parse("clip [sound=https%3A%2F%2Ffiles.catbox.moe%2Fxy.ogg]")
        assertEquals("clip", explicit.name)
        assertEquals("https://files.catbox.moe/xy.ogg", explicit.url)
    }

    @Test fun `a double-encoded tag is decoded until it is a url`() {
        val parsed = SoundPost.parse("x[sound=files.catbox.moe%252Fqw.mp3]")
        assertEquals("https://files.catbox.moe/qw.mp3", parsed.url)
        assertEquals("https://a.example/b.mp3", SoundPost.toUrl("https%253A%252F%252Fa.example%252Fb.mp3"))
    }

    @Test fun `junk and non-https tags yield no url but keep the name`() {
        assertNull(SoundPost.parse("a[sound=http%3A%2F%2Fevil.example%2Fx.mp3]").url)
        assertNull(SoundPost.parse("a[sound=javascript:alert(1)]").url)
        assertNull(SoundPost.parse("a[sound=not a url]").url)
        assertNull(SoundPost.parse("a[sound=]").url)
        assertNull(SoundPost.parse("a[sound=%22%3E%3Cscript%3E]").url)
        assertNull(SoundPost.parse("a[sound=user%3Apw%40host.example%2Fx.mp3]").url)
        assertEquals("a", SoundPost.parse("a[sound=not a url]").name)
        val plain = SoundPost.parse("plain filename")
        assertEquals("plain filename", plain.name)
        assertNull(plain.url)
    }
}
