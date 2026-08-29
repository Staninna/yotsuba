package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One page of a full-screen vertical media feed. */
data class ViewerPage(
    val isVideo: Boolean,
    /** Playable URI string for videos; unused for images. */
    val videoUri: String = "",
    /** Coil model for images; unused for videos. */
    val imageModel: Any? = null,
    val thumbnailModel: Any? = null,
    val width: Int = 0,
    val height: Int = 0,
    val title: String = "",
    /** Appended after the "n / total" prefix in the top chrome. */
    val subtitle: String = "",
    val contentDescription: String = "",
) {
    val pipInfo: PipMediaInfo get() = PipMediaInfo(width, height, isVideo)
}

/** Pager position plus mute/playback/chrome state, shared by both viewers. */
@Stable
class MediaFeedState internal constructor(
    val pager: PagerState,
    private val scope: CoroutineScope,
    initialMuted: Boolean,
    initialPlaying: Boolean,
) {
    var muted by mutableStateOf(initialMuted)
    var playbackOn by mutableStateOf(initialPlaying)
    var chromeVisible by mutableStateOf(true)

    val currentPage: Int get() = pager.currentPage

    fun scrollTo(index: Int) {
        scope.launch { pager.scrollToPage(index) }
    }

    fun previous() = scrollTo((currentPage - 1).coerceAtLeast(0))

    fun next(lastIndex: Int) = scrollTo((currentPage + 1).coerceAtMost(lastIndex))

    fun animateNextWrapping(pageCount: Int) {
        scope.launch { pager.animateScrollToPage((currentPage + 1) % pageCount) }
    }
}

@Composable
fun rememberMediaFeedState(
    initialIndex: Int,
    initialMuted: Boolean = false,
    initialPlaying: Boolean = true,
    pageCount: () -> Int,
): MediaFeedState {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = initialIndex, pageCount = pageCount)
    return remember(pager) { MediaFeedState(pager, scope, initialMuted, initialPlaying) }
}

/**
 * The shared full-screen vertical media feed (live thread and vault): pager over zoomable
 * images and looping videos, auto-hiding top chrome, and picture-in-picture.
 */
@Composable
fun MediaFeedViewer(
    pages: List<ViewerPage>,
    feed: MediaFeedState,
    pip: PipController,
    autoAdvance: Boolean,
    onToggleAutoAdvance: () -> Unit,
    onPageViewed: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** False while another layer (e.g. a reply panel) covers the feed. */
    feedActive: Boolean = true,
    behaviour: ViewerBehaviour = ViewerBehaviour(),
    /** Long-press on the page at this index; no-op where saving does not apply. */
    onLongPressPage: (Int) -> Unit = {},
    /** Saves still queued or running; kept on screen after the chrome hides. */
    activeDownloads: Int = 0,
    topBarActions: @Composable RowScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val chromeShown = feed.chromeVisible && !pip.inPipMode && feedActive

    LaunchedEffect(feed.chromeVisible) {
        if (feed.chromeVisible) {
            delay(3_000)
            feed.chromeVisible = false
        }
    }

    LaunchedEffect(feed.currentPage) { onPageViewed(feed.currentPage) }

    // One owner for the wake lock. FLAG_KEEP_SCREEN_ON is window-scoped and not
    // refcounted, so letting each composed VideoPage set it would have them fighting;
    // View.keepScreenOn is released for us when this composable leaves.
    val view = LocalView.current
    val keepAwake = behaviour.keepScreenOn && feedActive &&
        feed.playbackOn && pages.getOrNull(feed.currentPage)?.isVideo == true
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(pip.inPipMode, feed.currentPage, feed.playbackOn, pages) {
        if (pip.inPipMode) {
            pip.updateParams(pages.getOrNull(feed.currentPage)?.pipInfo, feed.playbackOn)
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = feed.pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !pip.inPipMode && feedActive,
        ) { page ->
            val p = pages[page]
            if (p.isVideo) {
                VideoPage(
                    videoUri = p.videoUri,
                    thumbnailModel = p.thumbnailModel,
                    initialWidth = p.width,
                    initialHeight = p.height,
                    selected = feed.currentPage == page && feedActive,
                    playing = feed.playbackOn,
                    onTogglePlay = { feed.playbackOn = !feed.playbackOn },
                    muted = feed.muted,
                    chromeVisible = chromeShown,
                    onToggleMute = { feed.muted = !feed.muted },
                    onToggleChrome = { feed.chromeVisible = !feed.chromeVisible },
                    autoAdvance = autoAdvance,
                    onEnded = { feed.animateNextWrapping(pages.size) },
                    behaviour = behaviour,
                    onLongPress = { onLongPressPage(page) },
                )
            } else {
                ImagePage(
                    model = p.imageModel,
                    thumbnailModel = p.thumbnailModel,
                    contentDescription = p.contentDescription,
                    onTap = { feed.chromeVisible = !feed.chromeVisible },
                    onLongPress = { onLongPressPage(page) },
                )
            }
        }

        overlay()

        val current = pages.getOrNull(feed.currentPage)
        ViewerTopChrome(
            visible = chromeShown,
            title = current?.title.orEmpty(),
            subtitle = current?.let { "${feed.currentPage + 1} / ${pages.size}${it.subtitle}" },
            onClose = onDismiss,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            AutoAdvanceButton(autoAdvance, onToggleAutoAdvance)
            PipButton { pip.enter(current?.pipInfo, feed.playbackOn) }
            topBarActions()
        }

        DownloadIndicator(
            count = activeDownloads,
            visible = activeDownloads > 0 && !chromeShown && !pip.inPipMode,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
