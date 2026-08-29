package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The viewer is a stack: media pages and reply panels push on top of each other.
 * Swipe left anywhere to open the current post's replies; swipe right to go back
 * one step, whatever that step was (media page or panel).
 */
/** Swipe distance that commits a horizontal navigation, in either viewer. */
private val SWIPE_COMMIT = 56.dp

/** How much more sideways than vertical a drag must be before it counts as a swipe. */
private const val HORIZONTAL_BIAS = 1.5f

/**
 * Horizontal navigation for the viewer, measured on the [PointerEventPass.Final] pass:
 * whatever movement no child claimed is ours.
 *
 * A plain `detectHorizontalDragGestures` on the parent loses this race. Compose offers
 * pointer events to children first, so the pager's slop detection or telephoto's pan can
 * consume the very event that would have crossed the threshold, and the swipe silently
 * does nothing — which is exactly the "sometimes it just doesn't open" the old one had.
 * Reading the leftovers instead means a drag something else wanted stays theirs, and one
 * nobody wanted always reaches us, including from on top of a reply panel that covers the
 * feed completely.
 */
suspend fun PointerInputScope.detectViewerSwipe(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    val commit = SWIPE_COMMIT.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        var dx = 0f
        var dy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            // A second finger means a pinch; nothing about that is a swipe.
            if (event.changes.size > 1) return@awaitEachGesture
            val change = event.changes.firstOrNull() ?: break
            if (!change.isConsumed) {
                dx += change.positionChange().x
                dy += change.positionChange().y
            }
            if (!change.pressed) break
        }
        // Diagonal drags belong to the vertical pager; only a decisive sideways one counts.
        if (abs(dx) < commit || abs(dx) < abs(dy) * HORIZONTAL_BIAS) return@awaitEachGesture
        if (dx < 0) onSwipeLeft() else onSwipeRight()
    }
}

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
