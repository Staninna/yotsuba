package dev.stan.yotsuba

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.FakeSettingsRepository
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class CatalogFlowTest {

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

    private fun openCatalog() {
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.clickText(TestSeed.BOARD_TITLE)
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    @Test
    fun catalogSearch_filtersThreads_andClearingRestoresThem() {
        openCatalog()

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.waitForText("Search this catalog")

        // A query nothing matches empties the catalog.
        composeRule.onNode(hasSetTextAction()).performTextInput("zzz-no-such-thread")
        composeRule.waitForText("No matches")

        // Clearing and typing a real match brings the thread back.
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Yotsuba")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    @Test
    fun layoutSwitch_persistsIntoSettings() {
        val fake = settingsRepository as FakeSettingsRepository
        assertEquals(CatalogLayout.COMFORTABLE, fake.state.value.catalogLayout)

        openCatalog()
        composeRule.onNodeWithContentDescription("Switch layout").performClick()

        composeRule.waitUntil(UI_TIMEOUT_MS) {
            fake.state.value.catalogLayout != CatalogLayout.COMFORTABLE
        }
        assertNotEquals(CatalogLayout.COMFORTABLE, fake.state.value.catalogLayout)
    }
}
