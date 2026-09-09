package dev.stan.yotsuba.vault

import androidx.compose.ui.test.assertIsSelected
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForContentDescriptionGone
import dev.stan.yotsuba.waitForText
import org.junit.Assert.assertFalse
import org.junit.Test

/** The search bar over the whole vault, in both scopes. */
@HiltAndroidTest
class VaultSearchFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.entries.toTypedArray())

    private fun openSearch() {
        composeRule.openVault()
        composeRule.tapIcon("Search")
        composeRule.waitForText("File name or thread subject")
    }

    @Test
    fun search_byFileName_keepsOnlyThatFile() {
        openSearch()
        composeRule.typeInField("spoiler")
        composeRule.waitForGridOrder(listOf(VaultSeed.spoilerImage.displayName))
        composeRule.segment("Files").assertIsSelected()
    }

    @Test
    fun search_bySubject_keepsEveryFileOfThatThread() {
        openSearch()
        composeRule.typeInField(TestSeed.VIDEO_SUBJECT)
        composeRule.waitForGridOrder(VaultSeed.videos)
    }

    @Test
    fun threadsScope_listsThreads_andATapEndsTheSearchInsideOne() {
        openSearch()
        composeRule.typeInField("thread")
        composeRule.tapSegment("Threads")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText(TestSeed.STICKY_SUBJECT)
        composeRule.waitForText(TestSeed.VIDEO_SUBJECT)

        composeRule.tap(TestSeed.VIDEO_SUBJECT)
        composeRule.waitForContentDescription("Back")
        composeRule.waitForContentDescription("Search")
        composeRule.waitForGridOrder(VaultSeed.videos)
        assertFalse(composeRule.hasText("File name or thread subject"))
    }

    @Test
    fun noMatch_showsNothingMatches_clearEmptiesIt_andBackClosesSearch() {
        openSearch()
        composeRule.typeInField("zzz")
        composeRule.waitForText("Nothing matches")
        composeRule.tapIcon("Clear")
        composeRule.waitForContentDescriptionGone("Clear")
        composeRule.waitForGridOrder(VaultSeed.newestFirst)

        pressBack()
        composeRule.waitForContentDescription("Search")
        composeRule.waitForText("Saved media")
        assertFalse(composeRule.hasText("File name or thread subject"))
    }
}
