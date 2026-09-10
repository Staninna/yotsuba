package dev.stan.yotsuba.vault

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** The browse bar's four menus and the settings gear, from the Recent root. */
@HiltAndroidTest
class VaultTopBarFlowTest : FlowTest() {

    override fun seed() = fakes.vault.seed(*VaultSeed.entries.toTypedArray())

    /** Taps Sync, picks [item], and waits for the pass to finish. */
    private fun sync(item: String) {
        composeRule.openVault()
        composeRule.tapIcon("Sync")
        composeRule.tap(item)
    }

    @Test
    fun importMenu_offersFilesAndFolder() {
        composeRule.openVault()
        composeRule.tapIcon("Import")
        // Both entries hand over to a system picker, so the menu is as far as this suite sees.
        composeRule.waitForText("Import photos or videos")
        composeRule.waitForText("Import a folder")
    }

    @Test
    fun syncMenu_rescan_rebuildsTheIndex() {
        composeRule.openVault()
        composeRule.tapIcon("Sync")
        composeRule.waitForText("Rebuild the index from the files on disk")
        composeRule.waitForText("Refresh saved threads from 4chan, one a second")
        composeRule.tap("Rescan", substring = false)
        composeRule.waitUntilTrue { fakes.vault.rescanCalls == 1 }
        composeRule.waitForText("Index rebuilt")
    }

    @Test
    fun fetchReplies_withNothingToSync_saysSo() {
        sync("Fetch new replies")
        composeRule.waitUntilTrue { fakes.vault.syncCalls == 1 }
        composeRule.waitForText("Nothing saved to sync yet.")
    }

    @Test
    fun fetchReplies_rateLimited_asksToTryAgain() {
        fakes.vault.syncSummary = VaultSyncSummary(updated = 1, rateLimited = true)
        sync("Fetch new replies")
        composeRule.waitForText("4chan asked us to slow down.")
    }

    @Test
    fun fetchReplies_reportsWhatItRefreshed() {
        fakes.vault.syncSummary = VaultSyncSummary(updated = 2, gone = 1)
        fakes.vault.syncProgressSteps = 3
        sync("Fetch new replies")
        composeRule.waitForText("Refreshed 2 threads, 1 already gone.")
        assertEquals(1, fakes.vault.syncCalls)
    }

    @Test
    fun moreMenu_offersTheThreeTools() {
        composeRule.openVault()
        composeRule.tapIcon("More")
        composeRule.waitForText("What the vault holds, by board and thread")
        composeRule.waitForText("Files saved twice, or images that look alike")
        composeRule.waitForText("Deleted files, kept for a week")
    }

    @Test
    fun settingsGear_opensSettings() {
        composeRule.openVault()
        composeRule.tapIcon("Settings")
        composeRule.waitForText("Appearance")
    }
}
