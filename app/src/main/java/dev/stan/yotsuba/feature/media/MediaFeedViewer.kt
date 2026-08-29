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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.rememberHaptics
import dev.stan.yotsuba.core.util.FileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHROME_HIDE_DELAY_MS = 3_000L

/** One page of a full-screen vertical media feed: an image to zoom or a video to play. */
sealed interface ViewerPage {
    /** Usually the already-cached thumbnail, drawn underneath until the real thing loads. */
    val thumbnailModel: Any?
    val width: Int
    val height: Int
    /** Null when the size is unknown (a legacy vault row, say). */
    val sizeBytes: Long?
    val title: String
    /** Free text the chrome appends after size and dimensions, e.g. the thread subject. */
    val note: String?
    val contentDescription: String
    /** External audio to play alongside the visual (a "sound post"); null for most files. */
    val soundUrl: String? get() = null
    /**
     * Shared-element key matching the thumbnail this page was opened from (the media's
     * full URL, or a vault file's path); null when nothing on the previous screen shares.
     */
    val sharedKey: String? get() = null

    val isVideo: Boolean get() = this is Video
    val pipInfo: PipMediaInfo get() = PipMediaInfo(width, height, isVideo)

    data class Image(
        /** Coil model: a URL request or a [java.io.File] straight from the vault. */
        val model: Any?,
        override val thumbnailModel: Any? = null,
        override val width: Int = 0,
        override val height: Int = 0,
        override val sizeBytes: Long? = null,
        override val title: String = "",
        override val note: String? = null,
        override val contentDescription: String = "",
        /** Data saver: show the thumbnail and fetch the full image only on a tap. */
        val deferLoad: Boolean = false,
        override val soundUrl: String? = null,
        override val sharedKey: String? = null,
    ) : ViewerPage

    data class Video(
        /** Playable URI string, remote or `file://`. */
        val uri: String,
        override val thumbnailModel: Any? = null,
        override val width: Int = 0,
        override val height: Int = 0,
        override val sizeBytes: Long? = null,
        override val title: String = "",
        override val note: String? = null,
        override val contentDescription: String = "",
        override val soundUrl: String? = null,
        override val sharedKey: String? = null,
    ) : ViewerPage
}

/** "3 / 12 · 1.2 MB · 1920×1080 · subject · ↓2", dropping whatever is unknown. */
internal fun viewerSubtitle(page: ViewerPage, index: Int, total: Int, activeDownloads: Int): String =
    buildString {
        append("${index + 1} / $total")
        page.sizeBytes?.let { append(" · ${FileSize.format(it)}") }
        if (page.width > 0 && page.height > 0) append(" · ${page.width}×${page.height}")
        page.note?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        if (activeDownloads > 0) append(" · ↓$activeDownloads")
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

    /** Bumped on every control interaction; the auto-hide timer restarts when it changes. */
    var chromeTouches by mutableStateOf(0L)
        private set

    /** True while the seek bar is being dragged; the chrome stays put until it is let go. */
    var scrubbing by mutableStateOf(false)

    /** Keeps the chrome up and restarts its countdown. */
    fun touchChrome() {
        chromeVisible = true
        chromeTouches++
    }

    /** Set for the length of a gesture that is clearly sideways, so the pager sits it out. */
    var pagerLocked by mutableStateOf(false)

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
    val haptics = rememberHaptics()

    // Restarts on every control tap and waits out a scrub, so the bar never vanishes
    // under a finger that is still using it.
    LaunchedEffect(feed.chromeVisible, feed.chromeTouches, feed.scrubbing) {
        if (feed.chromeVisible && !feed.scrubbing) {
            delay(CHROME_HIDE_DELAY_MS)
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

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                lockPagerOnHorizontalIntent(
                    onLock = { feed.pagerLocked = true },
                    onRelease = { feed.pagerLocked = false },
                )
            },
    ) {
        VerticalPager(
            state = feed.pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !pip.inPipMode && feedActive && !feed.pagerLocked,
        ) { page ->
            when (val p = pages[page]) {
                is ViewerPage.Video -> VideoPage(
                    videoUri = p.uri,
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
                    onControlTouched = feed::touchChrome,
                    onScrubbing = { feed.scrubbing = it },
                    autoAdvance = autoAdvance,
                    onEnded = { feed.animateNextWrapping(pages.size) },
                    behaviour = behaviour,
                    onLongPress = { haptics.longPress(); onLongPressPage(page) },
                    soundUrl = p.soundUrl,
                    sharedKey = p.sharedKey,
                )
                is ViewerPage.Image -> ImagePage(
                    model = p.model,
                    thumbnailModel = p.thumbnailModel,
                    contentDescription = p.contentDescription,
                    deferLoad = p.deferLoad,
                    sizeBytes = p.sizeBytes,
                    soundUrl = p.soundUrl,
                    selected = feed.currentPage == page && feedActive,
                    playing = feed.playbackOn,
                    muted = feed.muted,
                    onTap = { feed.chromeVisible = !feed.chromeVisible },
                    onLongPress = { haptics.longPress(); onLongPressPage(page) },
                    sharedKey = p.sharedKey,
                )
            }
        }

        overlay()

        val current = pages.getOrNull(feed.currentPage)
        ViewerTopChrome(
            visible = chromeShown,
            title = current?.title.orEmpty(),
            subtitle = current?.let { viewerSubtitle(it, feed.currentPage, pages.size, activeDownloads) },
            onClose = onDismiss,
            badge = if (current?.soundUrl != null) stringResource(R.string.media_sound_badge) else null,
            modifier = Modifier.align(Alignment.TopCenter).notifyOnPress(feed::touchChrome),
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
