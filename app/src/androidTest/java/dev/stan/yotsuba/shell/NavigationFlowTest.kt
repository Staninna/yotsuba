package dev.stan.yotsuba.shell

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openBoardsTab
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openSeededViewer
import dev.stan.yotsuba.openThreadsTab
import dev.stan.yotsuba.openVaultTab
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
        composeRule.tapTab("Home")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.tab("Home").assertIsSelected()
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
        composeRule.tapTab("Home")
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
        composeRule.tapTab("Threads")
        composeRule.waitForContentDescription("Clear all")
        composeRule.nodeWithText("Recent", substring = false).assertIsSelected()
    }

    @Test
    fun pushedScreens_hideBottomBar_andBackRestoresIt() {
        composeRule.openSeededThread()
        composeRule.waitForTextGone("Saved", substring = false)
        // The viewer is pushed on top of the thread; the bar stays hidden all the way down.
        composeRule.openSeededViewer()
        assertFalse(composeRule.hasText("Saved", substring = false))
        pressBack()
        composeRule.waitForText(TestSeed.OP_TEXT)
        pressBack()
        composeRule.waitForText(TestSeed.STICKY_SUBJECT)
        assertFalse(composeRule.hasText("Saved", substring = false))
        composeRule.goBack()
        composeRule.waitForText("Saved", substring = false)
        composeRule.tab("Boards").assertIsSelected()
    }

    @Test
    fun recreate_keepsCurrentTab() {
        composeRule.openVaultTab()
        composeRule.waitForText("Vault is empty")
        recreate()
        composeRule.waitForText("Vault is empty")
        composeRule.tab("Saved").assertIsSelected()
    }

    @Test
    fun recreate_keepsOpenThread() {
        composeRule.openSeededThread()
        recreate()
        composeRule.waitForText(TestSeed.OP_TEXT)
        composeRule.waitForText(TestSeed.REPLY_TEXT)
    }
}
