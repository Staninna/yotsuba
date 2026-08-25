package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.database.dao.HiddenThreadDao
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.data.repository.HiddenThreadsRepositoryImpl
import dev.stan.yotsuba.domain.model.HiddenThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenThreadsRepositoryImplTest {

    private class FakeDao : HiddenThreadDao {
        val rows = MutableStateFlow<List<HiddenThreadEntity>>(emptyList())
        override fun all(): Flow<List<HiddenThreadEntity>> = rows
        override fun forBoard(board: String): Flow<List<HiddenThreadEntity>> =
            rows.map { list -> list.filter { it.board == board } }
        override suspend fun hide(entity: HiddenThreadEntity) {
            rows.value = rows.value.filterNot {
                it.board == entity.board && it.threadNo == entity.threadNo
            } + entity
        }
        override suspend fun unhide(board: String, threadNo: Long) {
            rows.value = rows.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
    }

    @Test fun `hide and unhide round-trip as domain models`() = runTest {
        val repo = HiddenThreadsRepositoryImpl(FakeDao())
        repo.hide("g", 1L)
        repo.hide("a", 2L)
        assertEquals(listOf(HiddenThread("g", 1L), HiddenThread("a", 2L)), repo.all.first())
        assertEquals(listOf(HiddenThread("g", 1L)), repo.forBoard("g").first())
        repo.unhide("g", 1L)
        assertEquals(listOf(HiddenThread("a", 2L)), repo.all.first())
    }
}
