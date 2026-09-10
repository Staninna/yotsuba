package dev.stan.yotsuba.thread

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A tapped link in a post body, and the confirmation that stands between it and the browser. */
@HiltAndroidTest
class LinkFlowTest : FlowTest() {

    private val domain = "example.com"

    private fun tapTheSeededLink() {
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.LINK_REPLY_TEXT)
    }

    @Test
    fun linkTap_asksFirst_andCancelKeepsTheThread() {
        tapTheSeededLink()

        composeRule.waitForText("Open external link?")
        composeRule.sheetNode(TestSeed.LINK_URL).assertIsDisplayed()
        composeRule.nodeWithText("Always trust $domain").assertIsDisplayed()

        composeRule.tap("Cancel")
        composeRule.waitForTextGone("Open external link?")
        assertEquals(emptySet<String>(), fakes.settings.state.value.trustedDomains)
        composeRule.listNode(TestSeed.LINK_REPLY_TEXT).assertIsDisplayed()
    }

    @Test
    fun alwaysTrust_thenOpen_remembersTheDomain() {
        tapTheSeededLink()
        composeRule.waitForText("Open external link?")

        composeRule.tap("Always trust $domain")
        composeRule.nodeWithText("Always trust $domain").assertIsOn()
        composeRule.tap("Open", substring = false)

        composeRule.waitUntilTrue { fakes.settings.state.value.trustedDomains == setOf(domain) }
    }

    @Test
    fun withConfirmationOff_theLinkOpensWithNoDialog() {
        fakes.settings.set { it.copy(confirmBeforeOpeningLinks = false) }
        tapTheSeededLink()

        composeRule.waitForIdle()
        assertFalse(composeRule.hasText("Open external link?"))
    }

    @Test
    fun alreadyTrustedDomain_skipsTheDialog() {
        fakes.settings.set { it.copy(trustedDomains = setOf(domain)) }
        tapTheSeededLink()

        composeRule.waitForIdle()
        assertFalse(composeRule.hasText("Open external link?"))
    }
}
