package dev.stan.yotsuba.core.util

object TimeFormat {
    fun relative(epochSeconds: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = (nowMs / 1000) - epochSeconds
        return when {
            diff < 60 -> "just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86_400 -> "${diff / 3600}h ago"
            diff < 86_400 * 30 -> "${diff / 86_400}d ago"
            diff < 86_400 * 365 -> "${diff / (86_400 * 30)}mo ago"
            else -> "${diff / (86_400 * 365)}y ago"
        }
    }
}
