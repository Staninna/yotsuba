package dev.stan.yotsuba.feature.home

import dev.stan.yotsuba.domain.model.CatalogThread
import kotlin.random.Random

/**
 * A random live thread from a random one of [boards]. Boards are tried in random order and
 * [catalog] is only called for the ones reached, so a first hit costs one fetch. Stickies and
 * closed threads never come up; [catalog] is expected to have applied the user's own hiding
 * and filters already. Null when no board has a thread that qualifies.
 */
suspend fun pickRandomThread(
    boards: Collection<String>,
    random: Random = Random,
    catalog: suspend (board: String) -> List<CatalogThread>,
): CatalogThread? {
    for (board in boards.shuffled(random)) {
        val live = catalog(board).filterNot { it.sticky || it.closed }
        if (live.isNotEmpty()) return live.random(random)
    }
    return null
}
