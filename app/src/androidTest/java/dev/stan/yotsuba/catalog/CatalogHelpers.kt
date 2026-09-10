package dev.stan.yotsuba.catalog

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
