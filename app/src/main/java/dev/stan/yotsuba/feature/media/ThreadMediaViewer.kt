package dev.stan.yotsuba.feature.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The full-screen media viewer, whole: pager, chrome, playback, picture-in-picture, and the
 * reply panel stacked over it.
 *
 * There is one of these because there is one viewer. The live thread and the vault differ
 * only in where their pages and their conversation come from, and when they were two
 * copies of this wiring the vault's half quietly drifted -- different slide distances, a
 * panel that animated from a different place, no replies at all for a while.
 *
 * [postNoAt] and [indexOfPost] are the only seam that varies: they map between a page and
 * the post it belongs to, which the live thread answers from its posts and the vault from
 * what happens to be saved.
 */
@Composable
fun ThreadMediaViewer(
    pages: List<ViewerPage>,
    thread: ViewerThread,
    behaviour: ViewerBehaviour,
    initialIndex: Int,
    muted: Boolean,
    playing: Boolean,
    autoAdvance: Boolean,
    onToggleAutoAdvance: () -> Unit,
    postNoAt: (Int) -> Long?,
    indexOfPost: (Long) -> Int,
    onPageViewed: (Int) -> Unit,
    onDismiss: () -> Unit,
    onLongPressPage: (Int) -> Unit = {},
    activeDownloads: Int = 0,
    overlay: @Composable BoxScope.() -> Unit = {},
    /** Secondary actions for the page, behind the top bar's overflow menu. */
    topBarMenu: @Composable ColumnScope.(page: Int, close: () -> Unit) -> Unit = { _, _ -> },
    topBarActions: @Composable RowScope.(page: Int, openReplies: (Long) -> Unit) -> Unit = { _, _ -> },
) {
    val feed = rememberMediaFeedState(
        initialIndex = initialIndex,
        initialMuted = muted,
        initialPlaying = playing,
    ) { pages.size }
    LaunchedEffect(muted) { feed.muted = muted }
    LaunchedEffect(playing) { feed.playbackOn = playing }

    val stack = rememberViewerStack(initialIndex, feed::scrollTo)
    val pip = rememberPipController(feed) { pages.lastIndex }

    BackHandler(enabled = stack.size > 1) { stack.pop() }

    LaunchedEffect(pip.inPipMode) {
        if (pip.inPipMode && !stack.onMedia) stack.collapseToMedia(feed.currentPage)
    }

    // The gesture detector below is set up once and keeps whatever it captured. The vault
    // reads its saved conversation off disk after the viewer is already on screen, so a
    // captured `thread` would still be the empty one it started with -- the swipe worked
    // in live threads, where the screen waits for its posts, and silently did nothing in
    // the vault. These stay current for the life of the composable instead.
    val hasPosts by rememberUpdatedState(thread.hasPosts)
    val postNoAtNow by rememberUpdatedState(postNoAt)
    val indexOfPostNow by rememberUpdatedState(indexOfPost)

    fun openReplies(postNo: Long) {
        if (hasPosts) stack.push(ViewerEntry.Panel(postNo))
    }

    fun jumpToMedia(postNo: Long) {
        val index = indexOfPostNow(postNo)
        if (index >= 0) stack.push(ViewerEntry.Media(index))
    }

    MediaFeedViewer(
        pages = pages,
        feed = feed,
        pip = pip,
        autoAdvance = autoAdvance,
        onToggleAutoAdvance = onToggleAutoAdvance,
        onPageViewed = { page ->
            stack.syncToPage(page)
            onPageViewed(page)
        },
        onDismiss = onDismiss,
        feedActive = stack.onMedia,
        behaviour = behaviour,
        onLongPressPage = onLongPressPage,
        activeDownloads = activeDownloads,
        // Horizontal navigation: left opens the current post's replies, right goes back.
        modifier = Modifier.pointerInput(pip.inPipMode) {
            if (pip.inPipMode) return@pointerInput
            detectViewerSwipe(
                onSwipeLeft = {
                    val top = stack.top
                    if (top is ViewerEntry.Media) postNoAtNow(top.index)?.let(::openReplies)
                },
                // At the bottom of the stack, back-swipe leaves the viewer.
                onSwipeRight = { if (stack.size > 1) stack.pop() else onDismiss() },
            )
        },
        topBarActions = { topBarActions(feed.currentPage, ::openReplies) },
        topBarMenu = { close -> topBarMenu(feed.currentPage, close) },
        overlay = {
            // Reply panel layer, animated push/pop; the media feed stays alive underneath.
            AnimatedContent(
                targetState = stack.top as? ViewerEntry.Panel,
                transitionSpec = {
                    if (stack.navForward) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    } else {
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "panel",
            ) { panel ->
                if (panel != null) {
                    SubThreadPanel(
                        rootPostNo = panel.rootPostNo,
                        depth = stack.panelDepth,
                        thread = thread,
                        onOpenSubThread = { stack.push(ViewerEntry.Panel(it)) },
                        onJumpToMedia = ::jumpToMedia,
                        onBack = stack::pop,
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
            overlay()
        },
    )
}
