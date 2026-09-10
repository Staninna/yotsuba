package dev.stan.yotsuba.catalog

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openCatalog
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Test

@HiltAndroidTest
class CatalogActionsFlowTest : FlowTest() {

    private val hidden get() = fakes.hidden.state.value

    /** The long-press sheet for one card, reached by pressing its title. */
    private fun openSheetFor(title: String) {
        composeRule.nodeWithText(title).performTouchInput { longClick() }
        composeRule.waitForText("Hide thread")
    }

    @Test
    fun hideThread_removesTheCard_andUndoBringsItBack() {
        composeRule.openCatalog()
        openSheetFor(TestSeed.THREAD_SUBJECT)
        composeRule.tap("Hide thread")
        composeRule.waitForText("Thread hidden")
        composeRule.waitForTextGone(TestSeed.THREAD_SUBJECT)
        composeRule.waitUntilTrue { hidden.any { it.threadNo == TestSeed.THREAD_NO } }

        composeRule.tap("Undo")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.waitUntilTrue { hidden.isEmpty() }
    }

    @Test
    fun copyLink_reportsItCopied() {
        composeRule.openCatalog()
        openSheetFor(TestSeed.THREAD_SUBJECT)
        composeRule.tap("Copy link")
        composeRule.waitForText("Link copied")
        // The card is still there: copying is not hiding.
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    @Test
    fun openInBrowser_isOfferedForEveryCard() {
        composeRule.openCatalog()
        openSheetFor(TestSeed.STICKY_SUBJECT)
        // Tapping leaves the app for a browser, which Compose cannot follow: assert the
        // boundary, the offered action itself.
        composeRule.nodeWithText("Open in browser").assertIsDisplayed().assertHasClickAction()
        pressBack()
        composeRule.waitForTextGone("Open in browser")
    }

    @Test
    fun filters_hideStubAndCount() {
        fakes.settings.set {
            it.copy(
                filters = listOf(
                    Filter(id = "hide", pattern = "Sticky OP", action = FilterAction.HIDE),
                    Filter(id = "stub", pattern = "Nothing more", action = FilterAction.STUB),
                ),
            )
        }
        composeRule.openCatalog()

        composeRule.waitForTextGone(TestSeed.STICKY_SUBJECT)
        composeRule.waitForText("Filtered: Nothing more")
        composeRule.waitForText("2 filtered")
        // Both the hidden and the stubbed thread count, but only the stub can be opened.
        composeRule.tap("Filtered: Nothing more")
        composeRule.waitForText(TestSeed.CLOSED_SUBJECT)
        composeRule.waitForText(TestSeed.CLOSED_OP_TEXT)
    }
}
