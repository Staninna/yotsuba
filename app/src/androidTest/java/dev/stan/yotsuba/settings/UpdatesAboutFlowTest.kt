package dev.stan.yotsuba.settings

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.BuildConfig
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.openSettingsSection
import dev.stan.yotsuba.waitForText
import org.junit.Assert.assertFalse
import org.junit.Test

@HiltAndroidTest
class UpdatesAboutFlowTest : FlowTest() {

    @Test
    fun updates_onADebugBuild_showsTheDevNoticeAndTheHistoryHeader() {
        composeRule.openSettingsSection("Updates", "Version history")
        composeRule.waitForText("Dev build ${BuildConfig.VERSION_NAME}")
        // A dev build cannot install a release APK, so it never offers the check.
        assertFalse(composeRule.hasText("Check for updates"))
        // The entries come from GitHub; only the states around them belong to the app.
        composeRule.waitForText("Loading release notes")
    }

    @Test
    fun about_showsTheVersionAndAttribution() {
        composeRule.openSettingsSection("About", "Your numbers")
        composeRule.waitForText("Version ${BuildConfig.VERSION_NAME}", substring = false)
        composeRule.waitForText("4chan is a registered trademark")
    }

    @Test
    fun about_yourNumbers_opensStats_andBackReturns() {
        composeRule.openSettingsSection("About", "Your numbers")
        composeRule.tapRow("Your numbers")
        composeRule.waitForText("You", substring = false)
        composeRule.goBack()
        composeRule.waitForText("Your numbers")
    }
}
