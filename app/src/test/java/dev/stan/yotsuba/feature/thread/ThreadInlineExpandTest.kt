package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** With [Settings.inlineImageExpansion] on, a still image expands in the card; everything else keeps the viewer. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadInlineExpandTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val still = ThreadEnv.postWithMedia(101)
    private val media = still.presentMedia!!
    private val on = Settings(inlineImageExpansion = true)

    private fun withExt(ext: String, soundUrl: String? = null) =
        media.copy(ext = ext, soundUrl = soundUrl)

    @Test fun `setting off keeps the viewer`() {
        assertEquals(ThumbnailTap.OPEN_VIEWER, thumbnailTap(media, 101, Session(), Settings()))
    }

    @Test fun `setting on expands a still image and collapses it again`() {
        assertEquals(ThumbnailTap.EXPAND, thumbnailTap(media, 101, Session(), on))
        val expanded = Session(expandedImages = setOf(101))
        assertEquals(ThumbnailTap.COLLAPSE, thumbnailTap(media, 101, expanded, on))
        // Turning the setting off mid-thread must not leave a card stuck open.
        assertEquals(ThumbnailTap.COLLAPSE, thumbnailTap(media, 101, expanded, Settings()))
    }

    @Test fun `videos, gifs and sound posts still open the viewer`() {
        assertEquals(ThumbnailTap.OPEN_VIEWER, thumbnailTap(withExt(".webm"), 101, Session(), on))
        assertEquals(ThumbnailTap.OPEN_VIEWER, thumbnailTap(withExt(".mp4"), 101, Session(), on))
        assertEquals(ThumbnailTap.OPEN_VIEWER, thumbnailTap(withExt(".gif"), 101, Session(), on))
        assertEquals(
            ThumbnailTap.OPEN_VIEWER,
            thumbnailTap(withExt(".png", soundUrl = "https://example.com/a.mp3"), 101, Session(), on),
        )
    }

    @Test fun `a spoiler reveals first, then expands`() {
        val spoilered = media.copy(spoiler = true)
        assertEquals(ThumbnailTap.REVEAL_SPOILER, thumbnailTap(spoilered, 101, Session(), on))
        val revealed = Session(revealedImages = setOf(101))
        assertEquals(ThumbnailTap.EXPAND, thumbnailTap(spoilered, 101, revealed, on))
        assertEquals(
            ThumbnailTap.EXPAND,
            thumbnailTap(spoilered, 101, Session(), on.copy(revealAllSpoilers = true)),
        )
    }

    @Test fun `the view model toggles the session and the card state, never the viewer`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv(posts = listOf(ThreadEnv.post(100), still))
        env.settings.state.value = on.copy(dataSaver = true)
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onThumbnailTap(still)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(101L), vm.session.value.expandedImages)
        assertNull(vm.mediaToOpen.value)
        val state = content(vm).postStates.getValue(101)
        assertTrue(state.imageExpanded)
        assertEquals(InlineImage(localPath = null, dataSaver = true), state.inlineImage)
        assertFalse(content(vm).postStates.getValue(100).imageExpanded)

        vm.onThumbnailTap(still)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptySet<Long>(), vm.session.value.expandedImages)
        assertNull(content(vm).postStates.getValue(101).inlineImage)
        assertNull(vm.mediaToOpen.value)
    }

    @Test fun `a tap on the expanded image opens the viewer on that post`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv(posts = listOf(ThreadEnv.post(100), still))
        env.settings.state.value = on
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onThumbnailTap(still)
        vm.onExpandedImageTap(still)
        assertEquals(101L, vm.mediaToOpen.value)
        // A post without media has nothing to open.
        vm.onMediaOpened()
        vm.onExpandedImageTap(ThreadEnv.post(100))
        assertNull(vm.mediaToOpen.value)
        assertTrue(still.media is PostMedia.Present)
    }
}
