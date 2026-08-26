package dev.stan.yotsuba.util

import dev.stan.yotsuba.core.util.FileSize
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FileSizeTest {

    // format() uses the default locale for the decimal separator; pin it so the
    // expected strings hold on any machine.
    private lateinit var originalLocale: Locale

    @Before fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test fun `bytes stay bytes below one kilobyte`() {
        assertEquals("0 B", FileSize.format(0))
        assertEquals("1023 B", FileSize.format(1023))
    }

    @Test fun `kilobytes round to whole numbers`() {
        assertEquals("1 KB", FileSize.format(1024))
        assertEquals("2 KB", FileSize.format(1536)) // 1.5 KB rounds up
        assertEquals("1023 KB", FileSize.format(1024 * 1024 - 1024))
    }

    @Test fun `megabytes keep one decimal`() {
        assertEquals("1.0 MB", FileSize.format(1024 * 1024))
        assertEquals("1.5 MB", FileSize.format(1_572_864))
        assertEquals("100.0 MB", FileSize.format(100L * 1024 * 1024))
    }
}
