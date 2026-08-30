package dev.stan.yotsuba

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Test

@HiltAndroidTest
class BookmarksFlowTest : FlowTest() {

    @Test
    fun bookmarkToggle_showsInBookmarksTab_thenRemove() {
        composeRule.openSeededThread()

        // Toggle the bookmark on from the thread top bar.
        composeRule.waitForContentDescription("Bookmark")
        composeRule.onNodeWithContentDescription("Bookmark").performClick()
        composeRule.waitForContentDescription("Remove bookmark")

        // Back to catalog, then jump to the Threads tab; Watched is the default segment.
        composeRule.openThreadsTab("Watched", select = false)
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)

        // Re-open the thread from the bookmark card and toggle the bookmark off.
        composeRule.clickText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForContentDescription("Remove bookmark")
        composeRule.onNodeWithContentDescription("Remove bookmark").performClick()
        composeRule.waitForContentDescription("Bookmark")
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Watched list is empty again.
        composeRule.waitForText("No bookmarks yet")
    }
}
