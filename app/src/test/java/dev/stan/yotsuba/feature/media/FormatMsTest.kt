package dev.stan.yotsuba.feature.media

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The transport clock: minutes and zero-padded seconds, the same in every locale. */
class FormatMsTest {

    private lateinit var previous: Locale

    @Before fun saveLocale() { previous = Locale.getDefault() }
    @After fun restoreLocale() { Locale.setDefault(previous) }

    @Test fun `zero and sub-second positions read 0 colon 00`() {
        assertEquals("0:00", formatMs(0))
        assertEquals("0:00", formatMs(999))
    }

    @Test fun `seconds are zero padded and minutes are not`() {
        assertEquals("0:05", formatMs(5_000))
        assertEquals("1:05", formatMs(65_000))
        assertEquals("12:00", formatMs(720_000))
    }

    @Test fun `an hour rolls into the minutes field`() {
        assertEquals("61:01", formatMs(3_661_000))
    }

    @Test fun `a locale with its own digits does not change the label`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("1:05", formatMs(65_000))
    }
}
