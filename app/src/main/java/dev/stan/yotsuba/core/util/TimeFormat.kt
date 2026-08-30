package dev.stan.yotsuba.core.util

import java.text.DateFormat
import java.util.Date

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
}
