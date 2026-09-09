package dev.stan.yotsuba.vault

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import org.junit.Test

/** The chip row over every grid: sort, direction, type, and sound. */
@HiltAndroidTest
class VaultSortFilterFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.entries.toTypedArray())

    /** Opens the sort chip (found by its direction arrow) and picks [item] from its menu. */
    private fun pickSort(item: String) {
        composeRule.onNode(
            hasContentDescription("Newest or largest first") or hasContentDescription("Reversed"),
        ).performClick()
        composeRule.tap(item, substring = false)
    }

    @Test
    fun sortMenu_eachSort_reordersTheGrid() {
        composeRule.openVault()
        composeRule.waitForGridOrder(VaultSeed.newestFirst)
        pickSort("Size")
        composeRule.waitForGridOrder(VaultSeed.largestFirst)
        pickSort("Name")
        composeRule.waitForGridOrder(VaultSeed.byName)
        pickSort("Post number")
        composeRule.waitForGridOrder(VaultSeed.byPost)
        pickSort("Date saved")
        composeRule.waitForGridOrder(VaultSeed.newestFirst)
    }

    @Test
    fun reverseOrder_flipsTheGrid_andTheArrow() {
        composeRule.openVault()
        composeRule.waitForGridOrder(VaultSeed.newestFirst)
        pickSort("Reverse order")
        composeRule.waitForContentDescription("Reversed")
        composeRule.waitForGridOrder(VaultSeed.newestFirst.reversed())
        pickSort("Reverse order")
        composeRule.waitForContentDescription("Newest or largest first")
        composeRule.waitForGridOrder(VaultSeed.newestFirst)
    }

    @Test
    fun typeFilter_andAudioFilter_narrowTheGrid() {
        composeRule.openVault()
        composeRule.waitForGridOrder(VaultSeed.newestFirst)
        composeRule.tapIcon("Images")
        composeRule.waitForGridOrder(VaultSeed.images)
        composeRule.tapIcon("Videos")
        composeRule.waitForGridOrder(VaultSeed.videos)
        composeRule.tapSegment("With sound")
        composeRule.waitForGridOrder(VaultSeed.videosWithSound)
        composeRule.tapSegment("Silent")
        composeRule.waitForGridOrder(listOf(VaultSeed.silentClip.displayName))
        composeRule.tapSegment("Any")
        composeRule.waitForGridOrder(VaultSeed.videos)
        composeRule.tapIcon("All")
        composeRule.waitForGridOrder(VaultSeed.newestFirst)
    }

    @Test
    fun videosFilter_onAnImageOnlyThread_showsTheEmptyText() {
        composeRule.openSeededVaultThread()
        composeRule.tapIcon("Videos")
        composeRule.waitForText("Nothing here matches the filter")
        composeRule.tapIcon("All")
        composeRule.waitForGridOrder(listOf(VaultSeed.seededImage.displayName, VaultSeed.spoilerImage.displayName))
    }

    @Test
    fun sort_survivesRecreate() {
        composeRule.openVault()
        pickSort("Name")
        composeRule.waitForGridOrder(VaultSeed.byName)
        recreate()
        composeRule.waitForText("Name", substring = false)
        composeRule.waitForGridOrder(VaultSeed.byName)
    }
}
