package dev.stan.yotsuba.feature.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The viewer is a stack: media pages and reply panels push on top of each other.
 * Swipe left anywhere to open the current post's replies; swipe right to go back
 * one step, whatever that step was (media page or panel).
 */
sealed interface ViewerEntry {
    data class Media(val index: Int) : ViewerEntry
    data class Panel(val rootPostNo: Long) : ViewerEntry
}

@Stable
class ViewerStack(initialIndex: Int, private val scrollToPage: (Int) -> Unit) {
    var entries by mutableStateOf<List<ViewerEntry>>(listOf(ViewerEntry.Media(initialIndex)))
        private set

    /** Slide direction for panel transitions: true = pushing deeper, false = popping back. */
    var navForward by mutableStateOf(true)
        private set

    val top: ViewerEntry get() = entries.last()
    val onMedia: Boolean get() = top is ViewerEntry.Media
    val size: Int get() = entries.size
    val panelDepth: Int get() = entries.count { it is ViewerEntry.Panel }

    fun push(entry: ViewerEntry) {
        navForward = true
        entries = entries + entry
        if (entry is ViewerEntry.Media) scrollToPage(entry.index)
    }

    fun pop() {
        if (entries.size <= 1) return
        navForward = false
        entries = entries.dropLast(1)
        restoreMediaBelow()
    }

    /** Vertical swipes on the feed rewrite the top media entry so back lands where you left. */
    fun syncToPage(page: Int) {
        val t = top
        if (t is ViewerEntry.Media && t.index != page) {
            entries = entries.dropLast(1) + ViewerEntry.Media(page)
        }
    }

    /** Entering PiP drops every panel, leaving just the current media page. */
    fun collapseToMedia(page: Int) {
        navForward = false
        entries = listOf(ViewerEntry.Media(page))
    }

    /** Restores the media page that lives directly under the new top. */
    private fun restoreMediaBelow() {
        val mediaBelow = entries.lastOrNull { it is ViewerEntry.Media } as? ViewerEntry.Media
        if (mediaBelow != null) scrollToPage(mediaBelow.index)
    }
}

@Composable
fun rememberViewerStack(initialIndex: Int, scrollToPage: (Int) -> Unit): ViewerStack =
    remember { ViewerStack(initialIndex, scrollToPage) }
