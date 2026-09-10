package dev.stan.yotsuba.vault

import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import org.junit.Test

/** The statistics sheet, empty and full. */
@HiltAndroidTest
class VaultStatsSheetFlowTest : FlowTest() {

    private fun openStats() {
        composeRule.openVault()
        composeRule.tapIcon("More")
        composeRule.tap("What the vault holds, by board and thread")
        composeRule.waitForText("Vault statistics", substring = false)
    }

    @Test
    fun emptyVault_saysNothingSavedYet() {
        openStats()
        composeRule.waitForText("Nothing saved yet")
    }

    @Test
    fun seededVault_rendersEverySection() {
        fakes.vault.seed(*VaultSeed.entries.toTypedArray())
        openStats()
        // Totals: eight files over five threads and four boards, five of them images.
        composeRule.waitForText("Files", substring = false)
        composeRule.waitForText("Size", substring = false)
        composeRule.waitForText("Threads", substring = false)
        composeRule.waitForText("Images", substring = false)
        composeRule.waitForText("Videos", substring = false)
        composeRule.waitForText("Boards", substring = false)

        composeRule.waitForText("By board")
        composeRule.waitForText("3 files · 3.9 MB")
        composeRule.scrollSheetTo("Vault statistics", "Biggest threads")
        composeRule.scrollSheetTo("Vault statistics", "/v/ · 3 files · 3.9 MB")
        composeRule.scrollSheetTo("Vault statistics", "Saved per week")
        composeRule.scrollSheetTo("Vault statistics", "Last 12 weeks")
        composeRule.scrollSheetTo("Vault statistics", "Oldest save")
        composeRule.waitForText("Newest save")
    }

    @Test
    fun biggestThreadRow_revealsThatThreadInTheExplorer() {
        fakes.vault.seed(*VaultSeed.entries.toTypedArray())
        openStats()
        composeRule.scrollSheetTo("Vault statistics", "/v/ · 3 files · 3.9 MB")
        composeRule.nodeWithText(TestSeed.VIDEO_SUBJECT, substring = false).performClick()

        // The sheet closes and the explorer lands inside the thread, three levels deep in one step.
        composeRule.waitForContentDescription("Back")
        composeRule.waitForText("3 files · 3.9 MB")
        VaultSeed.videos.forEach { composeRule.waitForContentDescription(it) }
    }
}
