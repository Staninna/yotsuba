package dev.stan.yotsuba.settings

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.repository.BackupInfo
import dev.stan.yotsuba.openSettingsSection
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

@HiltAndroidTest
class StorageFlowTest : FlowTest() {

    private fun openStorage() = composeRule.openSettingsSection("Storage & data", "Clear cache")

    /** Taps a destructive row and takes the "are you sure?" dialog's [answer]. */
    private fun confirm(row: String, answer: String = "Confirm") {
        composeRule.tapRow(row)
        composeRule.waitForText("Are you sure?")
        composeRule.tapRow(answer)
        composeRule.waitForTextGone("Are you sure?")
    }

    @Test
    fun clearCache_confirmed_clearsAndReports() {
        openStorage()
        confirm("Clear cache")
        composeRule.waitUntilTrue { fakes.maintenance.clearCalls == 1 }
        composeRule.waitForText("Cleared")
    }

    @Test
    fun clearCache_thatFails_saysWhy() {
        fakes.maintenance.failWith = RuntimeException("disk is on fire")
        openStorage()
        confirm("Clear cache")
        composeRule.waitForText("Couldn't clear: disk is on fire")
    }

    @Test
    fun clearHistory_cancelled_keepsTheHistory() {
        fakes.history.seed(TestSeed.historyEntry())
        openStorage()
        confirm("Clear history", answer = "Cancel")
        assertEquals(1, fakes.history.state.value.size)
    }

    @Test
    fun clearHistoryBookmarksAndTrustedDomains_emptyThemAll() {
        fakes.history.seed(TestSeed.historyEntry())
        fakes.bookmarks.seed(TestSeed.bookmark())
        fakes.settings.set { it.copy(trustedDomains = setOf("example.com")) }
        openStorage()

        confirm("Clear history")
        composeRule.waitUntilTrue { fakes.history.state.value.isEmpty() }
        confirm("Clear bookmarks")
        composeRule.waitUntilTrue { fakes.bookmarks.state.value.isEmpty() }
        confirm("Clear trusted domains")
        composeRule.waitUntilTrue { fakes.settings.state.value.trustedDomains.isEmpty() }
    }

    @Test
    fun everySwitchFlipsItsSetting() {
        openStorage()
        flip("Confirm before deleting saved media", false) { it.confirmVaultDelete }
        flip("Snapshot watched threads", false) { it.snapshotWatchedThreads }
        flip("Prune dead threads to saved conversations", true) { it.pruneDeadSidecars }
    }

    @Test
    fun galleryHiding_isOutOfReachWithoutStorageAccess() {
        fakes.vault.access.value = false
        openStorage()
        composeRule.waitForText("Hide saved media from the gallery")
        composeRule.waitForText("No storage access, so the vault folder is out of reach")
    }

    @Test
    fun exportNow_writesABackup_andSaysWhen() {
        openStorage()
        composeRule.tapRow("Export now")
        composeRule.waitUntilTrue { fakes.backup.exportCalls == 1 }
        composeRule.waitForText("Backup written")
    }

    @Test
    fun importFromBackup_confirmed_reportsWhatItRestored() {
        openStorage()
        confirm("Import from backup")
        composeRule.waitUntilTrue { fakes.backup.importCalls == 1 }
        composeRule.waitForText("Restored 2 bookmarks and 1 hidden threads")
    }

    @Test
    fun restoreCard_onAFreshInstall_dismissesOrRestores() {
        fakes.backup.freshInstall = true
        fakes.backup.available = BackupInfo(1_700_000_000_000L)
        openStorage()
        composeRule.waitForText("Backup from")
        composeRule.tapRow("Dismiss")
        composeRule.waitForTextGone("Backup from")
        assertEquals(0, fakes.backup.importCalls)
    }

    @Test
    fun restoreCard_restoreImportsTheBackup() {
        fakes.backup.freshInstall = true
        fakes.backup.available = BackupInfo(1_700_000_000_000L)
        openStorage()
        composeRule.tapRow("Restore")
        composeRule.waitUntilTrue { fakes.backup.importCalls == 1 }
        composeRule.waitForText("Restored 2 bookmarks and 1 hidden threads")
    }
}
