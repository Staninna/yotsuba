package dev.stan.yotsuba

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.FakeSettingsRepository
import javax.inject.Inject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class SettingsFlowTest : FlowTest() {

    @Inject
    lateinit var settings: FakeSettingsRepository

    @Test
    fun togglingDynamicColor_updatesSettingsRepository() {
        assertTrue(settings.state.value.dynamicColor)

        // Open Settings from the Boards gear, drill into Appearance, flip the "Dynamic color" switch row.
        composeRule.waitForContentDescription("Settings")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForText("Appearance")
        composeRule.clickText("Appearance")
        composeRule.waitForText("Dynamic color")
        composeRule.clickText("Dynamic color")

        composeRule.waitUntil(UI_TIMEOUT_MS) { !settings.state.value.dynamicColor }
        assertFalse(settings.state.value.dynamicColor)
    }
}
