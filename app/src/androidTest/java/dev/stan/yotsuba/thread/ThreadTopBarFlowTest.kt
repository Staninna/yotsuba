package dev.stan.yotsuba.thread

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** The thread's top bar: bookmark, refresh, and every entry of the overflow menu. */
@HiltAndroidTest
class ThreadTopBarFlowTest : FlowTest() {

    private val key = TestSeed.BOARD to TestSeed.THREAD_NO

    @Test
    fun bookmarkToggle_addsThenRemovesTheBookmark() {
        composeRule.openSeededThread()
        composeRule.tapIcon("Bookmark")
        composeRule.waitUntilTrue { fakes.bookmarks.state.value.any { it.threadNo == TestSeed.THREAD_NO } }
        assertEquals(TestSeed.THREAD_SUBJECT, fakes.bookmarks.state.value.single().subject)

        composeRule.tapIcon("Remove bookmark")
        composeRule.waitUntilTrue { fakes.bookmarks.state.value.isEmpty() }
        composeRule.waitForContentDescription("Bookmark", substring = false)
    }

    @Test
    fun refreshButton_andPullToRefresh_bypassTheCache() {
        composeRule.openSeededThread()
        composeRule.tapIcon("Refresh")
        composeRule.waitUntilTrue { fakes.threads.calls.last() == Triple(TestSeed.BOARD, TestSeed.THREAD_NO, true) }

        val before = fakes.threads.calls.size
        composeRule.threadListNode().performTouchInput { swipeDown() }
        composeRule.waitUntilTrue { fakes.threads.calls.size > before && fakes.threads.calls.last().third }
    }

    @Test
    fun copyLink_putsTheThreadUrlOnTheClipboard() {
        composeRule.openSeededThread()
        composeRule.tapMenuItem("Copy link")
        // The item closes the menu as it copies. Reading the clipboard before that has settled
        // reads it from behind a popup, and a timeout here then says nothing about the copy.
        composeRule.waitForTextGone("Copy link", substring = false)
        composeRule.waitUntilTrue { clipboardText() == Urls.threadWebUrl(TestSeed.BOARD, TestSeed.THREAD_NO) }
    }

    @Test
    fun saveAllMedia_queuesEverySeededFile() {
        composeRule.openSeededThread()
        composeRule.tapMenuItem("Save all media (2)")
        composeRule.waitForText("Saving these files")
        composeRule.waitUntilTrue { fakes.vault.saves.size == 2 }
        assertEquals(
            setOf(TestSeed.mediaItem.fullUrl, TestSeed.spoilerMediaItem.fullUrl),
            fakes.vault.saves.map { (item, _) -> item.fullUrl }.toSet(),
        )
        assertEquals(key, fakes.vault.saves.first().second.let { it.board to it.threadNo })
    }

    @Test
    fun saveAllMedia_isDisabledOnATextOnlyThread() {
        composeRule.openThread(TestSeed.BOARD_TITLE, TestSeed.CLOSED_SUBJECT, TestSeed.CLOSED_OP_TEXT)
        composeRule.tapIcon("More options")
        composeRule.nodeWithText("Save all media (0)", substring = false).assertIsNotEnabled()
    }

    @Test
    fun treeViewAndAutoRefresh_showTheirCheckState() {
        composeRule.openSeededThread()
        composeRule.tapIcon("More options")
        composeRule.nodeWithText("Tree view").assertIsOff()
        composeRule.nodeWithText("Auto-refresh").assertIsOff()
        composeRule.tap("Tree view")

        composeRule.tapIcon("More options")
        composeRule.nodeWithText("Tree view").assertIsOn()
        composeRule.tap("Auto-refresh")

        composeRule.tapIcon("More options")
        composeRule.nodeWithText("Auto-refresh").assertIsOn()
    }

    @Test
    fun unreadOnly_isDisabledInAThreadThatWasNeverRead() {
        composeRule.openSeededThread()
        composeRule.tapIcon("More options")
        composeRule.nodeWithText("Unread only", substring = false).assertIsNotEnabled()
    }

    /** The read mark comes from history, and the cut keeps the OP so the thread keeps its header. */
    @Test
    fun unreadOnly_dropsWhatWasReadLastTime_andPutsItBack() {
        fakes.history.readMarks[key] = TestSeed.THREAD_NO + 3
        composeRule.openSeededThread()

        composeRule.tapIcon("More options")
        composeRule.nodeWithText("Unread only", substring = false).assertIsOff()
        composeRule.tap("Unread only", substring = false)

        composeRule.waitForTextGone(TestSeed.REPLY_TEXT)
        composeRule.listNode(TestSeed.OP_TEXT).assertIsDisplayed()
        composeRule.listNode(TestSeed.LINK_REPLY_TEXT).assertIsDisplayed()

        composeRule.tapIcon("More options")
        composeRule.nodeWithText("Unread only", substring = false).assertIsOn()
        composeRule.tap("Unread only", substring = false)
        composeRule.waitForText(TestSeed.REPLY_TEXT)
    }

    /** The share sheet belongs to the system; the test stops at the tap that opens it. */
    @Test
    fun share_leavesForTheSystemSheet() {
        composeRule.openSeededThread()
        composeRule.tapMenuItem("Share")
    }

    @Test
    fun openInBrowser_leavesForTheBrowser() {
        composeRule.openSeededThread()
        composeRule.tapMenuItem("Open in browser")
    }

    private fun clipboardText(): String? {
        var text: CharSequence? = null
        onActivity { text = it.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text }
        return text?.toString()
    }
}
