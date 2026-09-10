package dev.stan.yotsuba.vault

import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.iconInRow
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A file a rescan found gone from disk: how the explorer marks it, and the three routes
 * that offer to fetch it back.
 */
@HiltAndroidTest
class VaultMissingFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.withMissing.toTypedArray())

    private val seededThread = VaultLocation(TestSeed.BOARD, TestSeed.THREAD_NO)

    /** The Sync menu's re-download, which works through every thread with a missing file. */
    private fun redownloadEverything() {
        composeRule.openVault()
        composeRule.tapIcon("Sync")
        composeRule.tap("Re-download missing")
    }

    @Test
    fun missingFile_isMarkedInTheGrid_andCountedOnItsThreadRow() {
        composeRule.openVault()
        composeRule.waitForContentDescription(VaultSeed.missingImage.displayName)
        composeRule.waitForContentDescription("Missing from disk")

        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        // Three files on the row, and the 5 MB the gone one records is not in its size.
        composeRule.waitForText("3 files · 19 KB")
        composeRule.waitForText("1 missing")
    }

    @Test
    fun syncMenu_redownload_asksForTheThreadThatLostAFile_andReports() {
        fakes.vault.syncSummary = VaultSyncSummary(redownloaded = 1, unrecoverable = 2)
        redownloadEverything()
        composeRule.waitUntilTrue { fakes.vault.redownloadCalls.isNotEmpty() }
        assertEquals(listOf(listOf(seededThread)), fakes.vault.redownloadCalls)
        composeRule.waitForText("Fetched 1 file back, 2 unrecoverable.")
    }

    @Test
    fun redownload_thatFetchesNothingBack_namesTheUnrecoverableFile() {
        fakes.vault.syncSummary = VaultSyncSummary(unrecoverable = 1)
        redownloadEverything()
        composeRule.waitForText("Fetched 0 files back, 1 unrecoverable.")
    }

    @Test
    fun threadRowMenu_redownloadsThatThread() {
        composeRule.openVault()
        composeRule.openVaultBoard("/${TestSeed.BOARD}/")
        composeRule.iconInRow(TestSeed.THREAD_SUBJECT, "More").performClick()
        composeRule.tap("Re-download missing")
        composeRule.waitUntilTrue { fakes.vault.redownloadCalls.isNotEmpty() }
        assertEquals(listOf(listOf(seededThread)), fakes.vault.redownloadCalls)
    }

    @Test
    fun entrySheet_redownload_reportsAPassThatFoundNoSource() {
        composeRule.openVault()
        composeRule.openEntrySheet(VaultSeed.missingImage.displayName)
        composeRule.tap("Re-download missing")
        composeRule.waitUntilTrue { fakes.vault.redownloadCalls.isNotEmpty() }
        assertEquals(listOf(listOf(seededThread)), fakes.vault.redownloadCalls)
        // The default summary fetched nothing back and found nothing to give up on either.
        composeRule.waitForText("Couldn't fetch any file back")
    }
}
