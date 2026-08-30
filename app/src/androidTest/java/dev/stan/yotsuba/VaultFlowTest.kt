package dev.stan.yotsuba

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class VaultFlowTest : FlowTest() {

    @Test
    fun vaultScreen_rendersEmptyState() {
        // Navigate to the vault via its bottom-bar tab ("Saved").
        composeRule.waitForText("Saved")
        composeRule.clickText("Saved")

        // The fake vault has storage access and no entries, so the empty state renders.
        composeRule.waitForText("Vault is empty")
    }
}
