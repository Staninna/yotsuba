package dev.stan.yotsuba.feature.media

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers, per thread, which media item was last on screen in the viewer. */
@Singleton
class MediaSessionStore @Inject constructor() {
    private val lastViewed = ConcurrentHashMap<Pair<String, Long>, Long>()

    fun setLastViewed(board: String, threadNo: Long, postNo: Long) {
        lastViewed[board to threadNo] = postNo
    }

    /** Returns and clears the last-viewed media post for this thread. */
    fun consumeLastViewed(board: String, threadNo: Long): Long? =
        lastViewed.remove(board to threadNo)
}
