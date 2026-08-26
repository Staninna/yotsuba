package dev.stan.yotsuba

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.FakeSettingsRepository
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class RevealSpoilersFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /** With "Reveal all spoilers" on, the first tap opens the viewer — no reveal step. */
    @Test
    fun revealAllSpoilersOn_singleTapOpensViewer() {
        val fake = settingsRepository as FakeSettingsRepository
        fake.state.value = fake.state.value.copy(revealAllSpoilers = true)

        composeRule.openSeededThread()
        composeRule.waitForContentDescription(TestSeed.SPOILER_FILENAME)
        composeRule.onNodeWithContentDescription(
            TestSeed.SPOILER_FILENAME, substring = true, ignoreCase = true,
        ).performClick()
        composeRule.waitForContentDescription("Close viewer")
    }
}
