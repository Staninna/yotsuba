package dev.stan.yotsuba.shell

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextContains
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.hasContentDescription
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.openBoardsTab
import dev.stan.yotsuba.openHomeTab
import dev.stan.yotsuba.openCatalog
import dev.stan.yotsuba.openThreadsTab
import dev.stan.yotsuba.openVaultTab
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.textField
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import org.junit.Assert.assertFalse
import org.junit.Test

@HiltAndroidTest
class NavigationFlowTest : FlowTest() {

    /**
     * The bottom-bar item for [label] is the selected one. Local to this class: the shared
     * helpers have no matcher-shaped `hasText`, and a tab label doubles as a screen title.
     */
    private fun assertTabSelected(label: String) {
        composeRule.onNode(hasText(label, substring = false) and hasClickAction()).assertIsSelected()
    }

    /** One favourite, so Home shows a catalog and its title is the board's, not "Home". */
    override fun seed() {
        fakes.settings.set { it.copy(favouriteBoards = setOf(TestSeed.BOARD)) }
    }

    @Test
    fun bottomBar_reachesEveryTab() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.openThreadsTab()
        composeRule.waitForText("No bookmarks yet")
        composeRule.openVaultTab()
        composeRule.waitForText("Vault is empty")
        composeRule.openHomeTab()
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        assertTabSelected("Home")
    }

    @Test
    fun homeSearchQuery_survivesSwitchingTabs() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.tapIcon("Search")
        composeRule.waitForText("Search this catalog")
        composeRule.typeInField("Yotsuba")
        composeRule.waitForTextGone(TestSeed.STICKY_SUBJECT)

        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.openHomeTab()
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.textField().assertTextContains("Yotsuba")
        assertFalse(composeRule.hasText(TestSeed.STICKY_SUBJECT))
    }

    @Test
    fun threadsSegment_survivesSwitchingTabs() {
        composeRule.openThreadsTab("Recent")
        composeRule.waitForContentDescription("Clear all")
        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.openThreadsTab(select = false)
        composeRule.waitForContentDescription("Clear all")
        // Recent's own actions are up, so the segment came back selected, not Watched.
        assertFalse(composeRule.hasContentDescription("Bookmark options"))
    }

    @Test
    fun pushedScreens_hideBottomBar_andSystemBackRestoresIt() {
        // Opened from the Home pane: /g/ is a favourite here, so its title is on the Boards
        // tab twice, under Favourites and again in its category, and cannot be tapped by name.
        composeRule.tap(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText(TestSeed.OP_TEXT)
        composeRule.waitForTextGone("Saved", substring = false)
        // The viewer is pushed on top of the thread; the bar stays hidden all the way down.
        composeRule.tapIcon(TestSeed.MEDIA_FILENAME, substring = true)
        composeRule.waitForContentDescription("Close viewer")
        assertFalse(composeRule.hasText("Saved", substring = false))
        pressBack()
        composeRule.waitForText(TestSeed.OP_TEXT)
        pressBack()
        composeRule.waitForText("Saved", substring = false)
        assertTabSelected("Home")
    }

    @Test
    fun pushedCatalog_hidesTheBottomBar_untilBackReturnsToTheTab() {
        // /v/ is no favourite here, so its title is on the Boards tab exactly once.
        composeRule.openCatalog(TestSeed.VIDEO_BOARD_TITLE, TestSeed.VIDEO_SUBJECT)
        assertFalse(composeRule.hasText("Saved", substring = false))
        composeRule.goBack()
        composeRule.waitForText("Saved", substring = false)
        assertTabSelected("Boards")
    }

    @Test
    fun recreate_keepsCurrentTab() {
        composeRule.openVaultTab()
        composeRule.waitForText("Vault is empty")
        recreate()
        composeRule.waitForText("Vault is empty")
        assertTabSelected("Saved")
    }

    @Test
    fun recreate_keepsOpenThread() {
        composeRule.tap(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText(TestSeed.OP_TEXT)
        recreate()
        composeRule.waitForText(TestSeed.OP_TEXT)
        composeRule.waitForText(TestSeed.REPLY_TEXT)
    }
}
