package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.repository.BoardRepository

/**
 * Answers [boards] with [result] and [board] from that list. A code not in the list goes to
 * [unlisted], which defaults to null; a test that wants every board to exist hands it [stub].
 */
class FakeBoardRepository(
    var result: DataResult<List<Board>> = DataResult.Success(emptyList()),
    var unlisted: (String) -> Board? = { null },
) : BoardRepository {
    constructor(list: List<Board>) : this(DataResult.Success(list))

    override suspend fun boards(forceRefresh: Boolean) = result
    override suspend fun board(code: String): Board? =
        (result as? DataResult.Success)?.value?.firstOrNull { it.code == code } ?: unlisted(code)

    companion object {
        /** A worksafe board with every flag off except what the caller asks for. */
        fun stub(code: String, webmAudio: Boolean = false, title: String = "Technology") = Board(
            code = code, title = title, description = "", worksafe = true,
            category = BoardCategory.INTERESTS, userIds = false, countryFlags = false,
            boardFlags = false, spoilers = false, webmAudio = webmAudio, codeTags = false,
            mathTags = false, sjisTags = false, textOnly = false,
        )
    }
}
