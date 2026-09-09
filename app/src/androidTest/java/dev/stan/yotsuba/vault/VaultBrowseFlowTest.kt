package dev.stan.yotsuba.vault

import androidx.compose.ui.test.assertIsSelected
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.hasContentDescription
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForContentDescriptionGone
import dev.stan.yotsuba.waitForText
import org.junit.Assert.assertFalse
import org.junit.Test

/** The two root modes and the board → thread → grid drill-down. */
@HiltAndroidTest
class VaultBrowseFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.entries.toTypedArray())

    @Test
    fun browse_listsEveryBoardWithItsCountAndSize() {
        composeRule.openVault()
        composeRule.waitForText("8 files")
        composeRule.tapSegment("Browse")
        composeRule.waitForText("/g/", substring = false)
        composeRule.waitForText("3 files · 23 KB")
        composeRule.waitForText("/v/", substring = false)
        composeRule.waitForText("3 files · 3.9 MB")
        composeRule.waitForText("Imported", substring = false)
        composeRule.waitForText("1 file · 1000 B")
        composeRule.waitForText("Unsorted", substring = false)
        composeRule.waitForText("1 file · 2 KB")
    }

    @Test
    fun boardTap_listsThreads_andThreadTap_showsItsGrid() {
        composeRule.openVault()
        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText(TestSeed.STICKY_SUBJECT)
        composeRule.waitForText("2 files · 19 KB")
        composeRule.waitForContentDescription("Back")

        composeRule.tap(TestSeed.THREAD_SUBJECT)
        composeRule.waitForContentDescription(VaultSeed.spoilerImage.displayName)
        composeRule.waitForContentDescription(VaultSeed.seededImage.displayName)
        composeRule.waitForContentDescriptionGone(VaultSeed.stickyPic.displayName)
        composeRule.waitForText("2 files")
    }

    @Test
    fun upArrow_thenSystemBack_walkOutOneLevelAtATime() {
        composeRule.openSeededVaultThread()
        composeRule.goBack()
        composeRule.waitForText(TestSeed.STICKY_SUBJECT)
        composeRule.waitForContentDescriptionGone(VaultSeed.spoilerImage.displayName)

        pressBack()
        composeRule.waitForText("/${TestSeed.VIDEO_BOARD}/", substring = false)
        composeRule.waitForContentDescriptionGone("Back")
        composeRule.segment("Browse").assertIsSelected()
    }

    @Test
    fun recent_showsTheFlatGridNewestFirst() {
        composeRule.openVault()
        composeRule.segment("Recent").assertIsSelected()
        composeRule.waitForGridOrder(VaultSeed.newestFirst)
    }

    @Test
    fun browseMode_survivesRecreate() {
        composeRule.openVault()
        composeRule.tapSegment("Browse")
        composeRule.waitForText("/${TestSeed.BOARD}/", substring = false)
        recreate()
        composeRule.waitForText("/${TestSeed.BOARD}/", substring = false)
        composeRule.segment("Browse").assertIsSelected()
        assertFalse(composeRule.hasContentDescription(VaultSeed.seededImage.displayName))
    }
}
