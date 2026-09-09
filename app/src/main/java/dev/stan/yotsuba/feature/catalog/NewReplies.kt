package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.HistoryEntry

/**
 * Replies that landed in a visited thread after the user's read mark, as far as the catalog
 * can tell. The catalog only carries the newest few replies, so when every one of them is
 * unread the true number may be higher: [atLeast] says the count is a floor, not a total.
 */
data class NewReplies(val count: Int, val atLeast: Boolean)

/** null when nothing in the catalog's tail is newer than [readUpTo]. */
fun CatalogThread.newRepliesSince(readUpTo: Long): NewReplies? {
    val newer = lastReplyNos.count { it > readUpTo }
    if (newer == 0) return null
    return NewReplies(newer, atLeast = newer == lastReplyNos.size && replyCount > newer)
}

/** The post the user is known to have seen; at worst the OP, since the thread was opened. */
val HistoryEntry.readUpTo: Long
    get() = maxReadPostNo ?: lastScrollPostNo ?: threadNo
