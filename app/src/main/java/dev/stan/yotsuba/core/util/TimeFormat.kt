package dev.stan.yotsuba.core.util

object TimeFormat {
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
