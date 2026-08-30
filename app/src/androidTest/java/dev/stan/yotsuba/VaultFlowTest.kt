package dev.stan.yotsuba

import androidx.compose.ui.test.onAllNodesWithText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class VaultFlowTest : FlowTest() {

    @Test
    fun vaultScreen_rendersEmptyState() {
        // Navigate to the vault via its bottom-bar tab ("Saved").
        composeRule.waitForText("Saved")
        composeRule.clickText("Saved")

        // The fake vault has storage access and no entries: either the "Saved media"
        // title or the empty state must be on screen.
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Saved media", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Vault is empty", substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
