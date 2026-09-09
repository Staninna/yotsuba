package dev.stan.yotsuba.feature.catalog

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which boards have had their "About /x/" card this process. The card comes from
 * boards.json's `meta_description`: the JSON API carries no announcement or blotter field,
 * so this is the one board-level text it offers. One card per board per session, and a
 * board opened again later in the session gets none.
 */
@Singleton
class BoardAboutCards @Inject constructor() {
    private val shown = mutableSetOf<String>()

    /** True the first time it is asked about [board]. */
    @Synchronized fun claim(board: String): Boolean = shown.add(board)
}
