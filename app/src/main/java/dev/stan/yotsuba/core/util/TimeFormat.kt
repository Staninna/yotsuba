package dev.stan.yotsuba.core.util

import dev.stan.yotsuba.domain.model.TimestampMode
import dev.stan.yotsuba.domain.model.TimestampZone
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

object TimeFormat {
    private val mediumDate: DateFormat by lazy { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    private val shortDate: DateFormat by lazy { DateFormat.getDateInstance(DateFormat.SHORT) }

    /** A calendar date in the default locale's medium style, e.g. "Jan 5, 2026". Main-thread only. */
    fun date(epochMillis: Long): String = mediumDate.format(Date(epochMillis))

    /** The short style, e.g. "1/5/26", for places that fit nothing longer. Main-thread only. */
    fun dateShort(epochMillis: Long): String = shortDate.format(Date(epochMillis))

    /** A video length as `m:ss`, or `h:mm:ss` past the hour. */
    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "just now", "5m ago", "3d ago"… for an instant in epoch milliseconds. */
    fun relativeMillis(epochMillis: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = (nowMs - epochMillis) / 1000
        return when {
            diff < 60 -> "just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86_400 -> "${diff / 3600}h ago"
            diff < 86_400 * 30 -> "${diff / 86_400}d ago"
            diff < 86_400 * 365 -> "${diff / (86_400 * 30)}mo ago"
            else -> "${diff / (86_400 * 365)}y ago"
        }
    }

    /**
     * Seconds-based entry kept for the 4chan API fields that arrive as epoch seconds
     * (catalog `last_modified`, post `time`). Anything holding milliseconds should call
     * [relativeMillis] directly rather than dividing first.
     */
    fun relative(epochSeconds: Long, nowMs: Long = System.currentTimeMillis()): String =
        relativeMillis(epochSeconds * 1000, nowMs)

    /**
     * A post's time the way the reader asked for it: "3m ago", the date and time, or both as
     * "3m ago, 21:04". The clock parts follow [locale] and sit in the phone's zone or the
     * board's; the relative part needs neither.
     */
    fun post(
        epochSeconds: Long,
        mode: TimestampMode,
        zone: TimestampZone,
        nowMs: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
    ): String {
        if (mode == TimestampMode.RELATIVE) return relative(epochSeconds, nowMs)
        val zoneId = when (zone) {
            TimestampZone.LOCAL -> ZoneId.systemDefault()
            TimestampZone.BOARD -> BOARD_ZONE
        }
        val at = Instant.ofEpochSecond(epochSeconds).atZone(zoneId)
        return when (mode) {
            TimestampMode.ABSOLUTE -> dateTime.withLocale(locale).format(at)
            else -> "${relative(epochSeconds, nowMs)}, ${time.withLocale(locale).format(at)}"
        }
    }

    /** 4chan stamps posts in US Eastern time; this is what the site itself shows. */
    private val BOARD_ZONE: ZoneId = ZoneId.of("America/New_York")
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
}
