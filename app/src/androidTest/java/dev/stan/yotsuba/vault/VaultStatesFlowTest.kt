package dev.stan.yotsuba.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.hasContentDescription
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The Saved tab before there is anything to browse: empty, locked out, or unindexed. */
@HiltAndroidTest
class VaultStatesFlowTest : FlowTest() {

    @Test
    fun emptyVault_showsEmptyState() {
        composeRule.openVault()
        composeRule.waitForText("Vault is empty")
        composeRule.waitForText("Media you save from threads")
    }

    @Test
    fun noStorageAccess_showsGrantPrompt_withoutTheBarActions() {
        fakes.vault.access.value = false
        composeRule.openVault()
        composeRule.waitForText("All files access")
        assertFalse(composeRule.hasContentDescription("Search"))
        assertFalse(composeRule.hasContentDescription("Import"))
        assertFalse(composeRule.hasContentDescription("Sync"))
        // The button hands over to system settings; the tap is as far as this suite can see.
        composeRule.button("Grant storage access").assertIsDisplayed().performClick()
    }

    @Test
    fun unindexedThreads_banner_rescanRebuildsIndex() {
        fakes.vault.unindexedCount = 2
        composeRule.openVault()
        composeRule.waitForText("Found 2 saved threads on this device")
        composeRule.waitForText("Rescan rebuilds the index")
        composeRule.tap("Rescan", substring = false)
        composeRule.waitUntilTrue { fakes.vault.rescanCalls == 1 }
        composeRule.waitForText("Index rebuilt")
        assertEquals(0, fakes.vault.unindexedCount)
    }
}
