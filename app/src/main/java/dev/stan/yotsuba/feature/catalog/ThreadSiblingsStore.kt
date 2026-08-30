package dev.stan.yotsuba.feature.catalog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton

/** The threads either side of one in the catalog it was opened from; null = end of the list. */
data class ThreadNeighbours(val previous: Long?, val next: Long?)

/**
 * Remembers the order a catalog was showing when a thread was opened from it, so the thread
 * screen can swipe to the next or previous one without going back. Only the last catalog
 * counts: a thread opened from somewhere else (a quote, history, a link) finds no entry and
 * gets no swipe.
 */
@Singleton
class ThreadSiblingsStore @Inject constructor() {
    @Volatile private var siblings: Siblings? = null

    /** Called when a thread is opened; [threadNos] is the list as displayed, after search and filters. */
    fun record(board: String, threadNos: List<Long>) {
        siblings = Siblings(board, threadNos)
    }

    fun neighbours(board: String, threadNo: Long): ThreadNeighbours? {
        val s = siblings ?: return null
        if (s.board != board) return null
        return neighboursIn(s.threadNos, threadNo)
    }

    private class Siblings(val board: String, val threadNos: List<Long>)

    companion object {
        /** Pure: the entries beside [threadNo] in [threadNos], or null when it is not in the list. */
        fun neighboursIn(threadNos: List<Long>, threadNo: Long): ThreadNeighbours? {
            val index = threadNos.indexOf(threadNo)
            if (index < 0) return null
            return ThreadNeighbours(
                previous = threadNos.getOrNull(index - 1),
                next = threadNos.getOrNull(index + 1),
            )
        }
    }
}

/** Hands the singleton store to the navigation graph, which has no other way to inject it. */
@HiltViewModel
class ThreadSiblingsViewModel @Inject constructor(val store: ThreadSiblingsStore) : ViewModel()
