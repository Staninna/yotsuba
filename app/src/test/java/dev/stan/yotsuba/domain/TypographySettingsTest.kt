package dev.stan.yotsuba.domain

import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.LineSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographySettingsTest {

    @Test fun `font size scales are the documented values and grow with the enum order`() {
        assertEquals(0.875f, FontSize.SMALL.scale)
        assertEquals(1f, FontSize.DEFAULT.scale)
        assertEquals(1.15f, FontSize.LARGE.scale)
        assertEquals(1.3f, FontSize.EXTRA_LARGE.scale)
        assertTrue(FontSize.entries.zipWithNext().all { (a, b) -> a.scale < b.scale })
    }

    @Test fun `line spacing values are the documented em heights and grow with the enum order`() {
        assertEquals(1.25f, LineSpacing.COMPACT.em)
        assertEquals(1.45f, LineSpacing.DEFAULT.em)
        assertEquals(1.7f, LineSpacing.RELAXED.em)
        assertTrue(LineSpacing.entries.zipWithNext().all { (a, b) -> a.em < b.em })
    }
}
