package dev.stan.yotsuba.settings

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.FilterField
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openSettingsSection
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

@HiltAndroidTest
class FiltersFlowTest : FlowTest() {

    private val pattern = "spam"

    private fun openFilters(vararg filters: Filter) {
        if (filters.isNotEmpty()) fakes.settings.set { it.copy(filters = filters.toList()) }
        composeRule.openSettingsSection("Filters", if (filters.isEmpty()) "No filters yet" else filters[0].pattern)
    }

    /** One of the dialog's three text fields, told apart by its label. */
    private fun field(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(label, substring = true))

    private fun openAddDialog() {
        composeRule.tapRow("Add filter")
        composeRule.waitForText("Pattern")
    }

    private fun savedFilter() = fakes.settings.state.value.filters.single()

    @Test
    fun emptyState_explainsWhatAFilterDoes() {
        openFilters()
        composeRule.waitForText("No filters yet")
        composeRule.waitForText("Add filter")
    }

    @Test
    fun addDialog_blocksSave_whileBlankOrInvalid() {
        openFilters()
        openAddDialog()
        composeRule.nodeWithText("Save", substring = false).assertIsNotEnabled()

        composeRule.tapRow("Regex")
        field("Pattern").performTextInput("spam[")
        composeRule.nodeWithText("Save", substring = false).assertIsNotEnabled()

        field("Pattern").performTextClearance()
        field("Pattern").performTextInput(pattern)
        composeRule.tapRow("Save")
        composeRule.waitUntilTrue { fakes.settings.state.value.filters.size == 1 }
        assertEquals(pattern, savedFilter().pattern)
    }

    @Test
    fun addDialog_savesTheFieldBoardsActionAndEnabled() {
        openFilters()
        openAddDialog()
        field("Pattern").performTextInput(pattern)
        composeRule.tapRow("Comment")
        composeRule.tapRow("Filename")
        field("Boards").performTextInput("g, v")
        composeRule.tapRow("Stub")
        composeRule.tapRow("Enabled")
        composeRule.tapRow("Save")

        composeRule.waitUntilTrue { fakes.settings.state.value.filters.size == 1 }
        val saved = savedFilter()
        assertEquals(FilterField.FILENAME, saved.field)
        assertEquals(setOf(TestSeed.BOARD, TestSeed.VIDEO_BOARD), saved.boards)
        assertEquals(FilterAction.STUB, saved.action)
        assertEquals(false, saved.enabled)
    }

    @Test
    fun addDialog_testFieldReportsMatchAndNoMatch() {
        openFilters()
        openAddDialog()
        field("Pattern").performTextInput(pattern)
        field("Test against sample text").performTextInput("this is spam")
        composeRule.waitForText("Matches")
        field("Test against sample text").performTextClearance()
        field("Test against sample text").performTextInput("ham")
        composeRule.waitForText("No match")
        composeRule.tapRow("Cancel")
        composeRule.waitForTextGone("Test against sample text")
    }

    @Test
    fun row_showsItsSummaryAndRegexError_switchTogglesEnabled_tapEdits() {
        openFilters(Filter(id = "a", pattern = "[", isRegex = true, action = FilterAction.FADE))
        composeRule.waitForText("Comment · Fade · Regex · All boards", substring = false)
        composeRule.waitForText("Regex does not compile; this filter matches nothing.")

        composeRule.onNode(isToggleable(), useUnmergedTree = true).performClick()
        composeRule.waitUntilTrue { !savedFilter().enabled }

        composeRule.tapRow("[")
        composeRule.waitForText("Edit filter")
    }

    @Test
    fun addDialog_keepsItsDraftAcrossARotation() {
        openFilters()
        openAddDialog()
        field("Pattern").performTextInput(pattern)
        recreate()
        // The dialog is reopened from the saved id and the draft is still in the field.
        composeRule.waitForText("Add filter")
        composeRule.tapRow("Save")
        composeRule.waitUntilTrue { fakes.settings.state.value.filters.size == 1 }
        assertEquals(pattern, savedFilter().pattern)
    }

    @Test
    fun swipeDeletesTheRow_andUndoPutsItBack() {
        openFilters(Filter(id = "a", pattern = pattern), Filter(id = "b", pattern = "bait"))
        composeRule.nodeWithText(pattern, substring = false).performTouchInput { swipeLeft() }
        composeRule.waitForText("Filter deleted")
        composeRule.waitUntilTrue { fakes.settings.state.value.filters.map { it.id } == listOf("b") }

        composeRule.tapRow("Undo")
        composeRule.waitUntilTrue { fakes.settings.state.value.filters.map { it.id } == listOf("a", "b") }
        composeRule.waitForText(pattern, substring = false)
    }
}
