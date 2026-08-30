package dev.stan.yotsuba.util

import dev.stan.yotsuba.core.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    private val nowMs = 1_000_000_000_000L // 1e9 seconds, exactly
    private val nowSec = nowMs / 1000

    private fun ago(seconds: Long) = TimeFormat.relative(nowSec - seconds, nowMs)

    @Test fun `under a minute reads just now`() {
        assertEquals("just now", ago(0))
        assertEquals("just now", ago(59))
    }

    @Test fun `minutes and hours boundaries`() {
        assertEquals("1m ago", ago(60))
        assertEquals("59m ago", ago(3599))
        assertEquals("1h ago", ago(3600))
        assertEquals("23h ago", ago(86_399))
    }

    @Test fun `days months and years boundaries`() {
        assertEquals("1d ago", ago(86_400))
        assertEquals("29d ago", ago(86_400L * 30 - 1))
        assertEquals("1mo ago", ago(86_400L * 30))
        // The month bucket is 30-day based, so day 360..364 still reads 12mo.
        assertEquals("12mo ago", ago(86_400L * 364))
        assertEquals("1y ago", ago(86_400L * 365))
        assertEquals("2y ago", ago(86_400L * 730))
    }

    @Test fun `future timestamps clamp to just now`() {
        assertEquals("just now", TimeFormat.relative(nowSec + 500, nowMs))
    }

    @Test fun `duration is m-ss below the hour and h-mm-ss past it`() {
        assertEquals("0:00", TimeFormat.duration(0))
        assertEquals("0:05", TimeFormat.duration(5_999))
        assertEquals("59:59", TimeFormat.duration(59 * 60_000L + 59_999L))
        assertEquals("1:23:20", TimeFormat.duration(83 * 60_000L + 20_000L))
    }
}
