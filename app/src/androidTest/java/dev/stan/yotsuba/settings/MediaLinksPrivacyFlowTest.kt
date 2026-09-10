package dev.stan.yotsuba.settings

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.domain.model.LocalSearchMethod
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.SeekStep
import dev.stan.yotsuba.openSettingsSection
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import org.junit.Assert.assertEquals
import org.junit.Test

@HiltAndroidTest
class MediaLinksPrivacyFlowTest : FlowTest() {

    @Test
    fun media_everyControlFlipsItsSetting() {
        composeRule.openSettingsSection("Media & playback", "Data saver")
        flip("Data saver", true) { it.dataSaver }
        flip("Expand images in the thread", true) { it.inlineImageExpansion }
        flip("Always", MediaAutoplay.ALWAYS) { it.mediaAutoplay }
        flip("Never", MediaAutoplay.NEVER) { it.mediaAutoplay }
        flip("Keep the screen on", false) { it.keepScreenOnWhileWatching }
        flip("Hold to save", false) { it.holdToSave }
        flip("Save replies with media", false) { it.saveRepliesWithMedia }
    }

    @Test
    fun media_skipByIsDeadUntilDoubleTapIsOn() {
        composeRule.openSettingsSection("Media & playback", "Data saver")
        flip("Double-tap to skip", false) { it.doubleTapSeekEnabled }
        composeRule.tapRow("5 s")
        composeRule.waitForIdle()
        assertEquals(SeekStep.TEN, fakes.settings.state.value.seekStep)

        // Live again: the chip that did nothing a moment ago now sets the step.
        flip("Double-tap to skip", true) { it.doubleTapSeekEnabled }
        flip("15 s", SeekStep.FIFTEEN) { it.seekStep }
    }

    @Test
    fun links_confirmSwitchAndTheTrustedDomainsDialog() {
        fakes.settings.set { it.copy(trustedDomains = setOf("example.com")) }
        composeRule.openSettingsSection("Links & privacy", "Confirm before opening links")
        flip("Confirm before opening links", false) { it.confirmBeforeOpeningLinks }

        composeRule.tapRow("Trusted domains (1)")
        composeRule.waitForText("example.com", substring = false)
        flip("Remove", emptySet()) { it.trustedDomains }
        composeRule.waitForText("Nothing here.")
        composeRule.tapRow("Done")
        composeRule.waitForText("Trusted domains (0)", substring = false)
    }

    @Test
    fun privacy_lockIsDeadWithoutAScreenLock() {
        composeRule.openSettingsSection("Privacy", "Lock the app")
        composeRule.waitForText("Set a screen lock on your phone first")
        composeRule.tapRow("Lock the app")
        composeRule.waitForIdle()
        assertEquals(false, fakes.settings.state.value.appLock)
        composeRule.waitForTextGone("Lock again after")
    }

    @Test
    fun privacy_delayChipsAppearOnceTheLockIsOn() {
        composeRule.openSettingsSection("Privacy", "Lock the app")
        // Not through the switch: the lock is unavailable here, and it wants the system prompt.
        fakes.settings.set { it.copy(appLock = true) }
        composeRule.waitForText("Lock again after")
        flip("1 min", 60) { it.appLockDelaySeconds }
        flip("Right away", 0) { it.appLockDelaySeconds }
    }

    @Test
    fun privacy_localSearchControlsFlipTheirSettings() {
        composeRule.openSettingsSection("Privacy", "Search local files by")
        flip("Temporary host", LocalSearchMethod.TEMP_HOST) { it.localSearchMethod }
        flip("Direct upload", LocalSearchMethod.DIRECT_UPLOAD) { it.localSearchMethod }
        flip("Ask before uploading a local file", false) { it.confirmTemporaryHost }
    }
}
