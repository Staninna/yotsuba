package dev.stan.yotsuba.catalog

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.CatalogThread

/**
 * Forty threads, enough for the scroll-to-top button (it needs nine items scrolled past).
 * Titles are zero-padded so "Filler 01" is not also a substring of "Filler 10".
 */
fun longCatalog(board: String): List<CatalogThread> = (1..40).map {
    TestSeed.catalogThread(board, 10_000L + it, "Filler %02d".format(it), "filler body $it")
}

const val FIRST_FILLER = "Filler 01"

/**
 * The thread grid. Matched by its vertical scroll axis, because on the Home tab the pager
 * around it is a lazy layout that scrolls to an index as well.
 */
fun ComposeTestRule.catalogGrid(): SemanticsNodeInteraction = onNode(
    SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasScrollToIndexAction(),
)
