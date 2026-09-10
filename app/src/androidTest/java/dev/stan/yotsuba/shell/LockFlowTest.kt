package dev.stan.yotsuba.shell

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import org.junit.Assert.assertFalse
import org.junit.Test

@HiltAndroidTest
class LockFlowTest : FlowTest() {

    /** The lock reads the setting on the first emission, so it has to be set before launch. */
    override val launchOnSetUp: Boolean get() = false

    @Test
    fun appLockOn_showsLockScreen_notTheApp() {
        fakes.settings.set { it.copy(appLock = true) }
        launch()
        composeRule.waitForText("Yotsuba is locked")
        composeRule.waitForText("Unlock", substring = false)
        assertFalse(composeRule.hasText("Boards", substring = false))
        assertFalse(composeRule.hasText("No favourite boards yet"))
        // The button hands over to the system prompt; the lock screen stays until it passes.
        composeRule.tap("Unlock", substring = false)
        composeRule.waitForText("Yotsuba is locked")
        assertFalse(composeRule.hasText("Boards", substring = false))
    }

    @Test
    fun appLockOff_rendersHomeAtOnce() {
        launch()
        composeRule.waitForText("No favourite boards yet")
        composeRule.waitForText("Boards", substring = false)
        assertFalse(composeRule.hasText("Yotsuba is locked"))
    }
}
