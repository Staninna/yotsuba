package dev.stan.yotsuba.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BoardProfileTest {

    private val global = Settings(fontSize = FontSize.SMALL, mediaAutoplay = MediaAutoplay.NEVER, revealAllSpoilers = false)

    @Test fun `forBoard lays set fields over the globals and leaves null ones alone`() {
        val settings = global.copy(
            boardProfiles = mapOf("a" to BoardProfile(fontSize = FontSize.LARGE, revealAllSpoilers = true)),
        )
        val a = settings.forBoard("a")
        assertEquals(FontSize.LARGE, a.fontSize)
        assertEquals(true, a.revealAllSpoilers)
        assertEquals(MediaAutoplay.NEVER, a.mediaAutoplay)
        assertEquals(global.lineSpacing, a.lineSpacing)
        assertEquals(global.inlineImageExpansion, a.inlineImageExpansion)
    }

    @Test fun `a board without a profile gets the same settings back`() {
        assertSame(global, global.forBoard("g"))
    }
}
