package dev.stan.yotsuba.util

import dev.stan.yotsuba.core.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/** The millisecond entry point history uses; the seconds one is covered by TimeFormatTest. */
class HistoryTimeFormatTest {
    private val nowMs = 1_700_000_000_000L

    @Test fun `millis and seconds entries agree`() {
        val fiveMinutesAgoMs = nowMs - 5 * 60_000
        assertEquals("5m ago", TimeFormat.relativeMillis(fiveMinutesAgoMs, nowMs))
        assertEquals(
            TimeFormat.relative(fiveMinutesAgoMs / 1000, nowMs),
            TimeFormat.relativeMillis(fiveMinutesAgoMs, nowMs),
        )
    }

    @Test fun `sub-minute and future instants are just now`() {
        assertEquals("just now", TimeFormat.relativeMillis(nowMs - 59_999, nowMs))
        assertEquals("just now", TimeFormat.relativeMillis(nowMs + 5_000, nowMs))
    }

    @Test fun `days are whole days`() {
        assertEquals("3d ago", TimeFormat.relativeMillis(nowMs - 3 * 86_400_000L - 1, nowMs))
    }
}
