package dev.stan.yotsuba.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.clearField
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.BoardProfile
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.iconInRow
import dev.stan.yotsuba.inRow
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openBoardsTab
import dev.stan.yotsuba.openCatalog
import dev.stan.yotsuba.shell.tapTab
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForContentDescriptionGone
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

@HiltAndroidTest
class CatalogFlowTest : FlowTest() {

    private val layout get() = fakes.settings.state.value.catalogLayout

    private fun seedLongCatalog() {
        fakes.catalog.catalogs[TestSeed.BOARD] = longCatalog(TestSeed.BOARD)
    }

    /** The seeded threads carry no thumbnail, and a blur needs a picture to hide. */
    private fun seedThumbnails() {
        for (board in listOf(TestSeed.BOARD, TestSeed.VIDEO_BOARD)) {
            fakes.catalog.catalogs[board] = fakes.catalog.catalogs.getValue(board)
                .map { it.copy(thumbnailUrl = "https://example.invalid/${it.no}.jpg") }
        }
    }

    private fun blurBoard(board: String) = fakes.settings.set {
        it.copy(boardProfiles = mapOf(board to BoardProfile(blurThumbnails = true)))
    }

    /** Whether the card titled [title] still hides its thumbnail: one node or none. */
    private fun blursIn(title: String) = composeRule
        .onAllNodes(inRow(title, BLURRED), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun cards_showTitlesMetadataAndBadges() {
        composeRule.openCatalog()
        composeRule.waitForText(TestSeed.OP_TEXT)
        composeRule.waitForText("7 replies")
        composeRule.waitForText("2 images")
        // The board's own title leads the top bar, its code the subtitle.
        composeRule.waitForText("/${TestSeed.BOARD}/", substring = false)
        composeRule.waitForContentDescription("Sticky")
        composeRule.waitForContentDescription("Closed")
    }

    @Test
    fun visitedThread_badgesHowManyRepliesAreNew() {
        fakes.catalog.catalogs[TestSeed.BOARD] = listOf(
            TestSeed.catalogThread(
                TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.THREAD_SUBJECT, TestSeed.OP_TEXT,
                replyCount = 7, lastReplyNos = listOf(1_001L, 1_002L, 1_003L),
            ),
        )
        fakes.history.seed(TestSeed.historyEntry(lastScrollPostNo = 1_001L))
        composeRule.openCatalog()
        composeRule.waitForText("+2 new")

        // Read no further than the OP: the catalog lists only the last few replies, so the
        // count it can prove is a floor.
        fakes.history.seed(TestSeed.historyEntry())
        composeRule.waitForText("3+ new")
    }

    @Test
    fun search_filtersThreads_andClosingRestoresThem() {
        composeRule.openCatalog()
        composeRule.tapIcon("Search")
        composeRule.waitForText("Search this catalog")
        composeRule.typeInField("zzz-no-such-thread")
        composeRule.waitForText("No matches")

        composeRule.clearField()
        composeRule.typeInField("Yotsuba")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForTextGone(TestSeed.STICKY_SUBJECT)

        // The toggle became a close button: it drops the query and the field.
        composeRule.tapIcon("Search")
        composeRule.waitForTextGone("Search this catalog")
        composeRule.waitForText(TestSeed.STICKY_SUBJECT)
    }

    @Test
    fun layoutCycler_walksAllThreeLayouts_andPersistsEach() {
        assertEquals(CatalogLayout.COMFORTABLE, layout)
        composeRule.openCatalog()

        composeRule.tapIcon("Comfortable layout. Switch to compact")
        composeRule.waitUntilTrue { layout == CatalogLayout.COMPACT }
        composeRule.tapIcon("Compact layout. Switch to list")
        composeRule.waitUntilTrue { layout == CatalogLayout.LIST }
        composeRule.tapIcon("List layout. Switch to comfortable")
        composeRule.waitUntilTrue { layout == CatalogLayout.COMFORTABLE }
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    @Test
    fun pullToRefresh_refetchesPastTheCache() {
        composeRule.openCatalog()
        composeRule.waitUntilTrue { fakes.catalog.calls.contains(TestSeed.BOARD to false) }
        composeRule.catalogGrid().performTouchInput { swipeDown() }
        composeRule.waitUntilTrue { fakes.catalog.calls.contains(TestSeed.BOARD to true) }
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    @Test
    fun scrollToTopButton_appearsAfterScrolling_andReturnsToTheTop() {
        seedLongCatalog()
        composeRule.openCatalog(firstThread = FIRST_FILLER)
        composeRule.catalogGrid().performScrollToIndex(20)
        composeRule.waitForContentDescription("Scroll to top")

        composeRule.tapIcon("Scroll to top")
        composeRule.waitForContentDescriptionGone("Scroll to top")
        composeRule.nodeWithText(FIRST_FILLER).assertIsDisplayed()
    }

    @Test
    fun homePane_keepsScrollPosition_acrossTabSwitches() {
        seedLongCatalog()
        fakes.settings.set { it.copy(favouriteBoards = setOf(TestSeed.BOARD)) }
        composeRule.waitForText(FIRST_FILLER)
        composeRule.catalogGrid().performScrollToIndex(20)
        composeRule.waitForContentDescription("Scroll to top")

        composeRule.openBoardsTab()
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.tapTab("Home")
        // The button only shows past nine scrolled items, so it is proof the pane came back
        // where it was rather than at the top.
        composeRule.waitForContentDescription("Scroll to top")
    }

    @Test
    fun blurredThumbnail_isRevealedByTappingItsOwnCard() {
        seedThumbnails()
        blurBoard(TestSeed.BOARD)
        composeRule.openCatalog()
        composeRule.waitForContentDescription(BLURRED)

        composeRule.iconInRow(TestSeed.THREAD_SUBJECT, BLURRED).performClick()
        composeRule.waitUntilTrue { blursIn(TestSeed.THREAD_SUBJECT) == 0 }
        // The tap reveals one thumbnail, not the board's.
        assertEquals(1, blursIn(TestSeed.STICKY_SUBJECT))
    }

    @Test
    fun blurSetting_appliesPerBoard_orGloballyWhenNoProfileDoes() {
        seedThumbnails()
        blurBoard(TestSeed.BOARD)
        composeRule.openCatalog()
        composeRule.waitForContentDescription(BLURRED)

        pressBack()
        composeRule.tap(TestSeed.VIDEO_BOARD_TITLE)
        composeRule.waitForText(TestSeed.VIDEO_SUBJECT)
        composeRule.waitForContentDescriptionGone(BLURRED)

        // The global setting covers the board that has no profile of its own.
        fakes.settings.set { it.copy(blurThumbnails = true) }
        composeRule.waitForContentDescription(BLURRED)
    }

    private companion object {
        const val BLURRED = "Blurred thumbnail. Tap to show"
    }
}
